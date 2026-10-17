package dev.mlua.core.classes;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.module.MLuaRegistration;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaClassRegistryTest {
    @Test
    void customClassTablesInheritFromOneSuperclass() {
        MLuaClassRegistry registry = new MLuaClassRegistry();
        LuaTable firstParent = registry.register("First", (String) null);
        registry.register("Second", (String) null);
        firstParent.set("Shared", "first");
        firstParent.set("FirstOnly", "first-only");
        LuaTable child = new LuaTable();
        child.set("Shared", "child");

        registry.register("Child", child, "First");

        assertEquals("child", child.get("Shared").tojstring());
        child.set("Shared", LuaValue.NIL);
        assertEquals("first", child.get("Shared").tojstring());
        assertEquals("first-only", child.get("FirstOnly").tojstring());
        LuaTable instance = new LuaTable();
        registry.attachInstance(instance, "Child", null);
        assertTrue(registry.isA(instance, "First"));
        LuaTable describedChild = registry.register("DescribedChild", new LuaTable(), "First",
            "A child class with one superclass.");
        assertEquals("First", describedChild.rawget("__mlua_doc_parents").get(1).tojstring());
        assertEquals("A child class with one superclass.",
            describedChild.rawget("__mlua_doc_description").tojstring());
    }

    @Test
    void inheritanceCyclesAreRejected() {
        MLuaClassRegistry registry = new MLuaClassRegistry();
        registry.register("First", (String) null);
        registry.register("Second", "First");

        assertThrows(IllegalStateException.class, () -> registry.register("First", "Second"));
    }

    @Test
    void parentClassCannotBeRemovedWhileAChildDependsOnIt() {
        MLuaClassRegistry registry = new MLuaClassRegistry();
        LuaTable parent = registry.register("Parent", (String) null);
        parent.set("Inherited", "value");
        LuaTable child = registry.register("Child", "Parent");

        assertEquals("value", child.get("Inherited").tojstring());
        LuaTable instance = new LuaTable();
        registry.attachInstance(instance, "Child", null);
        assertTrue(registry.isA(instance, "Parent"));
        assertFalse(registry.unregister("Parent"));

        assertEquals("value", child.get("Inherited").tojstring());
        assertTrue(registry.isA(instance, "Parent"));
    }

    @Test
    void attachingClassPreservesExistingInstanceIndexFallback() {
        MLuaClassRegistry registry = new MLuaClassRegistry();
        LuaTable classTable = registry.register("Widget", (String) null);
        classTable.set("ClassMethod", "class-value");
        LuaTable fallback = new LuaTable();
        fallback.set("Existing", "fallback-value");
        LuaTable metatable = new LuaTable();
        metatable.set("__index", fallback);
        LuaTable instance = new LuaTable();
        instance.setmetatable(metatable);

        registry.attachInstance(instance, "Widget", null);

        assertEquals("class-value", instance.get("ClassMethod").tojstring());
        assertEquals("fallback-value", instance.get("Existing").tojstring());
    }

    @Test
    void classFunctionsAreSharedAndReceiveTheLiveInstanceAsSelf() {
        MLuaClassRegistry registry = new MLuaClassRegistry();
        LuaTable classTable = registry.register("ModelPart", (String) null);
        VarArgFunction getName = new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return args.arg1().get("name");
            }
        };
        registry.registerFunction(classTable, "GetName", getName, "Returns the model part name.");

        LuaTable first = new LuaTable();
        first.set("name", "left_arm");
        registry.attachInstance(first, "ModelPart", null);
        LuaTable second = new LuaTable();
        second.set("name", "right_arm");
        registry.attachInstance(second, "ModelPart", null);

        assertEquals("left_arm", first.get("GetName").call(first).tojstring());
        assertEquals("right_arm", second.get("GetName").call(second).tojstring());
        assertTrue(first.get("GetName") == getName);
        assertTrue(second.get("GetName") == getName);
    }

    @Test
    void serviceClassesCanOnlyBeMaterializedThroughTheirServiceLifecycle() {
        LuaTable serviceClass = new LuaTable();
        serviceClass.set("__mlua_service_class", LuaValue.valueOf(true));
        MLuaRegistration registration = new MLuaRegistration() {
            @Override public LuaTable registerClass(String name, String parentName) { throw new UnsupportedOperationException(); }
            @Override public LuaTable registerClass(String name, String parentName, String description) { throw new UnsupportedOperationException(); }
            @Override public LuaTable registerClassTable(String name, LuaTable classTable, String parentName) { throw new UnsupportedOperationException(); }
            @Override public LuaTable registerClassTable(String name, LuaTable classTable, String parentName, String description) { throw new UnsupportedOperationException(); }
            @Override public LuaTable classTable(String name) { return "Alerts".equals(name) ? serviceClass : null; }
        };

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
            () -> registration.createClassInstance("Alerts"));
        assertTrue(failure.getMessage().contains("Minecraft:GetService"));
    }

    @Test
    void newClassFunctionsMustUseDescribedRegistration() {
        MLuaClassRegistry registry = new MLuaClassRegistry();
        LuaTable classTable = registry.register("AddonClass", (String) null);
        VarArgFunction function = new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return LuaValue.NONE; }
        };

        LuaError failure = assertThrows(LuaError.class, () -> classTable.set("Undocumented", function));
        assertTrue(failure.getMessage().contains("RegisterClassFunction"));

        registry.registerFunction(classTable, "Documented", function, "Runs the addon class action.");
        assertTrue(classTable.rawget("Documented") == function);
        assertEquals("Runs the addon class action.",
            classTable.rawget("__mlua_doc_methods").get("Documented").tojstring());
        classTable.set("ClassConstant", "value");
        assertEquals("value", classTable.get("ClassConstant").tojstring());
    }
}
