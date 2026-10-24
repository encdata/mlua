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
        registry.register("Third", "Second");

        assertThrows(IllegalStateException.class, () -> registry.register("First", "Third"));
    }

    @Test
    void ancestryQueriesWalkDeepSingleParentChains() {
        MLuaClassRegistry registry = new MLuaClassRegistry();
        registry.register("Root", (String) null);
        registry.register("Middle", "Root");
        registry.register("Leaf", "Middle");
        LuaTable instance = new LuaTable();
        registry.attachInstance(instance, "Leaf", null);

        assertTrue(registry.isA(instance, "Leaf"));
        assertTrue(registry.isA(instance, "Middle"));
        assertTrue(registry.isA(instance, "Root"));
        assertFalse(registry.isA(instance, "Unrelated"));
        assertTrue(registry.isSubclassOf("Leaf", "Leaf"));
        assertTrue(registry.isSubclassOf("Unregistered", "Unregistered"));
        assertTrue(registry.isSubclassOf("Leaf", "Root"));
        assertFalse(registry.isSubclassOf("Root", "Leaf"));
        assertFalse(registry.isSubclassOf("Leaf", "Unrelated"));
    }

    @Test
    void inheritedPropertyChecksFindLiveInstancesThroughTheClassIndex() {
        MLuaClassRegistry registry = new MLuaClassRegistry();
        registry.register("Base", (String) null);
        registry.register("Child", "Base");
        LuaTable child = new LuaTable();
        child.set("health", LuaValue.valueOf(20));
        registry.attachInstance(child, "Child", null);

        IllegalStateException conflict = assertThrows(IllegalStateException.class,
            () -> registry.registerProperty("Base", "health", new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) { return LuaValue.valueOf(20); }
            }, LuaValue.NIL, "number", "shared:Shared", "Current health."));

        assertTrue(conflict.getMessage().contains("live Child instance"));
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
    void classCannotBeRemovedWhileAnInstanceUsesIt() {
        MLuaClassRegistry registry = new MLuaClassRegistry();
        registry.register("Widget", (String) null);
        registry.attachInstance(new LuaTable(), "Widget", null);

        assertFalse(registry.unregister("Widget"));
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
    void instanceLookupResolvesInheritedPropertiesAndEnforcesReadOnlyWrites() {
        MLuaClassRegistry registry = new MLuaClassRegistry();
        LuaTable parent = registry.register("BaseWidget", (String) null);
        registry.register("Widget", "BaseWidget");
        VarArgFunction healthGetter = new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return args.arg1().get("health"); }
        };
        VarArgFunction healthSetter = new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                args.arg1().set("health", args.arg(2));
                return LuaValue.NONE;
            }
        };
        registry.registerProperty("BaseWidget", "Health", healthGetter, healthSetter,
            "number", "shared:Shared", "Current health.");
        registry.registerProperty("BaseWidget", "Name", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return args.arg1().get("name"); }
        }, LuaValue.NIL, "string", "shared:Shared", "Widget name.");

        LuaTable instance = new LuaTable();
        instance.set("health", LuaValue.valueOf(20));
        instance.set("name", LuaValue.valueOf("door"));
        registry.attachInstance(instance, "Widget", null);

        assertEquals(20, instance.get("Health").toint());
        instance.set("Health", LuaValue.valueOf(12));
        assertEquals(12, instance.get("health").toint());
        assertEquals("door", instance.get("Name").tojstring());
        LuaError failure = assertThrows(LuaError.class,
            () -> instance.set("Name", LuaValue.valueOf("gate")));
        assertTrue(failure.getMessage().contains("read-only"));
        assertTrue(parent.rawget("__mlua_properties").get("Health").istable());
    }

    @Test
    void refreshingAClassSkipsFrozenInstancesAndStillUpdatesMutableInstances() {
        MLuaClassRegistry registry = new MLuaClassRegistry();
        LuaTable classTable = registry.register("Widget", (String) null, "A widget.");
        LuaTable frozen = new LuaTable();
        registry.attachInstance(frozen, "Widget", null);
        frozen.freeze();
        LuaTable mutable = new LuaTable();
        registry.attachInstance(mutable, "Widget", null);

        assertTrue(registry.register("Widget", (String) null) == classTable);

        VarArgFunction describe = new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return LuaValue.valueOf("widget"); }
        };
        registry.registerFunction(classTable, "Describe", describe, "Describes this widget.");

        assertTrue(frozen.isfrozen());
        assertTrue(mutable.get("Describe").call(mutable).eq_b(LuaValue.valueOf("widget")));
    }

    @Test
    void propertiesCannotReuseAClassFunctionName() {
        MLuaClassRegistry registry = new MLuaClassRegistry();
        LuaTable classTable = registry.register("ModelPart", (String) null);
        VarArgFunction isValid = new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return LuaValue.TRUE; }
        };
        registry.registerFunction(classTable, "IsValid", isValid, "Checks whether this handle is live.");

        IllegalStateException collision = assertThrows(IllegalStateException.class, () ->
            registry.registerProperty("ModelPart", "IsValid", new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) { return LuaValue.TRUE; }
            }, LuaValue.NIL, "boolean", "shared:Shared", "Checks whether this handle is live."));

        assertTrue(collision.getMessage().contains("already defines member 'IsValid'"));
        assertTrue(classTable.rawget("IsValid") == isValid);
        VarArgFunction validGetter = new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return LuaValue.TRUE; }
        };
        registry.registerProperty("ModelPart", "Valid", validGetter, LuaValue.NIL,
            "boolean", "shared:Shared", "Whether this handle is live.");
        assertTrue(classTable.rawget("__mlua_properties").get("Valid").get("get") == validGetter);
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

    @Test
    void behaviorMixinDefinitionsAreSharedAndInheritedByInstances() {
        MLuaClassRegistry registry = new MLuaClassRegistry();
        LuaTable parent = registry.register("Parent", (String) null);
        registry.register("Child", "Parent");
        LuaTable mixin = registry.registerMixin("Interactable", "Adds a shared interaction action.");
        assertFalse(registry.hasMixin("Child", "Interactable"));
        VarArgFunction interact = new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf("interacted:" + args.arg1().get("name").tojstring());
            }
        };

        registry.registerMixinFunction("Interactable", "Interact", interact, "Runs the interaction action.");
        registry.applyMixin("Parent", "Interactable");
        LuaTable first = new LuaTable();
        first.set("name", "door");
        registry.attachInstance(first, "Child", null);

        assertTrue(registry.mixin("Interactable") == mixin);
        assertTrue(registry.hasMixin("Child", "Interactable"));
        assertTrue(mixin.get("Interact") == interact);
        assertEquals("interacted:door", first.get("Interact").call(first).tojstring());
        assertEquals("Runs the interaction action.",
            parent.rawget("__mlua_doc_inherited_methods").get("Interact").tojstring());
        assertTrue(registry.removeMixin("Parent", "Interactable"));
        assertFalse(registry.hasMixin("Child", "Interactable"));
    }

    @Test
    void pendingMixinRemovalWaitsUntilItsLastClassDetachesIt() {
        MLuaClassRegistry registry = new MLuaClassRegistry();
        registry.register("Widget", (String) null);
        registry.registerMixin("Shared", "Adds a shared method.");
        registry.registerMixinFunction("Shared", "Use", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return LuaValue.NONE; }
        }, "Uses the shared behavior.");
        registry.applyMixin("Widget", "Shared");

        assertFalse(registry.removeMixin("Shared"));
        assertTrue(registry.hasMixin("Widget", "Shared"));
        assertTrue(registry.removeMixin("Widget", "Shared"));
        assertFalse(registry.hasMixin("Widget", "Shared"));
        assertTrue(registry.mixin("Shared") == null);
    }
}
