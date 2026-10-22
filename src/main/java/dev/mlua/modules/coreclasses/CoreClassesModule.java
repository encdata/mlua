package dev.mlua.modules.coreclasses;

import dev.mlua.core.MLuaContextView;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import java.util.List;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class CoreClassesModule implements MLuaModule {
    @Override
    public String id() { return "core-classes"; }

    @Override
    public int priority() { return -100; }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        registration.registerClass("Object", null, "Root class in MLua's Lua class hierarchy.");
        registration.registerInstanceBaseClass("Instance", "Object", "Base class for live wrappers around Minecraft objects.");
        registerInstanceMethods(registration);
        registration.registerClass("Signal", registration.requireInstanceBaseClassName(),
            "A live event source that connects callbacks, fires values, and resumes waiters.");
        MLuaClassMethods.registerInstanceMethod(registration, "Signal", "Connect",
            "Connects a callback that runs whenever the signal fires.");
        MLuaClassMethods.registerInstanceMethod(registration, "Signal", "Once",
            "Connects a callback that disconnects after its first call.");
        MLuaClassMethods.registerInstanceMethod(registration, "Signal", "Fire",
            "Calls connected callbacks and resumes waiters with these arguments.");
        MLuaClassMethods.registerInstanceMethod(registration, "Signal", "Wait",
            "Yields the current coroutine until the signal fires, then returns the arguments passed to Fire.");
        registration.registerClass("Connection", registration.requireInstanceBaseClassName(),
            "A live handle for an owned callback registration, including signals, render passes, and addon events.");
        MLuaClassMethods.registerInstanceMethod(registration, "Connection", "Disconnect",
            "Removes this callback registration so it no longer receives events or render frames.");
        MLuaClassMethods.registerInstanceMethod(registration, "Connection", "GetConnected",
            "Returns whether this callback registration is still active.");
        MLuaClassMethods.registerInstanceProperty(registration, "Connection", "Connected", "GetConnected", null,
            "boolean", "shared:Shared", "Whether this callback registration is still active.");
        registration.registerServiceBaseClass("Service", registration.requireInstanceBaseClassName(), "Base class for live Lua service instances.");
        registration.registerClassFunction("Service", "GetName", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String instanceClassName = registration.classNameOf(args.arg1());
                if (instanceClassName == null) return LuaValue.NIL;
                LuaTable instanceClass = registration.classTable(instanceClassName);
                LuaValue serviceName = instanceClass == null ? LuaValue.NIL : instanceClass.rawget("__mlua_service_name");
                return serviceName.isstring() ? serviceName : LuaValue.NIL;
            }
        }, "Returns this service's registered name.");
        MLuaClassMethods.registerInstanceProperty(registration, "Service", "Name", "GetName", null,
            "string", "shared:Shared", "This service's registered name.");
    }

    private static void registerInstanceMethods(MLuaRegistration registration) {
        registerInstanceFunction(registration, "GetClassName", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue self = args.arg1();
                String type = registration.classNameOf(self);
                return type == null ? LuaValue.valueOf(self.typename()) : LuaValue.valueOf(type);
            }
        }, "Returns this instance's registered MLua class name.");
        registerInstanceFunction(registration, "GetClass", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String type = registration.classNameOf(args.arg1());
                if (type == null) return LuaValue.NIL;
                LuaValue classTable = registration.classTable(type);
                return classTable == null ? LuaValue.NIL : classTable;
            }
        }, "Returns the registered class table that defines this instance's behavior.");
        registerInstanceFunction(registration, "GetSuperclass", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String type = registration.classNameOf(args.arg1());
                if (type == null) return LuaValue.NIL;
                LuaTable classTable = registration.classTable(type);
                LuaValue parents = classTable == null ? LuaValue.NIL : classTable.rawget("__mlua_doc_parents");
                if (!parents.istable()) return LuaValue.NIL;
                String parentName = parents.checktable().get(1).optjstring("");
                if (parentName.isBlank()) return LuaValue.NIL;
                LuaTable superclass = registration.classTable(parentName);
                return superclass == null ? LuaValue.NIL : superclass;
            }
        }, "Returns the immediate superclass table, or nil when this class has no superclass.");
        registerInstanceFunction(registration, "GetType", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue self = args.arg1();
                String type = registration.classNameOf(self);
                return type == null ? LuaValue.valueOf(self.typename()) : LuaValue.valueOf(type);
            }
        }, "Returns this instance's registered MLua runtime type.");
        registerInstanceFunction(registration, "IsA", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue target = args.arg(2);
                if (target.isnil()) return LuaValue.FALSE;
                String targetName;
                if (target.istable()) {
                    LuaTable targetClass = target.checktable();
                    targetName = targetClass.rawget("__type").optjstring("");
                    if (!targetClass.rawget("__class").toboolean()
                        || registration.classTable(targetName) != targetClass) return LuaValue.FALSE;
                } else {
                    targetName = target.checkjstring();
                }
                return LuaValue.valueOf(registration.isInstanceOf(args.arg1(), targetName));
            }
        }, "Returns whether this instance inherits from or matches the given class.");
        registerInstanceFunction(registration, "ToString", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue self = args.arg1();
                String type = registration.classNameOf(self);
                return type == null ? LuaValue.valueOf(self.typename()) : LuaValue.valueOf(type);
            }
        }, "Returns this instance's registered MLua class name as text.");
    }

    private static void registerInstanceFunction(MLuaRegistration registration, String methodName,
                                                 LuaValue implementation, String description) {
        LuaValue existing = registration.classTable("Instance").rawget(methodName);
        if (existing.isfunction()) implementation = existing;
        registration.registerClassFunction("Instance", methodName, implementation, description);
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        CoreClassesDocumentation.contribute(docs);
    }

}
