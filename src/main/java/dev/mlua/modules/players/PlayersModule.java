package dev.mlua.modules.players;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaContextView;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.common.enums.Side;
import dev.mlua.loaderapi.EventBridge;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.modules.players.Service.MLuaPlayersService;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.runtime.data.MLuaDataManager;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.mcapi.MCApiRuntime;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public class PlayersModule implements MLuaModule {
    @Override
    public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
        MLuaEntityWrapper.clearCache(runtime);
    }

    @Override
    public void onRuntimeTick(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, double deltaTime) {
        MLuaEntityWrapper.cleanCache();
    }

    @Override public void onReload() { clearState(); }
    @Override public void onReload(dev.mlua.common.enums.Side side) {
        if (side == dev.mlua.common.enums.Side.CLIENT || side == dev.mlua.common.enums.Side.BOTH) {
            MLuaDataManager.clearClientCache();
        }
    }
    @Override public void onUnload() { clearState(); }

    private static void clearState() {
        MLuaDataManager.clearClientCache();
    }
    @Override
    public String id() { return "players"; }

    @Override
    public List<String> dependencies() { return List.of("core-classes"); }

    @Override
    public int priority() { return 10; }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder players = ServiceEntry.builder("Players", MLuaPlayersService::new)
            .reflective(MLuaPlayersService.class);
        return List.of(players.build());
    }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        registration.registerClass("Entity", registration.requireInstanceBaseClassName(),
            "A live entity instance with position, movement, health, and interaction APIs.");
        registerEntityBoundsClass(registration);
        registration.registerClassFunction("Entity", "GetId",
            entityMethod(registration, entity -> LuaValue.valueOf(entity.getId())), "Returns this entity's runtime ID.");
        LuaValue entityType = entityMethod(registration, entity -> LuaValue.valueOf(MCApiRuntime.registryKey("entity_type", entity.getType())));
        registration.registerClassFunction("Entity", "GetType", entityType, "Returns this entity's registered type.");
        registration.registerClassFunction("Entity", "GetEntityType", entityType, "Returns this entity's registered type.");
        registration.registerClassFunction("Entity", "GetName",
            entityMethod(registration, entity -> LuaValue.valueOf(entity.getName().getString())), "Returns this entity's current name.");
        registration.registerClassFunction("Entity", "GetUUID",
            entityMethod(registration, entity -> LuaValue.valueOf(entity.getUUID().toString())), "Returns this entity's UUID.");
        registration.registerClassFunction("Entity", "GetIsRemoved",
            entityMethod(registration, entity -> LuaValue.valueOf(entity.isRemoved())), "Returns whether this entity has been removed from its world.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetLevel", "Returns the live world containing this entity.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetDimension", "Returns the dimension identifier containing this entity.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetPosition", "Returns this entity's current position.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetPositionX", "Returns this entity's X position.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetPositionY", "Returns this entity's Y position.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetPositionZ", "Returns this entity's Z position.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "SetPosition", "Moves this entity to finite Vector3 or CFrame coordinates.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "Teleport", "Teleports this entity to finite Vector3 or CFrame coordinates.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetVelocity", "Returns this entity's current velocity.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "SetVelocity", "Sets this entity's velocity from a Vector3 with finite components.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetRotation", "Returns this entity's pitch and yaw in degrees.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetRotationRadians", "Returns this entity's pitch and yaw in radians.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "SetRotation", "Sets this entity's pitch and yaw in degrees, either as separate numbers or from a rotation table.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "SetRotationRadians", "Sets this entity's pitch and yaw in radians.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetCFrame", "Returns this entity's position and rotation as a CFrame.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "SetCFrame", "Sets this entity's position and rotation from a CFrame.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetLookVector", "Returns the direction this entity is facing.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "LookAt", "Turns this entity toward a finite Vector3, CFrame, or live entity position.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "DistanceTo", "Returns the distance from this entity to a finite Vector3, CFrame, or live entity position.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetIsAlive", "Returns whether this entity is alive.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetIsOnGround", "Returns whether this entity is on the ground.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetIsInWater", "Returns whether this entity is in water.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetIsOnFire", "Returns whether this entity is on fire.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetIsMoving", "Returns whether this entity is moving.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetIsFalling", "Returns whether this entity is falling.");
        String[][] entityStateMethods = {
            {"GetCustomName", "Returns this entity's custom name, or nil."},
            {"SetCustomName", "Sets or clears this entity's custom name."},
            {"GetCustomNameVisible", "Returns whether the custom name is shown above this entity."},
            {"SetCustomNameVisible", "Shows or hides this entity's custom name."},
            {"GetGlowing", "Returns whether this entity has the glowing outline."},
            {"SetGlowing", "Enables or disables this entity's glowing outline."},
            {"GetNoGravity", "Returns whether gravity is disabled for this entity."},
            {"SetNoGravity", "Enables or disables gravity for this entity."},
            {"GetInvulnerable", "Returns whether this entity is invulnerable."},
            {"SetInvulnerable", "Enables or disables this entity's invulnerability."},
            {"GetSilent", "Returns whether this entity is silent."},
            {"SetSilent", "Enables or disables this entity's silent state."},
            {"GetFireTicks", "Returns the remaining fire duration in ticks."},
            {"SetFireTicks", "Sets the remaining fire duration using a whole number of ticks; negative values clear the fire duration."},
            {"GetEyePosition", "Returns this entity's eye position as a Vector3."},
            {"GetBoundingBox", "Returns the minimum and maximum corners of this entity's bounds."},
            {"GetWidth", "Returns this entity's bounding-box width."},
            {"GetHeight", "Returns this entity's bounding-box height."},
            {"GetEyeHeight", "Returns this entity's eye height above its position."},
            {"GetVehicle", "Returns the entity this entity is riding, or nil."},
            {"GetRootVehicle", "Returns the topmost vehicle in this entity's riding chain."},
            {"GetPassengers", "Returns the entities riding this entity."},
            {"StartRiding", "Attempts to mount this entity onto another entity."},
            {"StopRiding", "Dismounts this entity from its current vehicle."},
            {"GetIsPassenger", "Returns whether this entity is riding another entity."},
            {"GetIsVehicle", "Returns whether this entity has passengers."},
            {"GetIsInLava", "Returns whether this entity is in lava."},
            {"GetIsUnderWater", "Returns whether this entity is fully underwater."},
            {"GetFallDistance", "Returns this entity's current fall distance."},
            {"SetFallDistance", "Sets this entity's fall distance from a finite number; negative values are clamped to zero."},
            {"GetTags", "Returns this entity's saved MLua tags."},
            {"HasTag", "Returns whether this entity has the given saved MLua tag."},
            {"AddTag", "Adds a persistent MLua tag to this entity."},
            {"RemoveTag", "Removes a persistent MLua tag from this entity."}
        };
        for (String[] method : entityStateMethods) {
            MLuaClassMethods.registerInstanceMethod(registration, "Entity", method[0], method[1]);
        }
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "Kill", "Kills this entity.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "Remove", "Removes this entity from its world.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetPersistentData",
            "Returns this entity's persistent MLua data table.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetSyncedData",
            "Returns this entity's replicated MLua data table.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetNbt",
            "Returns this entity's persistent data table or reads one key from it.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "SetNbt",
            "Writes entity persistent data or replaces the persistent data table.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "getNbt",
            "Compatibility alias for GetNbt.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "setNbt",
            "Compatibility alias for SetNbt.");
        String[][] entityAnimationMethods = {
            {"LoadAnimation", "Loads and starts an animation on this entity."},
            {"ResetAnimation", "Stops animations and resets this entity's model pose."},
            {"LoadModel", "Loads and starts a model animation."},
            {"ResetModel", "Stops animations and resets this entity's model pose."},
            {"PlayAnimation", "Plays a named animation track."},
            {"StopAnimation", "Stops a named animation track."},
            {"GetPlayingAnimationTracks", "Returns the active animation tracks."},
            {"StopAllAnimations", "Stops all animation tracks on this entity."},
            {"SetBonePose", "Sets the pose of a named model bone."},
            {"GetBonePose", "Returns the pose of a named model bone."},
            {"GetBonePoses", "Returns the current pose for every model bone."},
            {"ClearBonePose", "Clears one bone pose or all bone poses."}
        };
        for (String[] method : entityAnimationMethods) {
            MLuaClassMethods.registerInstanceMethod(registration, "Entity", method[0], method[1]);
        }
        registerEntityProperties(registration);

        registration.registerClass("LivingEntity", "Entity",
            "A living entity with health, damage, equipment, and attributes.");
        for (String method : List.of("GetHealth", "SetHealth", "GetMaxHealth", "GetArmor", "Damage", "Heal",
                "GetAttribute", "GetAttributeInstance", "SetAttribute", "GetEquipment", "SetEquipment", "GetIsJumping", "GetIsSwimming",
                "GetAbsorption", "SetAbsorption", "GiveEffect", "RemoveEffect", "HasEffect", "GetEffects", "ClearEffects")) {
            String description = switch (method) {
                case "GetHealth" -> "Returns this entity's current health.";
                case "SetHealth" -> "Sets this entity's health to a finite, non-negative value.";
                case "GetMaxHealth" -> "Returns this entity's maximum health.";
                case "GetArmor" -> "Returns this entity's armor value.";
                case "GetIsJumping" -> "Returns whether this entity is moving upward while airborne.";
                case "GetIsSwimming" -> "Returns whether this entity is swimming.";
                case "GetAbsorption" -> "Returns this entity's current absorption health.";
                case "SetAbsorption" -> "Sets this entity's absorption health to a finite, non-negative value.";
                case "GiveEffect" -> "Applies a potion effect to this living entity.";
                case "RemoveEffect" -> "Removes a potion effect from this living entity.";
                case "HasEffect" -> "Checks whether this living entity has a potion effect.";
                case "GetEffects" -> "Returns this living entity's active effects.";
                case "ClearEffects" -> "Removes all potion effects from this living entity.";
                case "Damage" -> "Applies server-side damage and returns whether Minecraft accepted it; accepts a registered damage source and attacker.";
                case "Heal" -> "Restores health through Minecraft's server-side healing path.";
                case "GetAttribute" -> "Returns the value of a registered living entity attribute.";
                case "GetAttributeInstance" -> "Returns a live AttributeInstance for a registered attribute.";
                case "SetAttribute" -> "Sets a registered living entity attribute to a finite base value.";
                case "GetEquipment" -> "Returns the item equipped in the given equipment slot.";
                default -> "Sets the item equipped in the given equipment slot.";
            };
            MLuaClassMethods.registerInstanceMethod(registration, "LivingEntity", method, description);
        }
        MLuaClassMethods.registerInstanceProperty(registration, "LivingEntity", "Health", "GetHealth", "SetHealth",
            "number", "shared:Shared", "This entity's current health.");
        MLuaClassMethods.registerInstanceProperty(registration, "LivingEntity", "MaxHealth", "GetMaxHealth", null,
            "number", "shared:Shared", "This entity's maximum health.");
        MLuaClassMethods.registerInstanceProperty(registration, "LivingEntity", "Armor", "GetArmor", null,
            "number", "shared:Shared", "This entity's armor value.");
        MLuaClassMethods.registerInstanceProperty(registration, "LivingEntity", "Absorption", "GetAbsorption", "SetAbsorption",
            "number", "shared:Shared", "This entity's current absorption health.");
        MLuaClassMethods.registerInstanceProperty(registration, "LivingEntity", "IsJumping", "GetIsJumping", null,
            "boolean", "shared:Shared", "Whether this entity is moving upward while airborne.");
        MLuaClassMethods.registerInstanceProperty(registration, "LivingEntity", "IsSwimming", "GetIsSwimming", null,
            "boolean", "shared:Shared", "Whether this entity is swimming.");
        MLuaClassMethods.registerInstanceProperty(registration, "LivingEntity", "ActiveEffects", "GetEffects", null,
            "table[]", "shared:Shared", "The potion effects currently applied to this entity.");

        registration.registerClass("Mob", "LivingEntity",
            "A Minecraft mob with AI targeting, visibility checks, and persistence controls.");
        MLuaClassMethods.registerInstanceMethod(registration, "Mob", "NavigateTo", "Starts native mob navigation toward a Vector3 or CFrame at the given speed.");
        MLuaClassMethods.registerInstanceMethod(registration, "Mob", "StopNavigation", "Stops this mob's current navigation path.");
        MLuaClassMethods.registerInstanceMethod(registration, "Mob", "IsNavigationDone", "Returns whether this mob has finished or lost its navigation path.");
        MLuaClassMethods.registerInstanceProperty(registration, "Mob", "NavigationDone", "IsNavigationDone", null,
            "boolean", "server:Server", "Whether this mob has finished or lost its navigation path.");
        MLuaClassMethods.registerInstanceMethod(registration, "Mob", "GetTarget", "Returns the living entity this mob currently targets, or nil.");
        MLuaClassMethods.registerInstanceMethod(registration, "Mob", "SetTarget", "Sets or clears this mob's AI target.");
        MLuaClassMethods.registerInstanceMethod(registration, "Mob", "CanSee", "Returns whether this mob has line of sight to an entity.");
        MLuaClassMethods.registerInstanceMethod(registration, "Mob", "CanAttack", "Returns whether Minecraft allows this mob to attack a living entity.");
        MLuaClassMethods.registerInstanceMethod(registration, "Mob", "GetPersistenceRequired", "Returns whether this mob is marked to persist without despawning.");
        MLuaClassMethods.registerInstanceMethod(registration, "Mob", "SetPersistenceRequired", "Marks this mob to persist without despawning.");
        MLuaClassMethods.registerInstanceProperty(registration, "Mob", "Target", "GetTarget", "SetTarget",
            "LivingEntity|nil", "server:Server", "This mob's current AI target.");
        MLuaClassMethods.registerInstanceProperty(registration, "Mob", "PersistenceRequired", "GetPersistenceRequired", null,
            "boolean", "shared:Shared", "Whether this mob is marked to persist without despawning.");

        LuaTable playerClass = registration.registerClass("Player", "LivingEntity",
            "A live player instance with player identity, inventory, messaging, and player controls.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "SendMessage",
            "Sends a chat message to this player. On the client, displays the message locally.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "GetDisplayName",
            "Returns this player's display name.");
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "DisplayName", "GetDisplayName", null,
            "string", "shared:Shared", "This player's display name.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "SendActionBar",
            "Sends an action bar message to this player.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "SendTitle",
            "Displays a title to this player.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "GetGameMode",
            "Returns this player's current game mode.");
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "GameMode", "GetGameMode", null,
            "GameMode", "shared:Shared", "This player's current game mode.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "SetGameMode",
            "Changes this player's game mode. This method is server-side only.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "GetInventory",
            "Returns this player's live Inventory instance.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "GetEnderChest",
            "Returns this player's live ender chest as a Container instance.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "GetOpenContainer",
            "Returns the live Container instance for the menu this player currently has open.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "GetWorld",
            "Returns the live Level instance containing this player.");
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "Inventory", "GetInventory", null,
            "Inventory", "shared:Shared", "Returns this player's live Inventory instance.");
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "EnderChest", "GetEnderChest", null,
            "Container", "shared:Shared", "Returns this player's live ender chest as a Container instance.");
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "OpenContainer", "GetOpenContainer", null,
            "Container", "shared:Shared", "Returns the live Container instance for the menu this player currently has open.");
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "World", "GetWorld", null,
            "Level", "shared:Shared", "Returns the live Level instance containing this player.");
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "IsLocalPlayer", "GetIsLocalPlayer", null,
            "boolean", "client:Client", "Whether this Player instance represents the local client player.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "GetFood",
            "Returns this player's food level.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "SetFood",
            "Sets this player's food level to a whole number from 0 to 20.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "GetFoodLevel",
            "Returns this player's food level.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "SetFoodLevel",
            "Sets this player's food level to a whole number from 0 to 20.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "GetSaturation",
            "Returns this player's food saturation.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "SetSaturation",
            "Sets this player's food saturation to a finite, non-negative value.");
        MLuaClassMethods.registerInstanceMethod(registration, "Player", "CauseFoodExhaustion",
            "Adds food exhaustion to this player; server-side only for authoritative changes.");
        for (String method : List.of("GetDisplayName", "SendMessage", "SendTitle", "SendActionBar",
                "GetEyeCFrame", "GetCameraCFrame", "GiveItem", "ClearInventory", "GiveEffect",
                "RemoveEffect", "HasEffect", "ClearEffects", "PlaySound", "GetNearbyEntities",
                "GetNearbyPlayers", "GetPersistentData", "GetSyncedData",
                "SetData", "GetData", "RemoveData", "HasPermission", "IsOperator")) {
            String description = switch (method) {
                case "GetDisplayName" -> "Returns this player's display name.";
                case "SendMessage" -> "Sends a chat message to this player.";
                case "SendTitle" -> "Sends a title to this player's screen.";
                case "SendActionBar" -> "Sends an action bar message to this player.";
                case "GetEyeCFrame" -> "Returns the player's eye position and view rotation.";
                case "GetCameraCFrame" -> "Returns the local player's camera transform; client-side only.";
                case "GiveItem" -> "Gives an item to this player.";
                case "ClearInventory" -> "Clears this player's inventory.";
                case "GiveEffect" -> "Applies a potion effect to this player.";
                case "RemoveEffect" -> "Removes a potion effect from this player.";
                case "HasEffect" -> "Checks whether this player has a potion effect.";
                case "ClearEffects" -> "Removes all potion effects from this player.";
                case "PlaySound" -> "Plays a sound for this player.";
                case "GetNearbyEntities" -> "Returns nearby entities.";
                case "GetNearbyPlayers" -> "Returns nearby players.";
                case "GetPersistentData" -> "Returns this player's persistent data.";
                case "GetSyncedData" -> "Returns this player's synchronized data.";
                case "SetData" -> "Sets a value in this player's persistent data.";
                case "GetData" -> "Reads a value from this player's persistent data.";
                case "RemoveData" -> "Removes a value from this player's persistent data.";
                case "HasPermission" -> "Checks whether this player has a permission node.";
                default -> "Checks whether this player is a server operator.";
            };
            MLuaClassMethods.registerInstanceMethod(registration, "Player", method, description);
        }
        String[][] inventoryAndStateMethods = {
            {"GetExperienceLevel", "Returns this player's current experience level."},
            {"GetExperienceProgress", "Returns this player's progress toward the next experience level."},
            {"GiveExperience", "Adds experience points to this player; server-side only."},
            {"GiveExperienceLevels", "Adds experience levels to this player; server-side only."},
            {"IsOnline", "Whether this player is connected and alive."},
            {"IsCreative", "Whether this player is in Creative mode."},
            {"IsSpectator", "Whether this player is in Spectator mode."},
            {"IsSleeping", "Whether this player is sleeping."},
            {"IsCrouching", "Whether this player is crouching."},
            {"IsSprinting", "Whether this player is sprinting."},
            {"IsSwimming", "Whether this player is swimming."},
            {"GetBlockPosition", "Returns this player's block-aligned position."},
            {"GetLookVector", "Returns the direction this player is looking."},
            {"GetSelectedSlot", "Returns the selected hotbar slot using a one-based integer index."},
            {"SetSelectedSlot", "Selects a hotbar slot using a whole-number index from 1 to 9."},
            {"GetHeldItem", "Returns the item in this player's main hand, or nil."},
            {"GetOffhandItem", "Returns the item in this player's offhand, or nil."},
            {"GetViewMode", "Returns the local player's view mode; client-side only."},
            {"SetViewMode", "Sets the local player's view mode; client-side only."},
            {"GetSlot", "Returns a live inventory slot using a one-based whole-number index."},
            {"SetSlot", "Sets or clears an inventory slot using a one-based whole-number index."},
            {"SetHotbarSlot", "Sets or clears a hotbar slot using a one-based index from 1 to 9."},
            {"SetArmorSlot", "Sets or clears an armor slot using a one-based index from 1 to 4."},
            {"SetOffhandSlot", "Sets or clears this player's offhand item."},
            {"GetInventorySize", "Returns this player's inventory slot count."},
            {"GetHotbar", "Returns live Slot instances for the nine hotbar positions."},
            {"GetArmorContents", "Returns the same live Slot instances exposed by Inventory for the four armor positions."},
            {"GetOffhandSlot", "Returns the same live Slot instance exposed by Inventory for the offhand."},
            {"GetOpenContainerTitle", "Returns the title of this player's current menu."},
            {"GetOpenContainerSize", "Returns the slot count of this player's current menu."},
            {"SetInventory", "Replaces this player's inventory contents from a one-based item table; server-side only."},
            {"GetIsLocalPlayer", "Whether this Player instance represents the local client player."},
            {"GetEffects", "Returns this player's active effects."}
        };
        for (String[] method : inventoryAndStateMethods) {
            MLuaClassMethods.registerInstanceMethod(registration, "Player", method[0], method[1]);
        }
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "FoodLevel", "GetFoodLevel", "SetFoodLevel",
            "number", "shared:Shared", "This player's food level.");
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "Food", "GetFood", "SetFood",
            "number", "shared:Shared", "Compatibility alias for FoodLevel.");
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "Saturation", "GetSaturation", "SetSaturation",
            "number", "shared:Shared", "This player's food saturation.");
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "ViewMode", "GetViewMode", "SetViewMode",
            "ViewMode", "client:Client", "The local player's view mode; client-side only.");
        MLuaClassMethods.registerInstanceProperty(registration, "Player", "SelectedSlot", "GetSelectedSlot", null,
            "number", "shared:Shared", "The selected hotbar slot using a one-based index.");
    }

    private static void registerEntityBoundsClass(MLuaRegistration registration) {
        registration.registerClass("EntityBounds", registration.requireInstanceBaseClassName(),
            "An axis-aligned entity bounding box with Vector3 corners and spatial queries.");
        registration.registerClassFunction("EntityBounds", "new", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue minimum = args.arg(1);
                LuaValue maximum = args.arg(2);
                if (!dev.mlua.math.MLuaVector.isVector3(minimum)
                        || !dev.mlua.math.MLuaVector.isVector3(maximum)) {
                    throw new LuaError("[MLua] EntityBounds.new expects minimum and maximum Vector3 values.");
                }
                LuaTable min = minimum.checktable();
                LuaTable max = maximum.checktable();
                return entityBounds(registration,
                    min.get("X").todouble(), min.get("Y").todouble(), min.get("Z").todouble(),
                    max.get("X").todouble(), max.get("Y").todouble(), max.get("Z").todouble());
            }
        }, "Creates bounds from ordered minimum and maximum Vector3 corners.");
        for (String name : List.of("Minimum", "Maximum")) {
            String getter = "Get" + name;
            registration.registerClassFunction("EntityBounds", getter, new org.luaj.vm2.lib.OneArgFunction() {
                @Override public LuaValue call(LuaValue self) { return self.checktable().get(name); }
            }, "Returns the " + name.toLowerCase() + " corner as a Vector3.");
            MLuaClassMethods.registerInstanceProperty(registration, "EntityBounds", name, getter, null,
                "Vector3", "shared:Shared", "The " + name.toLowerCase() + " corner of the bounds.");
        }
        registration.registerClassFunction("EntityBounds", "GetCenter", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaTable self = args.checktable(1);
                LuaTable min = self.get("Minimum").checktable();
                LuaTable max = self.get("Maximum").checktable();
                return dev.mlua.math.MLuaVector.vector3(
                    (min.get("X").todouble() + max.get("X").todouble()) * 0.5,
                    (min.get("Y").todouble() + max.get("Y").todouble()) * 0.5,
                    (min.get("Z").todouble() + max.get("Z").todouble()) * 0.5);
            }
        }, "Returns the center point as a Vector3.");
        MLuaClassMethods.registerInstanceProperty(registration, "EntityBounds", "Center", "GetCenter", null,
            "Vector3", "shared:Shared", "The center point of the bounds.");
        registration.registerClassFunction("EntityBounds", "GetSize", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaTable self = args.checktable(1);
                LuaTable min = self.get("Minimum").checktable();
                LuaTable max = self.get("Maximum").checktable();
                return dev.mlua.math.MLuaVector.vector3(
                    max.get("X").todouble() - min.get("X").todouble(),
                    max.get("Y").todouble() - min.get("Y").todouble(),
                    max.get("Z").todouble() - min.get("Z").todouble());
            }
        }, "Returns the box dimensions as a Vector3.");
        MLuaClassMethods.registerInstanceProperty(registration, "EntityBounds", "Size", "GetSize", null,
            "Vector3", "shared:Shared", "The dimensions of the bounds.");
        registration.registerClassFunction("EntityBounds", "GetVolume", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaTable self = args.checktable(1);
                LuaTable min = self.get("Minimum").checktable();
                LuaTable max = self.get("Maximum").checktable();
                return LuaValue.valueOf((max.get("X").todouble() - min.get("X").todouble())
                    * (max.get("Y").todouble() - min.get("Y").todouble())
                    * (max.get("Z").todouble() - min.get("Z").todouble()));
            }
        }, "Returns the volume of this box in cubic blocks.");
        MLuaClassMethods.registerInstanceProperty(registration, "EntityBounds", "Volume", "GetVolume", null,
            "number", "shared:Shared", "The volume of the bounds in cubic blocks.");
        registration.registerClassFunction("EntityBounds", "Contains", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue point = args.arg(2);
                if (!dev.mlua.math.MLuaVector.isVector3(point)) {
                    throw new LuaError("[MLua] EntityBounds:Contains expects a Vector3.");
                }
                LuaTable self = args.checktable(1);
                LuaTable min = self.get("Minimum").checktable();
                LuaTable max = self.get("Maximum").checktable();
                LuaTable position = point.checktable();
                return LuaValue.valueOf(position.get("X").todouble() >= min.get("X").todouble()
                    && position.get("X").todouble() <= max.get("X").todouble()
                    && position.get("Y").todouble() >= min.get("Y").todouble()
                    && position.get("Y").todouble() <= max.get("Y").todouble()
                    && position.get("Z").todouble() >= min.get("Z").todouble()
                    && position.get("Z").todouble() <= max.get("Z").todouble());
            }
        }, "Returns whether a Vector3 point lies inside or on the box.");
        registration.registerClassFunction("EntityBounds", "Intersects", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue other = args.arg(2);
                if (!other.istable()) {
                    throw new LuaError("[MLua] EntityBounds:Intersects expects an EntityBounds instance.");
                }
                LuaTable otherBounds = other.checktable();
                LuaValue type = otherBounds.get("__type");
                if (!type.isstring() || !type.checkjstring().equals("EntityBounds")) {
                    throw new LuaError("[MLua] EntityBounds:Intersects expects an EntityBounds instance.");
                }
                LuaTable self = args.checktable(1);
                LuaTable firstMin = self.get("Minimum").checktable();
                LuaTable firstMax = self.get("Maximum").checktable();
                LuaTable secondMin = otherBounds.get("Minimum").checktable();
                LuaTable secondMax = otherBounds.get("Maximum").checktable();
                return LuaValue.valueOf(firstMax.get("X").todouble() >= secondMin.get("X").todouble()
                    && firstMin.get("X").todouble() <= secondMax.get("X").todouble()
                    && firstMax.get("Y").todouble() >= secondMin.get("Y").todouble()
                    && firstMin.get("Y").todouble() <= secondMax.get("Y").todouble()
                    && firstMax.get("Z").todouble() >= secondMin.get("Z").todouble()
                    && firstMin.get("Z").todouble() <= secondMax.get("Z").todouble());
            }
        }, "Returns whether this box overlaps another EntityBounds instance.");
        registration.registerClassFunction("EntityBounds", "Inflate", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                double amount = args.checkdouble(2);
                if (!Double.isFinite(amount) || amount < 0) {
                    throw new LuaError("[MLua] EntityBounds:Inflate expects a finite, non-negative amount.");
                }
                LuaTable self = args.checktable(1);
                LuaTable min = self.get("Minimum").checktable();
                LuaTable max = self.get("Maximum").checktable();
                return entityBounds(registration,
                    min.get("X").todouble() - amount, min.get("Y").todouble() - amount, min.get("Z").todouble() - amount,
                    max.get("X").todouble() + amount, max.get("Y").todouble() + amount, max.get("Z").todouble() + amount);
            }
        }, "Returns a new bounds instance expanded equally on each side.");
        registration.registerClassFunction("EntityBounds", "Deflate", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                double amount = args.checkdouble(2);
                if (!Double.isFinite(amount) || amount < 0) {
                    throw new LuaError("[MLua] EntityBounds:Deflate expects a finite, non-negative amount.");
                }
                LuaTable self = args.checktable(1);
                LuaTable min = self.get("Minimum").checktable();
                LuaTable max = self.get("Maximum").checktable();
                return entityBounds(registration,
                    min.get("X").todouble() + amount, min.get("Y").todouble() + amount, min.get("Z").todouble() + amount,
                    max.get("X").todouble() - amount, max.get("Y").todouble() - amount, max.get("Z").todouble() - amount);
            }
        }, "Returns a new bounds instance contracted equally on each side.");
        registration.registerClassFunction("EntityBounds", "Move", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue offset = args.arg(2);
                if (!dev.mlua.math.MLuaVector.isVector3(offset)) {
                    throw new LuaError("[MLua] EntityBounds:Move expects a Vector3 offset.");
                }
                LuaTable delta = offset.checktable();
                LuaTable self = args.checktable(1);
                LuaTable min = self.get("Minimum").checktable();
                LuaTable max = self.get("Maximum").checktable();
                double x = delta.get("X").todouble();
                double y = delta.get("Y").todouble();
                double z = delta.get("Z").todouble();
                return entityBounds(registration,
                    min.get("X").todouble() + x, min.get("Y").todouble() + y, min.get("Z").todouble() + z,
                    max.get("X").todouble() + x, max.get("Y").todouble() + y, max.get("Z").todouble() + z);
            }
        }, "Returns a new bounds instance translated by a Vector3 offset.");
        registration.registerClassFunction("EntityBounds", "Union", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue other = args.arg(2);
                if (!other.istable()) throw new LuaError("[MLua] EntityBounds:Union expects an EntityBounds instance.");
                LuaTable otherBounds = other.checktable();
                LuaValue type = otherBounds.get("__type");
                if (!type.isstring() || !type.checkjstring().equals("EntityBounds")) {
                    throw new LuaError("[MLua] EntityBounds:Union expects an EntityBounds instance.");
                }
                LuaTable self = args.checktable(1);
                LuaTable min = self.get("Minimum").checktable();
                LuaTable max = self.get("Maximum").checktable();
                LuaTable otherMin = otherBounds.get("Minimum").checktable();
                LuaTable otherMax = otherBounds.get("Maximum").checktable();
                return entityBounds(registration,
                    Math.min(min.get("X").todouble(), otherMin.get("X").todouble()),
                    Math.min(min.get("Y").todouble(), otherMin.get("Y").todouble()),
                    Math.min(min.get("Z").todouble(), otherMin.get("Z").todouble()),
                    Math.max(max.get("X").todouble(), otherMax.get("X").todouble()),
                    Math.max(max.get("Y").todouble(), otherMax.get("Y").todouble()),
                    Math.max(max.get("Z").todouble(), otherMax.get("Z").todouble()));
            }
        }, "Returns a new bounds instance enclosing both boxes.");
        registration.registerClassFunction("EntityBounds", "DistanceTo", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue point = args.arg(2);
                if (!dev.mlua.math.MLuaVector.isVector3(point)) {
                    throw new LuaError("[MLua] EntityBounds:DistanceTo expects a Vector3.");
                }
                LuaTable position = point.checktable();
                LuaTable self = args.checktable(1);
                LuaTable min = self.get("Minimum").checktable();
                LuaTable max = self.get("Maximum").checktable();
                double dx = Math.max(Math.max(min.get("X").todouble() - position.get("X").todouble(), 0),
                    position.get("X").todouble() - max.get("X").todouble());
                double dy = Math.max(Math.max(min.get("Y").todouble() - position.get("Y").todouble(), 0),
                    position.get("Y").todouble() - max.get("Y").todouble());
                double dz = Math.max(Math.max(min.get("Z").todouble() - position.get("Z").todouble(), 0),
                    position.get("Z").todouble() - max.get("Z").todouble());
                return LuaValue.valueOf(Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
        }, "Returns the shortest distance from a Vector3 point to this box.");
        registration.registerClassFunction("EntityBounds", "ClosestPoint", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue point = args.arg(2);
                if (!dev.mlua.math.MLuaVector.isVector3(point)) {
                    throw new LuaError("[MLua] EntityBounds:ClosestPoint expects a Vector3.");
                }
                LuaTable position = point.checktable();
                LuaTable self = args.checktable(1);
                LuaTable min = self.get("Minimum").checktable();
                LuaTable max = self.get("Maximum").checktable();
                return dev.mlua.math.MLuaVector.vector3(
                    Math.max(min.get("X").todouble(), Math.min(max.get("X").todouble(), position.get("X").todouble())),
                    Math.max(min.get("Y").todouble(), Math.min(max.get("Y").todouble(), position.get("Y").todouble())),
                    Math.max(min.get("Z").todouble(), Math.min(max.get("Z").todouble(), position.get("Z").todouble())));
            }
        }, "Returns the nearest point on or inside this box.");
    }

    private static LuaTable entityBounds(MLuaRegistration registration,
                                         double minX, double minY, double minZ,
                                         double maxX, double maxY, double maxZ) {
        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
                || minX > maxX || minY > maxY || minZ > maxZ) {
            throw new LuaError("[MLua] EntityBounds corners must be finite and ordered from minimum to maximum.");
        }
        LuaTable result = registration.createClassInstance("EntityBounds");
        result.set("Minimum", dev.mlua.math.MLuaVector.vector3(minX, minY, minZ));
        result.set("Maximum", dev.mlua.math.MLuaVector.vector3(maxX, maxY, maxZ));
        return result;
    }

    private static void registerEntityProperties(MLuaRegistration registration) {
        String[][] properties = {
            {"Id", "GetId", null, "number", "shared:Shared", "This entity's runtime ID."},
            {"Type", "GetType", null, "string", "shared:Shared", "This entity's registered type."},
            {"EntityType", "GetEntityType", null, "string", "shared:Shared", "This entity's registered type."},
            {"Name", "GetName", null, "string", "shared:Shared", "This entity's current name."},
            {"UUID", "GetUUID", null, "string", "shared:Shared", "This entity's UUID."},
            {"IsRemoved", "GetIsRemoved", null, "boolean", "shared:Shared", "Whether this entity has been removed from its world."},
            {"Level", "GetLevel", null, "Level", "shared:Shared", "The live world containing this entity."},
            {"Dimension", "GetDimension", null, "string", "shared:Shared", "The dimension identifier containing this entity."},
            {"Nbt", "GetNbt", "SetNbt", "Nbt|table", "server:Server", "The entity's persistent data; assign a table or Nbt value to replace it."},
            {"Position", "GetPosition", "SetPosition", "Vector3", "shared:Shared", "This entity's current position."},
            {"Velocity", "GetVelocity", "SetVelocity", "Vector3", "shared:Shared", "This entity's current velocity."},
            {"Rotation", "GetRotation", "SetRotation", "Vector2", "shared:Shared", "This entity's pitch and yaw in degrees."},
            {"CFrame", "GetCFrame", "SetCFrame", "CFrame", "shared:Shared", "This entity's position and rotation."},
            {"LookVector", "GetLookVector", null, "Vector3", "shared:Shared", "The direction this entity is facing."},
            {"IsAlive", "GetIsAlive", null, "boolean", "shared:Shared", "Whether this entity is alive."},
            {"IsOnGround", "GetIsOnGround", null, "boolean", "shared:Shared", "Whether this entity is on the ground."},
            {"IsInWater", "GetIsInWater", null, "boolean", "shared:Shared", "Whether this entity is in water."},
            {"IsOnFire", "GetIsOnFire", null, "boolean", "shared:Shared", "Whether this entity is on fire."},
            {"IsMoving", "GetIsMoving", null, "boolean", "shared:Shared", "Whether this entity is moving."},
            {"IsFalling", "GetIsFalling", null, "boolean", "shared:Shared", "Whether this entity is falling."},
            {"EyePosition", "GetEyePosition", null, "Vector3", "shared:Shared", "This entity's eye position."},
            {"BoundingBox", "GetBoundingBox", null, "EntityBounds", "shared:Shared", "This entity's axis-aligned bounds."},
            {"Width", "GetWidth", null, "number", "shared:Shared", "This entity's bounding-box width."},
            {"Height", "GetHeight", null, "number", "shared:Shared", "This entity's bounding-box height."},
            {"EyeHeight", "GetEyeHeight", null, "number", "shared:Shared", "This entity's eye height above its position."},
            {"Vehicle", "GetVehicle", null, "Entity|nil", "shared:Shared", "The entity this entity is riding, or nil."},
            {"RootVehicle", "GetRootVehicle", null, "Entity", "shared:Shared", "The topmost vehicle in this entity's riding chain."},
            {"Passengers", "GetPassengers", null, "Entity[]", "shared:Shared", "Entities currently riding this entity."},
            {"IsPassenger", "GetIsPassenger", null, "boolean", "shared:Shared", "Whether this entity is riding another entity."},
            {"IsVehicle", "GetIsVehicle", null, "boolean", "shared:Shared", "Whether this entity has passengers."},
            {"IsInLava", "GetIsInLava", null, "boolean", "shared:Shared", "Whether this entity is in lava."},
            {"IsUnderWater", "GetIsUnderWater", null, "boolean", "shared:Shared", "Whether this entity is fully underwater."},
            {"FallDistance", "GetFallDistance", "SetFallDistance", "number", "shared:Shared", "This entity's current fall distance."},
            {"CustomName", "GetCustomName", "SetCustomName", "string|nil", "shared:Shared", "The entity's custom name."},
            {"CustomNameVisible", "GetCustomNameVisible", "SetCustomNameVisible", "boolean", "shared:Shared", "Whether the custom name is visible."},
            {"Glowing", "GetGlowing", "SetGlowing", "boolean", "shared:Shared", "Whether the entity has a glowing outline."},
            {"NoGravity", "GetNoGravity", "SetNoGravity", "boolean", "shared:Shared", "Whether gravity is disabled."},
            {"Invulnerable", "GetInvulnerable", "SetInvulnerable", "boolean", "shared:Shared", "Whether the entity is invulnerable."},
            {"Silent", "GetSilent", "SetSilent", "boolean", "shared:Shared", "Whether the entity is silent."},
            {"FireTicks", "GetFireTicks", "SetFireTicks", "number", "shared:Shared", "Remaining fire duration in ticks."}
        };
        for (String[] property : properties) {
            MLuaClassMethods.registerInstanceProperty(registration, "Entity", property[0], property[1], property[2],
                property[3], property[4], property[5]);
        }
    }

    private static VarArgFunction entityMethod(MLuaRegistration registration, java.util.function.Function<Entity, LuaValue> getter) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue self = args.arg1();
                if (!registration.isInstanceOf(self, "Entity")) {
                    throw new LuaError("[MLua] Entity method called without an Entity instance.");
                }
                LuaValue raw = self.istable() ? self.checktable().rawget("__entity") : LuaValue.NIL;
                if (!raw.isuserdata(Entity.class)) {
                    throw new LuaError("[MLua] Entity method called without a live entity instance.");
                }
                return getter.apply((Entity) raw.touserdata(Entity.class));
            }
        };
    }

    @Override
    public void contributeGlobals(Globals globals, MLuaContextView context) {
        globals.set("Entity", context.registration().classTable("Entity"));
        globals.set("LivingEntity", context.registration().classTable("LivingEntity"));
        globals.set("Mob", context.registration().classTable("Mob"));
        globals.set("Player", context.registration().classTable("Player"));
    }

    @Override
    public void registerEvents(EventBridge events) {
        events.onPlayerJoin(playerObj -> {
            ServerPlayer player = (ServerPlayer) playerObj;
            var session = MLuaRuntime.sessionForLevel(player.level());
            var registration = MLuaRuntime.registrationForLevel(player.level());
            MLuaPlayersService.PLAYER_ADDED.fireOnSession(session, Side.SERVER, MLuaPlayersService.playerTable(player, registration));
            MLuaRuntime ownerRuntime = registration instanceof MLuaRuntime runtime ? runtime : MLuaRuntime.get();
            ownerRuntime.deliverPendingServerDiagnostics(player);
            MLuaDataManager.syncInitialState(player);
        });

        events.onPlayerLeave(playerObj -> {
            ServerPlayer player = (ServerPlayer) playerObj;
            var session = MLuaRuntime.sessionForLevel(player.level());
            dev.mlua.modules.events.EventsModule.forgetPlayerStepPositions(player.getUUID(), session);
            var registration = MLuaRuntime.registrationForLevel(player.level());
            MLuaPlayersService.PLAYER_REMOVING.fireOnSession(session, Side.SERVER, MLuaPlayersService.playerTable(player, registration));
            MLuaEntityWrapper.removeFromCache(player);
        });

        if (LoaderApi.platform().isClient()) {
            events.onClientDisconnect(() -> {
                MLuaDataManager.clearClientCache();
                MLuaEntityWrapper.clearSideCache(Side.CLIENT);
            });
        }
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:GetInventory()", "Container"));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:GetOpenContainer()", "Container"));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:GetEnderChest()", "Container"));
        docs.register(MLuaDocBuilder.Registration.signature("Players", "Players:GetPlayerByName(name: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Players", "Players:GetPlayerByUUID(uuid: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Players", "Players:Broadcast(message: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SendMessage(message: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SendActionBar(message: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SendTitle(title: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:GiveItem(item: Item)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:GiveEffect(effect: string, duration: number, amplifier: number, showParticles: boolean)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:GiveExperience(amount: number)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:GiveExperienceLevels(levels: number)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetGameMode(mode: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetPosition(position: Vector3|CFrame)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:Teleport(position: Vector3|CFrame)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:LookAt(target: Vector3|CFrame|Entity)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetRotation()", "Vector2"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetRotationRadians()", "Vector2"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:SetRotation(pitch: number, yaw: number)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:SetRotationRadians(rotation: Vector2)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetCFrame()", "CFrame"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:SetCFrame(transform: CFrame)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:PlaySound(sound: string, volume: number, pitch: number)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetData(key: string, value: any)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:GetData(key: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:RemoveData(key: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetSlot(slot: number, item: Item)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetHotbarSlot(slot: number, item: Item)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetArmorSlot(slot: number, item: Item)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetOffhandSlot(item: Item)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetInventory(items: Item[])", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetViewMode(mode: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:HasPermission(level: number)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:DistanceTo(target: Vector3|CFrame|Entity)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:HasEffect(effect: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:RemoveEffect(effect: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetFood(food: number)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetFoodLevel(level: number)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetSaturation(saturation: number)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:CauseFoodExhaustion(amount: number)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetHealth(health: number)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Player", "Player:SetSelectedSlot(slot: number)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Players", "Players:GetLocalPlayer()", "Player?"));
        docs.register(MLuaDocBuilder.Registration.signature("Players", "Players:GetPlayers()", "Player[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Players", "Players:GetPlayerByName(name: string)", "Player?"));
        docs.register(MLuaDocBuilder.Registration.signature("Players", "Players:GetPlayerByUUID(uuid: string)", "Player?"));
        docs.register(MLuaDocBuilder.Registration.signature("Players", "Players:GetPlayerById(id: number)", "Player?"));
        docs.register(MLuaDocBuilder.Registration.signature("Players", "Players:GetPlayerCount()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("Players", "Players:GetMaxPlayers()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("Players", "Players:Broadcast(message: string)", "void"));
        docs.register(MLuaDocBuilder.Registration.service("Players", "MLuaPlayersService",
                "Manages player connections and provides player instances.",
                "services/players.html"));
        docs.register(MLuaDocBuilder.Registration.property("Players", "Players.LocalPlayer", "Player",
            "The local player on the client; nil on the server.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.event("Players", "Players.PlayerAdded", "player: Player",
            "Fires when a player joins the game.", "server:Server,client:Client"));
        docs.register(MLuaDocBuilder.Registration.event("Players", "Players.PlayerRemoving", "player: Player",
            "Fires when a player leaves the game.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Players", "Find a player", "Look up a connected player by exact name, then use the live Player instance.",
            "local Players = Minecraft:GetService(\"Players\")\n" +
            "local player = Players:GetPlayerByName(\"Alex\")\n" +
            "if player then\n" +
            "    player:SendMessage(\"Welcome!\")\n" +
            "end"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Players", "Script mob navigation", "Mob:NavigateTo uses Minecraft's native path navigation. Call it from server-side entity behavior, then check the path before choosing another destination.",
            "local function updatePatrol(mob, waypoint)\n" +
            "    if mob:IsNavigationDone() then\n" +
            "        mob:NavigateTo(waypoint, 1.1)\n" +
            "    end\n" +
            "end\n\n" +
            "local waypoint = Vector3.new(24, 64, -12)\n" +
            "updatePatrol(scriptedMob, waypoint)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Players", "Apply server-side damage", "LivingEntity:Damage uses Minecraft's damage pipeline and returns true only when the target accepted the hit. Pass an attacker to preserve kill credit and damage attribution.",
            "local accepted = target:Damage(6, \"generic\", attacker)\n" +
            "if accepted then\n" +
            "    attacker:SendMessage(\"Hit confirmed\")\n" +
            "end\n\n" +
            "target:Heal(4)"));
        docs.register(MLuaDocBuilder.Registration.clazz("Object", "LuaTable",
                "Root class in MLua's Lua class hierarchy.",
                "classes/object.html", null));
        docs.register(MLuaDocBuilder.Registration.clazz("Instance", "LuaTable",
                "Base class for live wrappers around Minecraft objects.",
                "classes/instance.html", "Object"));
        docs.register(MLuaDocBuilder.Registration.clazz("Entity", "MLuaEntityWrapper",
                "A live entity instance with position, movement, health, and interaction APIs.",
                "classes/entity.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.clazz("LivingEntity", "MLuaEntityWrapper",
                "A living entity with health, damage, equipment, and attributes.",
                "classes/living-entity.html", "Entity"));
        docs.register(MLuaDocBuilder.Registration.clazz("Mob", "MLuaEntityWrapper",
                "A Minecraft mob with AI targeting, visibility checks, and persistence controls.",
                "classes/mob.html", "LivingEntity"));
        docs.register(MLuaDocBuilder.Registration.property("Mob", "mob.Target", "LivingEntity|nil", "This mob's current AI target. Readable and writable.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.property("Mob", "mob.PersistenceRequired", "boolean", "Whether this mob is marked to persist without despawning.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Name", "string", "The entity's current name.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.UUID", "string", "The entity's UUID.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Nbt", "Nbt|table", "The entity's persistent data. Server-side only; assigning a table or Nbt value replaces it.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Position", "Vector3", "The entity's current position. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Velocity", "Vector3", "The entity's current velocity. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Rotation", "Vector2", "The entity's pitch and yaw. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.IsAlive", "boolean", "Whether the entity is alive.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.IsOnGround", "boolean", "Whether the entity is on the ground.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.IsInWater", "boolean", "Whether the entity is in water.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.IsOnFire", "boolean", "Whether the entity is on fire.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.CustomName", "string|nil", "The entity's custom name. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.CustomNameVisible", "boolean", "Whether the custom name is shown above this entity. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Glowing", "boolean", "Whether this entity has a glowing outline. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.NoGravity", "boolean", "Whether gravity is disabled. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Invulnerable", "boolean", "Whether this entity is invulnerable. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Silent", "boolean", "Whether this entity is silent. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.FireTicks", "number", "Remaining fire duration in ticks. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.EyePosition", "Vector3", "This entity's eye position.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Width", "number", "This entity's bounding-box width.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Height", "number", "This entity's bounding-box height.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.EyeHeight", "number", "This entity's eye height above its position.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Vehicle", "Entity|nil", "The entity this entity is riding, or nil.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.RootVehicle", "Entity", "The topmost vehicle in this entity's riding chain.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.Passengers", "Entity[]", "Entities currently riding this entity.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.IsPassenger", "boolean", "Whether this entity is riding another entity.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.IsVehicle", "boolean", "Whether this entity has passengers.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.IsInLava", "boolean", "Whether this entity is in lava.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.IsUnderWater", "boolean", "Whether this entity is fully underwater.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "entity.FallDistance", "number", "The entity's current fall distance. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("LivingEntity", "entity.Health", "number", "The entity's current health. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("LivingEntity", "entity.MaxHealth", "number", "The entity's maximum health.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("LivingEntity", "entity.ActiveEffects", "table[]", "Active potion effects with identifier, duration, amplifier, ambient, and visibility fields.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("LivingEntity", "entity.Armor", "number", "The entity's armor value.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("LivingEntity", "entity.Absorption", "number", "The entity's current absorption health. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.clazz("Player", "MLuaPlayerWrapper",
                "A live player instance with player identity, inventory, messaging, and player controls.",
                "classes/player.html", "LivingEntity"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.Name", "string", "The player's username.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.UUID", "string", "The player's UUID.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.Nbt", "Nbt|table", "The player's persistent data. Server-side only; assigning a table or Nbt value replaces it.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.Position", "Vector3", "The player's current position. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.Health", "number", "The player's current health. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.MaxHealth", "number", "The player's maximum health.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.FoodLevel", "number", "The player's current food level. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.Food", "number", "Compatibility alias for player.FoodLevel. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.Saturation", "number", "The player's current food saturation. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.Velocity", "Vector3", "The player's current velocity. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.Rotation", "Vector2", "The player's pitch and yaw. Readable and writable.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.ViewMode", "ViewMode", "The local player's camera perspective. Readable and writable.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.GameMode", "GameMode", "The player's current game mode.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.SelectedSlot", "number", "The selected hotbar slot using a one-based index.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.Armor", "number", "The player's current armor value.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.IsAlive", "boolean", "Whether the player is alive.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.IsOnGround", "boolean", "Whether the player is on the ground.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.IsInWater", "boolean", "Whether the player is in water.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Player", "player.IsOnFire", "boolean", "Whether the player is on fire.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Player", "Use a live player instance", "Player values returned by the Players service stay connected to the live entity.",
            "local Players = Minecraft:GetService(\"Players\")\n" +
            "local player = Players:GetLocalPlayer()\n" +
            "if player then\n" +
            "    print(player.Name, player.Position)\n" +
            "    player:SendMessage(\"Hello!\")\n" +
            "end"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Player", "Charge food exhaustion for a custom action",
            "CauseFoodExhaustion adds to Minecraft's normal exhaustion meter, so custom sprinting, heavy attacks, or tools can share the vanilla hunger system. Call this helper from a server-side action callback.",
            "local function spendStamina(player, exhaustion)\n    player:CauseFoodExhaustion(exhaustion)\nend"));
    }
}
