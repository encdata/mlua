package dev.mlua.minecraft.binding;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.common.enums.Side;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.minecraft.ui.element.*;

public final class MLuaUIBindings {

    public static LuaTable uiObjectTable() {
        MLuaRuntime runtime = MLuaRuntime.get();
        return uiObjectTable(runtime, runtime.session().clientScheduler());
    }

    public static void registerClasses(MLuaRegistration registration) {
        registerBuiltins(registration);
    }

    public static LuaTable registerNativeClass(MLuaRegistration registration, String name,
                                               Class<? extends GuiBase> instanceType,
                                               Supplier<? extends GuiBase> factory,
                                               String description, String parentName) {
        return MLuaUIClassRegistry.registerNativeClass(registration, name, instanceType, factory, description, parentName);
    }

    @Deprecated(forRemoval = false)
    public static LuaTable registerNativeClass(MLuaRegistration registration, String name,
                                               Class<? extends GuiBase> instanceType,
                                               Supplier<? extends GuiBase> factory,
                                               String description, String... parentNames) {
        return MLuaUIClassRegistry.registerNativeClass(registration, name, instanceType, factory, description, parentNames);
    }

    public static LuaTable uiObjectTable(MLuaRegistration registration, MLuaTaskSchedulerView scheduler) {
        return uiObjectTable(registration, scheduler, null, null, null);
    }

