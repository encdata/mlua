package dev.mlua.modules.registry;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.minecraft.binding.MLuaRegistryProperties;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaRegistryEntry;
import dev.mlua.minecraft.binding.MLuaRegistryDefinition;
import dev.mlua.minecraft.binding.MLuaGeneratedDataResource;
import dev.mlua.minecraft.binding.MLuaGeneratedAssetResource;
import dev.mlua.minecraft.binding.MLuaContainerType;
import dev.mlua.module.MLuaRegistration;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Owns the public class bindings contributed by the Registry module. */
final class RegistryClassRegistration {
    private RegistryClassRegistration() {}

    static void register(MLuaRegistration registration) {
        MLuaRegistryProperties.registerClasses(registration);
        MLuaItemWrapper.classTable(registration);
        registration.registerClassFunction("Item", "Properties", new ZeroArgFunction() {
            @Override public LuaValue call() {
                return MLuaRegistryProperties.create(registration, "ItemProperties");
            }
        }, "Creates chainable item registration properties matching Minecraft Item.Properties.");
        MLuaBlockWrapper.classTable(registration);
        registration.registerClassFunction("Block", "Properties", new ZeroArgFunction() {
            @Override public LuaValue call() {
                return MLuaRegistryProperties.create(registration, "BlockProperties");
            }
        }, "Creates chainable block registration properties matching Minecraft BlockBehaviour.Properties.");
        registration.registerClass("Nbt", registration.requireInstanceBaseClassName(), "A live view of a Minecraft NBT compound that reads and writes its backing data.");
        MLuaClassMethods.registerInstanceMethod(registration, "Nbt", "Get", "Reads a value at the given NBT key.");
        MLuaClassMethods.registerInstanceMethod(registration, "Nbt", "Set", "Writes a value at the given NBT key.");
        MLuaClassMethods.registerInstanceMethod(registration, "Nbt", "Remove", "Removes the value at the given NBT key.");
        registration.registerClass("RegistryCategory", registration.requireInstanceBaseClassName(),
            "A live category API table exposed by the Registry service.");
        registration.registerClassFunction("RegistryCategory", "GetName", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                return self.checktable().get("__registry_category_name");
            }
        }, "Returns this category's canonical Registry table name.");
        registration.registerClassFunction("RegistryCategory", "GetDescription", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                return self.checktable().get("__mlua_doc_description");
            }
        }, "Returns the category's registered description.");
        registration.registerClassFunction("RegistryCategory", "GetMethods", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                LuaValue metadata = self.checktable().rawget("__mlua_doc_methods");
                java.util.List<String> names = new java.util.ArrayList<>();
                if (metadata.istable()) {
                    for (LuaValue key : metadata.checktable().keys()) {
                        if (key.isstring()) names.add(key.tojstring());
                    }
                }
                java.util.Collections.sort(names);
                LuaTable result = new LuaTable();
                for (int index = 0; index < names.size(); index++) {
                    result.set(index + 1, LuaValue.valueOf(names.get(index)));
                }
                return result;
            }
        }, "Lists the category-specific methods registered on this API table.");
        registration.registerClassFunction("RegistryCategory", "GetMethodDescription", new TwoArgFunction() {
            @Override public LuaValue call(LuaValue self, LuaValue name) {
                LuaValue metadata = self.checktable().rawget("__mlua_doc_methods");
                return metadata.istable() ? metadata.checktable().get(name) : LuaValue.NIL;
            }
        }, "Returns the registered description for one category method, or nil when that method is unavailable.");
        registration.registerClassProperty("RegistryCategory", "Name", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                return self.checktable().get("__registry_category_name");
            }
        }, LuaValue.NIL, "string", "shared:Shared", "This category's canonical Registry table name.");
        registration.registerClassProperty("RegistryCategory", "Description", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                return self.checktable().get("__mlua_doc_description");
            }
        }, LuaValue.NIL, "string", "shared:Shared", "The category's registered description.");
        registration.registerClass("ScriptedEntity", "Mob",
            "A registered Lua-controlled mob with navigation, sensing, combat, and persistent AI data.");
        String[][] aiMethods = {
            {"MoveTo", "On the server, finds a path to a Vector3 or CFrame position using a finite positive speed. The 26.1 implementation also accepts x, y, z coordinates."},
            {"NavigateTo", "Alias for MoveTo; accepts a Vector3 or CFrame position and a finite positive speed. The 26.1 implementation also accepts x, y, z coordinates."},
            {"StopMoving", "On the server, stops the current navigation path and reports whether a path was active."},
            {"LookAt", "On the server, turns the mob toward a native Vector3 or CFrame position."},
            {"AttackTarget", "On the server, sets or clears this mob's target; a target must be in the same dimension."},
            {"GetTarget", "Returns the mob's current live target, or nil."},
            {"CanSee", "Returns whether this mob has line of sight to a live entity in the same dimension."},
            {"GetBehaviorKey", "Returns the registered content key that defines this mob's behavior."},
            {"GetAIState", "Returns the mob's active scripted AI state, or nil."},
            {"GetAIStateTicks", "Returns the number of ticks spent in the active AI state."},
            {"SetAIState", "On the server, changes the active scripted AI state and runs its exit and enter callbacks. Calls made from a transition callback are queued instead of recursing; MLua applies at most " + dev.mlua.minecraft.scripted.MLuaScriptedEntity.MAX_AI_TRANSITIONS_PER_REQUEST + " queued transitions per request. It re-enables scripted AI callbacks for the selected state after a failure."},
            {"FindNearestPlayer", "Finds the nearest living player within a finite range capped at 128 blocks, optionally requiring line of sight."},
            {"FindNearestEntity", "Finds the nearest living entity within a finite range capped at 128 blocks, optionally filtering by entity type."},
            {"FindNearestBlock", "Finds the nearest matching block in a spherical radius capped at 16 blocks; unloaded chunks are skipped."},
            {"OpenDoor", "On the server, opens or toggles a door at the given position."},
            {"CloseDoor", "On the server, closes a door at the given position."},
            {"IsDoorOpen", "Returns whether the door at the given position is open."},
            {"SetDoorBreakProgress", "On the server, shows door breaking progress from 0 through 9, or clears it with -1."},
            {"BreakDoor", "On the server, breaks a wooden door at the given position."},
            {"SetCanOpenDoors", "On the server, enables or disables opening doors while navigating."},
            {"SetCanPassDoors", "On the server, enables or disables pathfinding through doors."},
            {"GetData", "Returns persistent Lua data saved with this mob. Conversion rejects data deeper than 64 levels or larger than 16,384 total entries."},
            {"SetData", "On the server, replaces persistent Lua data after conversion; map keys must be strings, arrays must be dense and one-based, and only booleans, finite numbers, strings, maps, and arrays are supported. Cycles, mixed-key tables, mixed-type NBT arrays, nesting deeper than 64 levels, or more than 16,384 total entries are rejected."},
            {"RandomStroll", "Chooses a nearby safe position and walks there."},
            {"Panic", "Moves away from the last attacker or chooses a random escape direction."},
            {"DistanceTo", "Returns the distance to another entity instance."},
            {"MeleeAttack", "Attacks a living target in reach, with an optional whole-number cooldown in ticks clamped to 1 through 200."}
        };
        for (String[] method : aiMethods) {
            MLuaClassMethods.registerInstanceMethod(registration, "ScriptedEntity", method[0], method[1]);
        }
        MLuaClassMethods.registerInstanceProperty(registration, "ScriptedEntity", "BehaviorKey",
            "GetBehaviorKey", null, "string", "shared:Shared",
            "The registered content key that selects this scripted mob's behavior definition.");
        MLuaBlockWrapper.classTable(registration);
        MLuaRegistryEntry.registerClass(registration);
        MLuaRegistryDefinition.registerClass(registration);
        MLuaGeneratedDataResource.registerClass(registration);
        MLuaGeneratedAssetResource.registerClass(registration);
        MLuaContainerType.registerClass(registration);
    }
}
