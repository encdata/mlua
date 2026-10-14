package dev.mlua.minecraft.binding;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Supplier;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.common.enums.Side;
import dev.mlua.common.enums.EasingDirection;
import dev.mlua.common.enums.EasingStyle;
import dev.mlua.modules.enums.MLuaEnumValue;
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
        if (registration == null) throw new IllegalArgumentException("UI class registration requires an MLua registry");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("UI class name must be non-empty");
        if (instanceType == null || !GuiBase.class.isAssignableFrom(instanceType)) {
            throw new IllegalArgumentException("Native UI class must use a GuiBase implementation type");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("UI class description must be non-empty");
        }
        LuaTable existing = registration.classTable(name);
        if (existing != null) {
            LuaValue existingType = existing.rawget("__mlua_ui_java_type");
            Object existingTypeObject = existingType.isuserdata() ? existingType.touserdata() : null;
            Class<?> existingJavaType = existingTypeObject instanceof Class<?> javaType ? javaType : null;
            if (existingJavaType == null) {
                throw new IllegalStateException("Lua class '" + name + "' already exists and has no native UI backing type");
            }
            if (existingJavaType != instanceType) {
                throw new IllegalStateException("UI class '" + name + "' already uses native type '"
                    + existingJavaType.getName() + "'");
            }
        }
        if (parentName == null || parentName.isBlank()) {
            throw new IllegalArgumentException("Native UI classes must declare a parent class");
        }
        LuaTable parent = registration.classTable(parentName);
        if (parent == null) {
            throw new IllegalStateException("UI class '" + name + "' requires registered parent class '"
                + parentName + "'");
        }
        boolean uiRootParent = name.equals("GuiBase")
            && parentName.equals(registration.requireInstanceBaseClassName());
        LuaTable parentProbe = new LuaTable();
        parentProbe.rawset("__type", LuaValue.valueOf(parentName));
        if (!uiRootParent && !registration.isInstanceOf(parentProbe, "GuiBase")) {
            throw new IllegalArgumentException("UI class '" + name + "' parent '" + parentName
                + "' must inherit from GuiBase");
        }
        if (!uiRootParent) validateNativeParentType(name, parentName, instanceType, registration, new HashSet<>());
        return registerClass(registration, name, instanceType, factory, description, parent);
    }

    @Deprecated(forRemoval = false)
    public static LuaTable registerNativeClass(MLuaRegistration registration, String name,
                                               Class<? extends GuiBase> instanceType,
                                               Supplier<? extends GuiBase> factory,
                                               String description, String... parentNames) {
        if (parentNames == null || parentNames.length != 1) {
            throw new IllegalArgumentException("Native UI classes require exactly one superclass");
        }
        return registerNativeClass(registration, name, instanceType, factory, description, parentNames[0]);
    }

    private static void validateNativeParentType(String className, String parentName, Class<?> instanceType,
                                                 MLuaRegistration registration, java.util.Set<String> visited) {
        if (!visited.add(parentName)) return;
        LuaTable parent = registration.classTable(parentName);
        if (parent == null) return;
        LuaValue nativeTypeValue = parent.rawget("__mlua_ui_java_type");
        if (!nativeTypeValue.isnil()) {
            Object nativeTypeObject = nativeTypeValue.isuserdata() ? nativeTypeValue.touserdata() : null;
            if (!(nativeTypeObject instanceof Class<?> nativeType)) {
                throw new IllegalStateException("UI parent class '" + parentName + "' has invalid native type metadata");
            }
            if (!nativeType.isAssignableFrom(instanceType)) {
                throw new IllegalArgumentException("UI class '" + className + "' uses native type '"
                    + instanceType.getName() + "' which does not extend declared parent '" + parentName
                    + "' backed by '" + nativeType.getName() + "'");
            }
            return;
        }
        String inheritedParent = registration.superclassName(parentName);
        if (inheritedParent != null) {
            validateNativeParentType(className, inheritedParent, instanceType, registration, visited);
        }
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
                        element.setScheduler(schedulerFor(registration));
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
        registerCommonInstanceMethods(registration);
        registerGuiObjectMethods(registration);
    }

    private static void registerGuiObjectMethods(MLuaRegistration registration) {
        registration.registerClassFunction("GuiObject", "MoveTo", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                GuiObject object = requireGuiObject(args.arg1(), "MoveTo");
                return LuaValue.valueOf(object.tweenPosition(parseUDim2(args.arg(2)), easingDirection(args.arg(5)),
                    easingStyle(args.arg(4)), positiveDuration(args.arg(3)), args.arg(6).optboolean(true)));
            }
        }, "Animates Position to a UDim2 target and returns whether the animation started.");
        registration.registerClassFunction("GuiObject", "ResizeTo", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                GuiObject object = requireGuiObject(args.arg1(), "ResizeTo");
                return LuaValue.valueOf(object.tweenSize(parseUDim2(args.arg(2)), easingDirection(args.arg(5)),
                    easingStyle(args.arg(4)), positiveDuration(args.arg(3)), args.arg(6).optboolean(true)));
            }
        }, "Animates Size to a UDim2 target and returns whether the animation started.");
        registration.registerClassFunction("GuiObject", "MoveAndResizeTo", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                GuiObject object = requireGuiObject(args.arg1(), "MoveAndResizeTo");
                return LuaValue.valueOf(object.tweenSizeAndPosition(parseUDim2(args.arg(3)), parseUDim2(args.arg(2)),
                    easingDirection(args.arg(6)), easingStyle(args.arg(5)), positiveDuration(args.arg(4)),
                    args.arg(7).optboolean(true)));
            }
        }, "Animates both Position and Size to UDim2 targets and returns whether the animation started.");
        registration.registerClassFunction("GuiObject", "CancelAnimation", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                requireGuiObject(args.arg1(), "CancelAnimation").cancelTween();
                return LuaValue.NONE;
            }
        }, "Stops the active Position or Size animation at its current values.");
        registration.registerClassFunction("GuiObject", "IsAnimating", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(requireGuiObject(args.arg1(), "IsAnimating").isTweening());
            }
        }, "Returns whether this UI object currently has a running Position or Size animation.");
    }

    private static GuiObject requireGuiObject(LuaValue value, String method) {
        GuiBase element = requireGuiElement(value, method);
        if (!(element instanceof GuiObject object)) throw new LuaError("[MLua] " + method + " requires a GuiObject instance.");
        return object;
    }

    private static double positiveDuration(LuaValue value) {
        double duration = value.checkdouble();
        if (!Double.isFinite(duration) || duration <= 0.0) {
            throw new LuaError("[MLua] UI animation duration must be a finite number greater than zero.");
        }
        return duration;
    }

    private static String easingStyle(LuaValue value) {
        if (value.isnil()) return EasingStyle.Linear.name();
        EasingStyle style = MLuaEnumValue.toJava(value, EasingStyle.class);
        if (style == null) throw new LuaError("[MLua] UI animation easing style must be an Enum.EasingStyle value.");
        return style.name();
    }

    private static String easingDirection(LuaValue value) {
        if (value.isnil()) return EasingDirection.Out.name();
        EasingDirection direction = MLuaEnumValue.toJava(value, EasingDirection.class);
        if (direction == null) throw new LuaError("[MLua] UI animation easing direction must be an Enum.EasingDirection value.");
        return direction.name();
    }

    private static LuaTable registerClass(MLuaRegistration registration, String name, Class<?> instanceType,
                                         Supplier<? extends GuiBase> factory, String description,
                                         LuaTable parent) {
        LuaTable existing = registration.classTable(name);
        LuaTable classTable = existing != null ? existing : new LuaTable();
        classTable.rawset("__mlua_doc_type", name);
        classTable.rawset("__mlua_doc_properties", propertyMetadata(instanceType));
        classTable.rawset("__mlua_ui_java_type", LuaValue.userdataOf(instanceType));
        VarArgFunction constructor = null;
        if (factory != null) {
            constructor = new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    LuaValue receiver = args.arg1();
                    boolean calledWithClass = receiver.istable() && receiver.rawget("__class").toboolean();
                    String instanceClass = calledWithClass
                        ? receiver.rawget("__type").optjstring(name) : name;
                    String instanceName = args.arg(calledWithClass ? 2 : 1).optjstring(name);
                    GuiBase instance = factory.get();
                    if (instance == null || !instanceType.isInstance(instance)) {
                        throw new LuaError("[MLua] UI factory for class '" + name
                            + "' must return a live " + instanceType.getName() + " instance.");
                    }
                    instance.setScheduler(schedulerFor(registration));
                    instance.setRegistration(registration);
                    instance.setName(instanceName);
                    LuaTable liveInstance = registration.attachClassInstance(instance.toLua(), instanceClass, null);
                    return liveInstance;
                }
            };
        }
        if (existing == null && parent != null) {
            LuaTable meta = new LuaTable();
            meta.set("__index", parent);
            classTable.setmetatable(meta);
        }
        String parentName = parent == null ? null : parent.rawget("__type").optjstring("");
        classTable = registration.registerClassTable(name, classTable, parentName, description);
        if (constructor != null) {
            registration.registerClassFunction(name, "new", constructor,
                "Creates a new " + name + " instance.");
        }
        return classTable;
    }

    private static void registerCommonInstanceMethods(MLuaRegistration registration) {
        registerGuiBaseMethod(registration, "AddChild", "Parents a live UI instance to this instance and returns the child.",
            new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    GuiBase parent = requireGuiElement(args.arg1(), "AddChild");
                    LuaValue childValue = args.arg(2);
                    GuiBase child = childValue.istable()
                        ? MLuaTableRegistry.getElement(childValue.checktable()) : null;
                    if (child == null) throw new LuaError("[MLua] AddChild expects a live UI instance.");
                    try {
                        parent.addChild(child);
                    } catch (IllegalArgumentException failure) {
                        throw new LuaError("[MLua] " + failure.getMessage());
                    }
                    return child.getSelfTable();
                }
            });
        registerGuiBaseMethod(registration, "RemoveChild", "Detaches a direct child from this instance without destroying it.",
            new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    GuiBase parent = requireGuiElement(args.arg1(), "RemoveChild");
                    LuaValue childValue = args.arg(2);
                    GuiBase child = childValue.istable()
                        ? MLuaTableRegistry.getElement(childValue.checktable()) : null;
                    if (child == null || child.getParent() != parent) return LuaValue.FALSE;
                    parent.removeChild(child);
                    return LuaValue.TRUE;
                }
            });
        registerGuiBaseMethod(registration, "Destroy", "Removes this GUI instance and its descendants from their parent.",
            new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    LuaValue self = args.arg1();
                    if (self.istable() && self.checktable().rawget("__mlua_destroyed").toboolean()) {
                        return LuaValue.NONE;
                    }
                    requireGuiElement(self, "Destroy").destroy();
                    return LuaValue.NONE;
                }
            });
        registerGuiBaseMethod(registration, "GetChildren", "Returns this GUI instance's direct children.",
            new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    if (isDestroyedGui(args.arg1())) return new LuaTable();
                    LuaTable children = new LuaTable();
                    int index = 1;
                    for (GuiBase child : requireGuiElement(args.arg1(), "GetChildren").getChildren()) {
                        children.set(index++, child.getSelfTable());
                    }
                    return children;
                }
            });
        registerGuiBaseMethod(registration, "FindFirstChild",
            "Returns a child with the given name, or nil. Pass true for the recursive argument to search descendants.",
            new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    if (isDestroyedGui(args.arg1())) return LuaValue.NIL;
                    return findFirstChild(requireGuiElement(args.arg1(), "FindFirstChild"),
                        args.arg(2).checkjstring(), args.arg(3).optboolean(false));
                }
            });
        registerGuiBaseMethod(registration, "GetDescendants", "Returns every descendant UI instance in parent-first order.",
            new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    if (isDestroyedGui(args.arg1())) return new LuaTable();
                    LuaTable descendants = new LuaTable();
                    appendDescendants(requireGuiElement(args.arg1(), "GetDescendants"), descendants, 1);
                    return descendants;
                }
            });
        registerGuiBaseMethod(registration, "GetFullName", "Returns this instance's dot-separated path from its root UI instance.",
            new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    if (isDestroyedGui(args.arg1())) {
                        LuaValue self = args.arg1();
                        return LuaValue.valueOf(self.istable() ? self.checktable().get("Name").optjstring("") : "");
                    }
                    GuiBase element = requireGuiElement(args.arg1(), "GetFullName");
                    java.util.List<String> names = new java.util.ArrayList<>();
                    for (GuiBase current = element; current != null; current = current.getParent()) {
                        names.add(current.getName());
                    }
                    java.util.Collections.reverse(names);
                    return LuaValue.valueOf(String.join(".", names));
                }
            });
    }

    private static void registerGuiBaseMethod(MLuaRegistration registration, String name,
                                              String description, LuaValue implementation) {
        registration.registerClassFunction("GuiBase", name, implementation, description);
    }

    private static GuiBase requireGuiElement(LuaValue instance, String method) {
        GuiBase element = instance.istable() ? MLuaTableRegistry.getElement(instance.checktable()) : null;
        if (element == null) throw new LuaError("[MLua] " + method + " requires a live UI instance.");
        return element;
    }

    private static boolean isDestroyedGui(LuaValue instance) {
        return instance.istable() && instance.checktable().rawget("__mlua_destroyed").toboolean();
    }

    private static LuaTable propertyMetadata(Class<?> type) {
        Map<String, PropertyMetadata> access = new HashMap<>();
        for (Method method : type.getMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) continue;
            String methodName = method.getName();
            if (methodName.equals("getClass")) continue;
            String property = null;
            int accessIndex = -1;
            if (methodName.startsWith("get") && methodName.length() > 3 && method.getParameterCount() == 0
                && method.getReturnType() != Void.TYPE) {
                property = methodName.substring(3);
                accessIndex = 0;
            } else if (methodName.startsWith("is") && methodName.length() > 2 && method.getParameterCount() == 0
                && (method.getReturnType() == Boolean.TYPE || method.getReturnType() == Boolean.class)) {
                property = methodName.substring(2);
                accessIndex = 0;
            } else if (methodName.startsWith("set") && methodName.length() > 3 && method.getParameterCount() == 1) {
                property = methodName.substring(3);
                accessIndex = 1;
            }
            if (property == null || property.equals("SelfTable") || property.equals("Children")) continue;
            PropertyMetadata metadata = access.computeIfAbsent(property, ignored -> new PropertyMetadata());
            if (method.getReturnType() == dev.mlua.core.signal.MLuaSignal.class) {
                metadata.signal = true;
            } else if (accessIndex == 0) {
                metadata.readable = true;
                metadata.valueType = method.getReturnType();
            } else {
                metadata.writable = true;
                if (metadata.valueType == null) metadata.valueType = method.getParameterTypes()[0];
            }
        }
        PropertyMetadata parent = access.computeIfAbsent("Parent", ignored -> new PropertyMetadata());
        parent.readable = true;
        parent.writable = true;
        parent.valueType = GuiBase.class;

        LuaTable documented = new LuaTable();
        access.forEach((name, metadata) -> {
            LuaTable descriptor = new LuaTable();
            descriptor.set("readable", LuaValue.valueOf(metadata.readable));
            descriptor.set("writable", LuaValue.valueOf(metadata.writable));
            descriptor.set("signal", LuaValue.valueOf(metadata.signal));
            if (metadata.valueType != null) descriptor.set("type", LuaValue.valueOf(luaTypeName(metadata.valueType)));
            descriptor.set("side", LuaValue.valueOf("client:Client"));
            documented.set(name, descriptor);
        });
        return documented;
    }

    private static String luaTypeName(Class<?> type) {
        if (type == String.class || type == Character.class || type == char.class) return "string";
        if (type == Boolean.class || type == boolean.class) return "boolean";
        if (Number.class.isAssignableFrom(type) || type.isPrimitive() && type != boolean.class && type != char.class) return "number";
        if (org.luaj.vm2.LuaValue.class.isAssignableFrom(type)) return "table";
        if (type.isEnum()) return type.getSimpleName();
        return type.getSimpleName();
    }

    private static final class PropertyMetadata {
        private boolean readable;
        private boolean writable;
        private boolean signal;
        private Class<?> valueType;
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
                element.setScheduler(schedulerFor(registration));
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

    private static LuaValue findFirstChild(GuiBase parent, String name, boolean recursive) {
        for (GuiBase child : parent.getChildren()) {
            if (name.equals(child.getName())) return child.getSelfTable();
            if (recursive) {
                LuaValue nested = findFirstChild(child, name, true);
                if (!nested.isnil()) return nested;
            }
        }
        return LuaValue.NIL;
    }

    private static int appendDescendants(GuiBase parent, LuaTable results, int index) {
        for (GuiBase child : parent.getChildren()) {
            results.set(index++, child.getSelfTable());
            index = appendDescendants(child, results, index);
        }
        return index;
    }

    private static MLuaTaskSchedulerView schedulerFor(MLuaRegistration registration) {
        return registration instanceof MLuaRuntime runtime ? runtime.session().clientScheduler() : MLuaRuntime.get().session().clientScheduler();
    }

    public static LuaValue classOf(String className) {
        return classOf(className, MLuaRuntime.get());
    }

    public static LuaValue classOf(String className, MLuaRegistration registration) {
        LuaTable t = registration.classTable(className);
        return t != null ? t : LuaValue.NIL;
    }
}
