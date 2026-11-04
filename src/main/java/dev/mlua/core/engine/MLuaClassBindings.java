package dev.mlua.core.engine;

import dev.mlua.core.MLuaContext;
import dev.mlua.module.MLuaRegistration;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Owns Lua class, instance, and behavior-mixin registration APIs. */
final class MLuaClassBindings {
    private MLuaClassBindings() { }

    static void bindInto(LuaTable table, MLuaContext context) {
        final LuaTable classes = registeredClasses(context.registration());
        final LuaTable behaviorMixins = registeredBehaviorMixins(context.registration());
        table.set("RegisterClass", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                MLuaRegistration registration = MLuaLuaBindings.requireMutableRegistration(context);
                String name = args.arg(2).checkjstring();
                LuaValue parent = args.arg(3);
                String description = args.arg(4).checkjstring();
                if (description.isBlank()) {
                    throw new LuaError("[MLua] RegisterClass requires a non-empty class description.");
                }
                String parentName = classParentName(registration, parent);
                LuaTable registered = registration.registerInstantiableClass(name, parentName, description);
                classes.rawset(name, registered);
                return registered;
            }
        });
        table.set("UnregisterClass", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue name) {
                String className = name.checkjstring();
                boolean removed = MLuaLuaBindings.requireMutableRegistration(context).unregisterClass(className);
                if (removed) classes.rawset(className, LuaValue.NIL);
                return LuaValue.valueOf(removed);
            }
        });
        table.set("GetClass", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue name) {
                LuaTable registered = requireRegistration(context).classTable(name.checkjstring());
                return registered == null ? LuaValue.NIL : registered;
            }
        });
        table.set("GetClasses", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                LuaTable names = new LuaTable();
                int index = 1;
                for (String name : requireRegistration(context).classNames()) {
                    names.rawset(index++, LuaValue.valueOf(name));
                }
                return names;
            }
        });
        table.set("GetBehaviorMixins", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                LuaTable names = new LuaTable();
                int index = 1;
                for (String name : requireRegistration(context).behaviorMixinNames()) {
                    names.rawset(index++, LuaValue.valueOf(name));
                }
                return names;
            }
        });
        table.set("RegisterBehaviorMixin", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                MLuaRegistration registration = MLuaLuaBindings.requireMutableRegistration(context);
                String name = args.arg(2).checkjstring();
                String description = args.arg(3).checkjstring();
                LuaTable mixin = MLuaBehaviorMixinApi.register(registration, name, description, args.arg(4));
                behaviorMixins.rawset(name, mixin);
                return mixin;
            }
        });
        table.set("RegisterBehaviorMixinFunction", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return MLuaLuaBindings.requireMutableRegistration(context).registerBehaviorMixinFunction(
                    args.arg(2).checkjstring(), args.arg(3).checkjstring(), args.arg(4), args.arg(5).checkjstring());
            }
        });
        table.set("UnregisterBehaviorMixin", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue name) {
                String mixinName = name.checkjstring();
                boolean removed = MLuaLuaBindings.requireMutableRegistration(context).unregisterBehaviorMixin(mixinName);
                if (removed) behaviorMixins.rawset(mixinName, LuaValue.NIL);
                return LuaValue.valueOf(removed);
            }
        });
        table.set("UnregisterBehaviorMixinFunction", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                MLuaRegistration registration = MLuaLuaBindings.requireMutableRegistration(context);
                String mixinName = args.arg(2).checkjstring();
                String functionName = args.arg(3).checkjstring();
                LuaTable mixin = registration.behaviorMixinTable(mixinName);
                LuaValue expected = args.narg() >= 4 ? args.arg(4) : mixin == null ? LuaValue.NIL : mixin.get(functionName);
                return LuaValue.valueOf(registration.unregisterBehaviorMixinFunction(mixinName, functionName, expected));
            }
        });
        table.set("ApplyBehaviorMixin", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                MLuaLuaBindings.requireMutableRegistration(context).applyBehaviorMixin(args.arg(2).checkjstring(), args.arg(3).checkjstring());
                return LuaValue.NONE;
            }
        });
        table.set("RemoveBehaviorMixin", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(MLuaLuaBindings.requireMutableRegistration(context).removeBehaviorMixin(
                    args.arg(2).checkjstring(), args.arg(3).checkjstring()));
            }
        });
        table.set("RegisterClassFunction", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                MLuaRegistration registration = MLuaLuaBindings.requireMutableRegistration(context);
                return registration.registerClassFunction(args.arg(2).checkjstring(),
                    args.arg(3).checkjstring(), args.arg(4), args.arg(5).checkjstring());
            }
        });
        table.set("UnregisterClassFunction", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                MLuaRegistration registration = MLuaLuaBindings.requireMutableRegistration(context);
                String className = args.arg(2).checkjstring();
                String functionName = args.arg(3).checkjstring();
                LuaTable type = registration.classTable(className);
                LuaValue expected = args.narg() >= 4 ? args.arg(4) : type == null ? LuaValue.NIL : type.rawget(functionName);
                return LuaValue.valueOf(registration.unregisterClassFunction(className, functionName, expected));
            }
        });
        table.set("RegisterClassProperty", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                MLuaRegistration registration = MLuaLuaBindings.requireMutableRegistration(context);
                String className = args.arg(2).checkjstring();
                String propertyName = args.arg(3).checkjstring();
                LuaValue getter = args.arg(4);
                LuaValue setter;
                String description;
                if (args.narg() >= 7) {
                    boolean readOnly = args.narg() == 7;
                    setter = readOnly ? LuaValue.NIL : args.arg(5);
                    String valueType = args.arg(readOnly ? 5 : 6).checkjstring();
                    String side = args.arg(readOnly ? 6 : 7).checkjstring();
                    description = args.arg(readOnly ? 7 : 8).checkjstring();
                    return registration.registerClassProperty(className, propertyName,
                        getter, setter, valueType, side, description);
                } else if (args.narg() == 5 && args.arg(5).isstring()) {
                    setter = LuaValue.NIL;
                    description = args.arg(5).checkjstring();
                } else {
                    setter = args.arg(5);
                    description = args.arg(6).checkjstring();
                }
                return registration.registerClassProperty(className, propertyName,
                    getter, setter, description);
            }
        });
        table.set("UnregisterClassProperty", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                MLuaRegistration registration = MLuaLuaBindings.requireMutableRegistration(context);
                String className = args.arg(2).checkjstring();
                String propertyName = args.arg(3).checkjstring();
                LuaTable type = registration.classTable(className);
                LuaValue descriptorValue = type == null ? LuaValue.NIL : type.rawget("__mlua_properties").get(propertyName);
                LuaValue expectedGetter = args.narg() >= 4 ? args.arg(4)
                    : descriptorValue.istable() ? descriptorValue.get("get") : LuaValue.NIL;
                return LuaValue.valueOf(registration.unregisterClassProperty(className, propertyName, expectedGetter));
            }
        });
        table.set("CreateInstance", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                MLuaRegistration registration = requireRegistration(context);
                LuaValue fields = args.arg(3);
                if (!fields.isnil() && !fields.istable()) {
                    throw new LuaError("[MLua] CreateInstance fields must be a table when provided.");
                }
                LuaValue classValue = args.arg(2);
                String className;
                if (classValue.isstring()) {
                    className = classValue.checkjstring();
                } else if (classValue.istable()) {
                    LuaTable classTable = classValue.checktable();
                    LuaValue type = classTable.rawget("__type");
                    LuaTable registeredClass = type.isstring() ? registration.classTable(type.tojstring()) : null;
                    boolean registeredView = registeredClass != null
                        && registeredClassForView(registration, classTable) == registeredClass;
                    if (!classTable.rawget("__class").toboolean() || registeredClass == null
                        || (registeredClass != classTable && !registeredView)) {
                        throw new LuaError("[MLua] CreateInstance requires a class registered in this runtime.");
                    }
                    className = type.tojstring();
                } else {
                    throw new LuaError("[MLua] CreateInstance expects a registered class or class name.");
                }
                return registration.createClassInstance(className,
                    fields.istable() ? fields.checktable() : null);
            }
        });
        table.set("Classes", classes);
        table.set("BehaviorMixins", behaviorMixins);
    }

   private static MLuaRegistration requireRegistration(MLuaContext context) {
      MLuaRegistration registration = context.registration();
      if (registration == null) {
         throw new LuaError("[MLua] Class registration is unavailable in this script context.");
      }
      return registration;
   }

   private static LuaTable registeredClassForView(MLuaRegistration registration, LuaTable candidate) {
      LuaValue source = candidate.rawget("__mlua_registered_class_view_source");
      if (!source.isuserdata() || !(source.touserdata() instanceof LuaTable classTable)) return null;
      String className = classTable.rawget("__type").optjstring("");
      return !className.isBlank() && candidate.rawget("__type").optjstring("").equals(className)
         && registration.classTable(className) == classTable ? classTable : null;
   }

   private static String classParentName(MLuaRegistration registration, LuaValue value) {
      if (value.isnil()) return null;
      if (value.isstring()) return value.checkjstring();
      if (!value.istable()) {
         throw new LuaError("[MLua] RegisterClass parent must be one class name, registered class, or nil.");
      }
      LuaTable parentClass = value.checktable();
      LuaValue type = parentClass.rawget("__type");
      if (!parentClass.rawget("__class").toboolean() || !type.isstring()) {
         throw new LuaError("[MLua] RegisterClass parent must be one registered class, not a parent list.");
      }
      LuaTable registeredClass = registration.classTable(type.tojstring());
      boolean registeredView = registeredClass != null
         && registeredClassForView(registration, parentClass) == registeredClass;
      if (registeredClass == null || (registeredClass != parentClass && !registeredView)) {
         throw new LuaError("[MLua] RegisterClass parent must be a class registered in this runtime.");
      }
      return type.tojstring();
    }

    private static LuaTable registeredClasses(MLuaRegistration registration) {
        LuaTable classes = new LuaTable();
        classes.rawset("__mlua_doc_category", LuaValue.valueOf("Classes"));
        if (registration == null) return classes;
        for (String name : registration.classNames()) {
            LuaTable classTable = registration.classTable(name);
            if (classTable != null) classes.rawset(name, classTable);
        }
        LuaTable meta = new LuaTable();
        meta.set("__index", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue key) {
                if (!key.isstring()) return LuaValue.NIL;
                LuaTable registered = registration.classTable(key.tojstring());
                return registered != null ? registered : LuaValue.NIL;
            }
        });
        classes.setmetatable(meta);
        return classes;
    }

    private static LuaTable registeredBehaviorMixins(MLuaRegistration registration) {
        LuaTable mixins = new LuaTable();
        if (registration == null) return mixins;
        for (String name : registration.behaviorMixinNames()) {
            LuaTable mixin = registration.behaviorMixinTable(name);
            if (mixin != null) mixins.rawset(name, mixin);
        }
        LuaTable meta = new LuaTable();
        meta.set("__index", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue key) {
                if (!key.isstring()) return LuaValue.NIL;
                LuaTable registered = registration.behaviorMixinTable(key.tojstring());
                return registered != null ? registered : LuaValue.NIL;
            }
        });
        mixins.setmetatable(meta);
      return mixins;
    }
}
