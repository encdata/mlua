package dev.mlua.modules.players;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Registers the Entity, LivingEntity, and Mob class contracts. */
final class MLuaEntityClassRegistration {
    private MLuaEntityClassRegistration() {}

    static void register(MLuaRegistration registration) {
        registration.registerClass("Entity", registration.requireInstanceBaseClassName(),
            "A live entity instance with position, movement, health, and interaction APIs.");
        MLuaEntityBoundsClassRegistration.register(registration);
        registration.registerClassFunction("Entity", "GetId",
            entityMethod(registration, entity -> LuaValue.valueOf(entity.getId())), "Returns this entity's runtime ID.");
        LuaValue entityType = entityMethod(registration, entity -> {
            var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            return LuaValue.valueOf(key == null ? "unknown" : key.toString());
        });
        registration.registerClassFunction("Entity", "GetEntityType", entityType, "Returns this entity's registered type.");
        registration.registerClassFunction("Entity", "GetName",
            entityMethod(registration, entity -> LuaValue.valueOf(entity.getName().getString())), "Returns this entity's current name.");
        registration.registerClassFunction("Entity", "GetUUID",
            entityMethod(registration, entity -> LuaValue.valueOf(entity.getUUID().toString())), "Returns this entity's UUID.");
        registration.registerClassFunction("Entity", "GetIsRemoved",
            entityMethod(registration, entity -> LuaValue.valueOf(entity.isRemoved())), "Returns whether this entity has been removed from its world.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetLevel", "Returns the live world containing this entity.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetWorld", "Returns the live dimension containing this entity as a Level instance.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetDimension", "Returns the dimension identifier containing this entity.");
        MLuaClassMethods.registerInstanceProperty(registration, "Entity", "World", "GetWorld", null,
            "Level", "shared:Shared", "The live dimension containing this entity.");
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
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "Attack", "On the client, requests a local-player attack against this entity through Minecraft's normal game mode.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "attack", "On the client, requests a local-player attack against this entity through Minecraft's normal game mode.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "DistanceTo", "Returns the distance from this entity to a finite Vector3, CFrame, or live entity position.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetNearbyEntities", "Returns entities in a box expanded around this entity's bounds, optionally matching an EntityQuery filter.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetIsAlive", "Returns whether this entity is alive.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetIsOnGround", "Returns whether this entity is on the ground.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetIsInWater", "Returns whether this entity is in water.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetIsOnFire", "Returns whether this entity is on fire.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetIsMoving", "Returns whether this entity's total velocity exceeds 0.05 blocks per tick.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "GetIsFalling", "Returns whether this entity is airborne and moving downward faster than 0.08 blocks per tick.");
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
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "Kill", "Kills this entity. Server-side only.");
        MLuaClassMethods.registerInstanceMethod(registration, "Entity", "Remove", "Removes this entity from its world. Server-side only.");
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
            {"GetAnimator", "Returns this entity's animation controller for loading and managing animation tracks."},
            {"LoadAnimation", "Creates an animation track. A RenderObject is accepted for compatibility and attaches its model; use AttachModel for that operation."},
            {"AttachModel", "Attaches an owned Mesh or BBModel RenderObject to this entity."},
            {"DetachModel", "Detaches an owned model from this entity and leaves it on the Manual render target."},
            {"ResetAnimation", "Stops this addon's animation tracks on the entity and clears its manual pose overrides."},
            {"LoadModel", "Compatibility alias for LoadAnimation; use AttachModel when attaching a render object."},
            {"ResetModel", "Stops this addon's animation tracks and removes its render models attached to the entity."},
            {"PlayAnimation", "Plays a named animation track."},
            {"StopAnimation", "Stops a named animation track."},
            {"GetPlayingAnimationTracks", "Returns the active animation tracks."},
            {"GetAnimationTracks", "Returns every live animation track, including tracks fading out after Stop."},
            {"GetTracksByPriority", "Returns this entity's live animation tracks at one AnimationPriority."},
            {"StopAllAnimations", "Stops this addon's animation tracks on this entity without stopping other addons' tracks."},
            {"SetBonePose", "Sets a named model bone's local offset in blocks and rotation from a CFrame."},
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
                case "GetIsJumping" -> "Returns whether this entity is airborne and moving upward faster than 0.08 blocks per tick.";
                case "GetIsSwimming" -> "Returns whether this entity is swimming.";
                case "GetAbsorption" -> "Returns this entity's current absorption health.";
                case "SetAbsorption" -> "Sets this entity's absorption health to a finite, non-negative value.";
                case "GiveEffect" -> "Applies a potion effect to this living entity and returns whether Minecraft accepted it. Duration defaults to 600 ticks; amplifier is zero-based and defaults to 0.";
                case "RemoveEffect" -> "Removes a potion effect from this living entity.";
                case "HasEffect" -> "Checks whether this living entity has a potion effect.";
                case "GetEffects" -> "Returns ActivePotionEffect instances for the effects currently applied to this living entity.";
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
            "boolean", "shared:Shared", "Whether this entity is airborne and moving upward faster than 0.08 blocks per tick.");
        MLuaClassMethods.registerInstanceProperty(registration, "LivingEntity", "IsSwimming", "GetIsSwimming", null,
            "boolean", "shared:Shared", "Whether this entity is swimming.");
        MLuaClassMethods.registerInstanceProperty(registration, "LivingEntity", "ActiveEffects", "GetEffects", null,
            "ActivePotionEffect[]", "shared:Shared", "The potion effects currently applied to this entity.");

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

    }

    private static void registerEntityProperties(MLuaRegistration registration) {
        String[][] properties = {
            {"Id", "GetId", null, "number", "shared:Shared", "This entity's runtime ID."},
            {"Type", "GetEntityType", null, "string", "shared:Shared", "This entity's Minecraft entity type identifier."},
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
            {"IsMoving", "GetIsMoving", null, "boolean", "shared:Shared", "Whether this entity's total velocity exceeds 0.05 blocks per tick."},
            {"IsFalling", "GetIsFalling", null, "boolean", "shared:Shared", "Whether this entity is airborne and moving downward faster than 0.08 blocks per tick."},
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

}
