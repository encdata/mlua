package dev.mlua.modules.combat;

import dev.mlua.modules.combat.Service.MLuaCombatService;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.common.enums.Side;
import dev.mlua.core.classes.MLuaClassMethods;
import java.util.List;
import java.util.Map;

public class CombatModule implements MLuaModule {
    @Override
    public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
        if (side == Side.CLIENT) return;
        MLuaProjectileDefinition.clearRuntime(runtimeSession);
        MLuaDamageSourceRegistry.clearRuntime(runtimeSession);
    }

    @Override
    public void clearOwnedState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, String ownerModId) {
        MLuaProjectileDefinition.clearOwned(runtimeSession, ownerModId);
        MLuaDamageSourceRegistry.clearOwned(runtimeSession, ownerModId);
    }

    @Override
    public String id() { return "combat"; }

    @Override
    public List<String> dependencies() { return List.of("core-classes", "players"); }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        registration.registerClass("Projectile", "Entity",
            "A live scripted projectile with current motion data and runtime controls.");
        MLuaClassMethods.registerInstanceMethod(registration, "Projectile", "Destroy", "Removes this projectile from the world.");
        MLuaClassMethods.registerInstanceMethod(registration, "Projectile", "SetDamage", "Changes this projectile's damage.");
        MLuaClassMethods.registerInstanceMethod(registration, "Projectile", "SetDirection", "Changes this projectile's direction.");
        MLuaClassMethods.registerInstanceMethod(registration, "Projectile", "SetVelocity", "Changes this projectile's velocity and speed.");
        MLuaClassMethods.registerInstanceMethod(registration, "Projectile", "GetDirection", "Returns this projectile's current direction.");
        MLuaClassMethods.registerInstanceMethod(registration, "Projectile", "GetSpeed", "Returns this projectile's configured speed in blocks per tick.");
        MLuaClassMethods.registerInstanceMethod(registration, "Projectile", "GetDamage", "Returns this projectile's current damage.");
        MLuaClassMethods.registerInstanceMethod(registration, "Projectile", "GetDamageSource", "Returns this projectile's registered damage source.");
        MLuaClassMethods.registerInstanceMethod(registration, "Projectile", "GetFireTicks", "Returns the fire duration applied to hit entities.");
        MLuaClassMethods.registerInstanceMethod(registration, "Projectile", "GetKnockback", "Returns the knockback applied to hit entities.");
        MLuaClassMethods.registerInstanceMethod(registration, "Projectile", "GetTick", "Returns the number of server ticks since this projectile was launched.");
        MLuaClassMethods.registerInstanceMethod(registration, "Projectile", "GetOwner", "Returns this projectile's owner, or nil when it has no live owner.");
        MLuaClassMethods.registerInstanceProperty(registration, "Projectile", "Direction", "GetDirection", "SetDirection",
            "Vector3|nil", "server:Server", "This projectile's current direction.");
        MLuaClassMethods.registerInstanceProperty(registration, "Projectile", "Speed", "GetSpeed", null,
            "number", "server:Server", "This projectile's configured speed in blocks per tick.");
        MLuaClassMethods.registerInstanceProperty(registration, "Projectile", "Damage", "GetDamage", "SetDamage",
            "number", "server:Server", "This projectile's current damage.");
        MLuaClassMethods.registerInstanceProperty(registration, "Projectile", "DamageSource", "GetDamageSource", null,
            "string", "server:Server", "This projectile's registered damage source.");
        MLuaClassMethods.registerInstanceProperty(registration, "Projectile", "FireTicks", "GetFireTicks", null,
            "number", "server:Server", "The fire duration applied to hit entities.");
        MLuaClassMethods.registerInstanceProperty(registration, "Projectile", "Knockback", "GetKnockback", null,
            "number", "server:Server", "The knockback applied to hit entities.");
        MLuaClassMethods.registerInstanceProperty(registration, "Projectile", "Tick", "GetTick", null,
            "number", "server:Server", "The number of server ticks since this projectile was launched.");
        MLuaClassMethods.registerInstanceProperty(registration, "Projectile", "Owner", "GetOwner", null,
            "Entity|nil", "server:Server", "This projectile's owner, or nil when it has no live owner.");

        registration.registerClass("ProjectileType", registration.requireInstanceBaseClassName(),
            "A registered projectile definition that can create live projectiles.");
        MLuaClassMethods.registerInstanceMethod(registration, "ProjectileType", "Fire",
            "Fires a projectile using this definition and the supplied owner and launch settings.");
        MLuaProjectileDefinition.registerTypeProperties(registration);
    }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder combat = ServiceEntry.builder("Combat", MLuaCombatService::new)
            .reflective(MLuaCombatService.class);
        return List.of(combat.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.clazz("ProjectileType", "MLuaProjectileDefinition",
            "A registered projectile definition that creates live Projectile instances.",
            "classes/projectile-type.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.method("ProjectileType", "ProjectileType:Fire(config: ProjectileDefinition): Projectile",
            "Fires this projectile type with an owner and optional launch settings.",
            "an owner entity and optional position, direction, damage, and motion overrides",
            "Projectile", "server:Server", null));
        docs.register(MLuaDocBuilder.Registration.property("ProjectileType", "ProjectileType.Name", "string", "The registered projectile type name.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.property("ProjectileType", "ProjectileType.Speed", "number", "The default launch speed in blocks per tick.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.property("ProjectileType", "ProjectileType.Damage", "number", "The default hit damage.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.property("ProjectileType", "ProjectileType.Gravity", "boolean", "Whether gravity affects the projectile by default.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.property("ProjectileType", "ProjectileType.Piercing", "boolean", "Whether the projectile can pass through entities by default.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.property("ProjectileType", "ProjectileType.MaxDistance", "number", "The maximum travel distance in blocks.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.property("ProjectileType", "ProjectileType.Size", "number", "The projectile hitbox size.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.property("ProjectileType", "ProjectileType.DamageSource", "string", "The registered damage source used for hits.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.property("ProjectileType", "ProjectileType.FireTicks", "number", "The default fire duration applied to hit entities.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.property("ProjectileType", "ProjectileType.Knockback", "number", "The default knockback applied to hit entities.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.signature("Combat", "Combat:DamageSourceExists(name: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Combat", "Combat:GetDamageSourceInfo(name: string)", null));

        docs.register(MLuaDocBuilder.Registration.type("ProjectileDefinition", null, "Options and callbacks for a registered scripted projectile."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileDefinition", "Owner", "Entity", "Entity that launches the projectile."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileDefinition", "Position?", "Vector3|CFrame", "Launch position; defaults to the owner's position."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileDefinition", "Direction?", "Vector3", "Launch direction; defaults to the owner's look direction."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileDefinition", "Speed", "number", "Launch speed in blocks per tick."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileDefinition", "Damage?", "number", "Damage applied on an entity hit."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileDefinition", "Gravity?", "boolean", "Whether Minecraft gravity affects the projectile."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileDefinition", "Piercing?", "boolean", "Whether the projectile can continue through entities."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileDefinition", "MaxDistance?", "number", "Maximum travel distance in blocks."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileDefinition", "MaxTicks?", "number", "Maximum lifetime in game ticks."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileDefinition", "Size?", "number", "Projectile hitbox size."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileDefinition", "DamageSource?", "string", "Registered damage source identifier."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileDefinition", "FireTicks?", "number", "How long a hit entity burns, in ticks."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileDefinition", "Knockback?", "number", "Knockback strength applied on an entity hit."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileDefinition", "className?", "string", "Registered Lua class inheriting from Projectile."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileDefinition", "OnHitEntity?", "fun(context: ProjectileHitEntityContext)", "Runs on the server before configured projectile damage is applied."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileDefinition", "OnHitBlock?", "fun(context: ProjectileHitBlockContext)", "Runs on the server when the projectile hits a block."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileDefinition", "OnExpired?", "fun(context: ProjectileContext)", "Runs on the server when the projectile expires."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileDefinition", "OnTick?", "fun(context: ProjectileTickContext)", "Runs once per server tick."));

        docs.register(MLuaDocBuilder.Registration.type("ProjectileContext", null, "Shared callback context for scripted projectile events."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileContext", "Projectile", "Entity", "Live projectile entity instance."));
        docs.register(MLuaDocBuilder.Registration.type("ProjectileTickContext", "ProjectileContext", "Callback context for a projectile tick."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileTickContext", "Tick", "number", "Number of server ticks since the projectile was launched."));
        docs.register(MLuaDocBuilder.Registration.type("ProjectileHitEntityContext", "ProjectileContext", "Callback context for an entity hit."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileHitEntityContext", "Entity", "Entity", "Live entity hit by the projectile."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileHitEntityContext", "Position", "Vector3", "Projectile hit position."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileHitEntityContext", "Damage", "number", "Configured projectile damage."));
        docs.register(MLuaDocBuilder.Registration.type("ProjectileHitBlockContext", "ProjectileContext", "Callback context for a block hit."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileHitBlockContext", "Position", "Vector3", "Projectile hit position."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileHitBlockContext", "BlockX", "number", "Hit block X coordinate."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileHitBlockContext", "BlockY", "number", "Hit block Y coordinate."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileHitBlockContext", "BlockZ", "number", "Hit block Z coordinate."));
        docs.register(MLuaDocBuilder.Registration.field("ProjectileHitBlockContext", "Face", "string", "Block face hit by the projectile."));
    }
}