    public static LuaTable uiObjectTable(MLuaRegistration registration, MLuaTaskSchedulerView scheduler,
                                         String ownerModId, Side ownerSide, Globals ownerGlobals) {
        LuaTable t = new LuaTable();
        t.rawset("__mlua_doc_category", LuaValue.valueOf("Classes"));
        Map<String, LuaTable> classViews = new HashMap<>();

        LuaTable probe = new LuaTable();
        for (String className : registration.classNames()) {
            LuaTable classTable = registration.classTable(className);
            if (classTable == null) continue;
            probe.rawset("__type", LuaValue.valueOf(className));
            if (registration.isInstanceOf(probe, "GuiBase")) {
                t.set(className, ownerBoundClassTable(classTable, registration, ownerModId, ownerSide, ownerGlobals, classViews));
            }
        }
        LuaTable meta = new LuaTable();
        meta.set("__index", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue table, LuaValue key) {
                if (!key.isstring()) return LuaValue.NIL;
                LuaTable classTable = registration.classTable(key.tojstring());
                if (classTable == null) return LuaValue.NIL;
                probe.rawset("__type", key);
                return registration.isInstanceOf(probe, "GuiBase")
                    ? ownerBoundClassTable(classTable, registration, ownerModId, ownerSide, ownerGlobals, classViews)
                    : LuaValue.NIL;
            }
        });
        t.setmetatable(meta);
        t.set("new", genericNewFunc(registration, ownerModId, ownerSide, ownerGlobals));
        return t;
    }

    private static LuaTable ownerBoundClassTable(LuaTable source, MLuaRegistration registration,
                                                  String ownerModId, Side ownerSide, Globals ownerGlobals,
                                                  Map<String, LuaTable> classViews) {
        String className = source.rawget("__type").optjstring("");
        LuaTable existing = classViews.get(className);
        if (existing != null && existing.rawget("__mlua_registered_class_view_source").isuserdata()
            && existing.rawget("__mlua_registered_class_view_source").touserdata() == source) return existing;
        LuaTable view = new LuaTable();
        view.rawset("__type", source.rawget("__type"));
        view.rawset("__class", source.rawget("__class"));
        view.rawset("__mlua_registered_class_view_source", LuaValue.userdataOf(source));
        LuaTable meta = new LuaTable();
        meta.set("__index", source);
        view.setmetatable(meta);

        view.set("new", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue constructor = source.get("new");
                if (!constructor.isfunction()) {
                    throw new LuaError("[MLua] UI class '" + className + "' has no registered constructor.");
                }
                boolean calledWithClass = args.arg1().istable()
                    && args.arg1().rawget("__class").toboolean();
                LuaValue[] forwarded = new LuaValue[args.narg()];
                for (int i = 1; i <= args.narg(); i++) forwarded[i - 1] = args.arg(i);
                if (calledWithClass) forwarded[0] = view;
                Varargs created = constructor.invoke(LuaValue.varargsOf(forwarded));
                if (created.arg1().istable()) {
                    LuaTable instance = created.arg1().checktable();
                    GuiBase element = MLuaTableRegistry.getElement(instance);
                    if (element != null) {
                        element.setScheduler(MLuaUIClassRegistry.schedulerFor(registration));
                        element.setRegistration(registration);
                        element.setOwnerContext(ownerModId, ownerSide, ownerGlobals);
                        int fieldsIndex = calledWithClass ? 2 : 1;
                        LuaValue fieldsValue = fieldsIndex < forwarded.length
                            ? forwarded[fieldsIndex] : LuaValue.NIL;
                        LuaTable fields = fieldsValue != null && fieldsValue.istable()
                            ? fieldsValue.checktable() : null;
                        String instanceClass = instance.rawget("__type").optjstring(className);
                        if (!className.equals(instanceClass)) {
                            registration.attachClassInstance(instance, className, null);
                            instanceClass = className;
                        }
                        registration.initializeClassInstance(instance, instanceClass, fields);
                    }
                }
                return created;
            }
        });
        classViews.put(className, view);
        return view;
    }

    private static void registerBuiltins(MLuaRegistration registration) {
        String instance = registration.requireInstanceBaseClassName();
        registerNativeClass(registration, "GuiBase", GuiBase.class, null,
            "Base class for all MLua UI instances.", instance);
        registerNativeClass(registration, "GuiBase2d", GuiBase2d.class, null,
            "Base class for 2D UI instances.", "GuiBase");
        registerNativeClass(registration, "LayerCollector", LayerCollector.class, null,
            "Base class for root UI containers such as ScreenGui.", "GuiBase2d");
        registerNativeClass(registration, "GuiObject", GuiObject.class, null,
            "Base class for visible rectangular UI objects.", "GuiBase2d");
        registerNativeClass(registration, "GuiButton", GuiButton.class, null,
            "Base class for clickable UI button objects.", "GuiObject");
        registerNativeClass(registration, "ScreenGui", ScreenGui.class,
            () -> new ScreenGui("Screen"), "A root UI container that displays its child objects on screen.", "LayerCollector");
        registerNativeClass(registration, "Frame", Frame.class, Frame::new,
            "A rectangular UI container.", "GuiObject");
        registerNativeClass(registration, "ScrollingFrame", ScrollingFrame.class,
            ScrollingFrame::new, "A UI container with a scrollable canvas.", "Frame");
        registerNativeClass(registration, "TextLabel", TextLabel.class, TextLabel::new,
            "A UI object that renders text.", "GuiObject");
        registerNativeClass(registration, "ImageLabel", ImageLabel.class, ImageLabel::new,
            "A UI object that renders an image.", "GuiObject");
        registerNativeClass(registration, "TextButton", TextButton.class, TextButton::new,
            "A clickable UI object that renders text.", "GuiButton");
        registerNativeClass(registration, "ImageButton", ImageButton.class, ImageButton::new,
            "A clickable UI object that renders an image.", "GuiButton");
        MLuaUIInstanceBindings.register(registration);
        MLuaUIAnimationBindings.register(registration);
    }

    public static LuaTable getClassTable(String name) {
        return getClassTable(name, MLuaRuntime.get());
    }

    public static LuaTable getClassTable(String name, MLuaRegistration registration) {
        LuaTable t = registration.classTable(name);
        return t != null ? t : new LuaTable();
    }

    public static String classNameOf(GuiBase element) {
        MLuaRegistration registration = element.registration();
        if (registration != null) {
            LuaTable self = element.getSelfTable();
            String attachedType = registration.classNameOf(self);
            if (attachedType != null && registration.classTable(attachedType) != null) return attachedType;

            Class<?> actualType = element.getClass();
            String mostSpecificName = null;
            Class<?> mostSpecificType = null;
            for (String className : registration.classNames()) {
                LuaTable classTable = registration.classTable(className);
                if (classTable == null) continue;
                LuaValue javaType = classTable.get("__mlua_ui_java_type");
                if (!javaType.isuserdata()) continue;
                Class<?> candidate = (Class<?>) javaType.touserdata();
                if (candidate == null || !candidate.isAssignableFrom(actualType)) continue;
                if (mostSpecificType == null || mostSpecificType.isAssignableFrom(candidate)) {
                    mostSpecificType = candidate;
                    mostSpecificName = className;
                }
            }
            if (mostSpecificName != null) return mostSpecificName;
        }
        return "GuiBase";
    }

    private static VarArgFunction genericNewFunc(MLuaRegistration registration, String ownerModId,
                                                  Side ownerSide, Globals ownerGlobals) {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                String className = args.checkjstring(1);
                LuaTable classTable = registration.classTable(className);
                if (classTable == null) throw new LuaError("[MLua] UIObject.new does not know class '" + className + "'");
                LuaTable probe = new LuaTable();
                probe.rawset("__type", LuaValue.valueOf(className));
                if (!registration.isInstanceOf(probe, "GuiBase")) {
                    throw new LuaError("[MLua] Class '" + className + "' is not a UI class");
                }
                LuaValue declaredConstructor = classTable.rawget("new");
                LuaValue constructor = declaredConstructor.isnil() ? classTable.get("new") : declaredConstructor;
                if (!constructor.isfunction()) throw new LuaError("[MLua] UI class '" + className + "' is abstract and cannot be created");
                int userArgCount = args.narg() - 1;
                LuaValue[] forwarded = new LuaValue[userArgCount + 1];
                forwarded[0] = classTable;
                for (int index = 2; index <= args.narg(); index++) forwarded[index - 1] = args.arg(index);
                Varargs created = constructor.invoke(LuaValue.varargsOf(forwarded));
                if (!created.arg1().istable()) {
                    throw new LuaError("[MLua] UI class '" + className + "' constructor must return a live UI instance");
                }
                LuaTable instance = created.arg1().checktable();
                GuiBase element = MLuaTableRegistry.getElement(instance);
                if (element == null) {
                    throw new LuaError("[MLua] UI class '" + className + "' constructor returned a table without a live UI backing instance");
                }
                element.setScheduler(MLuaUIClassRegistry.schedulerFor(registration));
                element.setRegistration(registration);
                if (!className.equals(instance.rawget("__type").optjstring(""))) {
                    registration.attachClassInstance(instance, className, null);
                }
                element.setOwnerContext(ownerModId, ownerSide, ownerGlobals);
                LuaValue fieldsValue = args.arg(3);
                registration.initializeClassInstance(instance, className,
                    fieldsValue.istable() ? fieldsValue.checktable() : null);
                return created;
            }
        };
    }

    public static LuaValue classOf(String className) {
        return classOf(className, MLuaRuntime.get());
    }

    public static LuaValue classOf(String className, MLuaRegistration registration) {
        LuaTable t = registration.classTable(className);
        return t != null ? t : LuaValue.NIL;
    }
}
