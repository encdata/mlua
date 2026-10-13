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

        registration.registerClass("ProjectileType", registration.requireInstanceBaseClassName(),
            "A registered projectile definition that can create live projectiles.");
        MLuaClassMethods.registerInstanceMethod(registration, "ProjectileType", "Fire",
            "Fires a projectile using this definition and the supplied owner and launch settings.");
    }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder combat = ServiceEntry.builder("Combat", MLuaCombatService::new)
            .description("Provides combat queries and registered damage source information.");
        combat.function("GetDamageSourceNames", "Returns the registered damage source identifiers.")
            .function("DamageSourceExists", "Returns whether a damage source identifier is registered.")
            .function("GetDamageSourceInfo", "Returns properties for a registered damage source.");
        return List.of(combat.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.signature("Combat", "Combat:DamageSourceExists(name: string)", null);
        docs.signature("Combat", "Combat:GetDamageSourceInfo(name: string)", null);

        docs.type("ProjectileDefinition", null, "Options and callbacks for a registered scripted projectile.");
        docs.field("ProjectileDefinition", "Speed", "number", "Launch speed in blocks per tick.");
        docs.field("ProjectileDefinition", "Damage?", "number", "Damage applied on an entity hit.");
        docs.field("ProjectileDefinition", "Gravity?", "boolean", "Whether Minecraft gravity affects the projectile.");
        docs.field("ProjectileDefinition", "Piercing?", "boolean", "Whether the projectile can continue through entities.");
        docs.field("ProjectileDefinition", "MaxDistance?", "number", "Maximum travel distance in blocks.");
        docs.field("ProjectileDefinition", "MaxTicks?", "number", "Maximum lifetime in game ticks.");
        docs.field("ProjectileDefinition", "Size?", "number", "Projectile hitbox size.");
        docs.field("ProjectileDefinition", "DamageSource?", "string", "Registered damage source identifier.");
        docs.field("ProjectileDefinition", "FireTicks?", "number", "How long a hit entity burns, in ticks.");
        docs.field("ProjectileDefinition", "Knockback?", "number", "Knockback strength applied on an entity hit.");
        docs.field("ProjectileDefinition", "OnHitEntity?", "fun(context: ProjectileHitEntityContext)", "Runs on the server before configured projectile damage is applied.");
        docs.field("ProjectileDefinition", "OnHitBlock?", "fun(context: ProjectileHitBlockContext)", "Runs on the server when the projectile hits a block.");
        docs.field("ProjectileDefinition", "OnExpired?", "fun(context: ProjectileContext)", "Runs on the server when the projectile expires.");
        docs.field("ProjectileDefinition", "OnTick?", "fun(context: ProjectileTickContext)", "Runs once per server tick.");

        docs.type("ProjectileContext", null, "Shared callback context for scripted projectile events.");
        docs.field("ProjectileContext", "Projectile", "Entity", "Live projectile entity instance.");
        docs.type("ProjectileTickContext", "ProjectileContext", "Callback context for a projectile tick.");
        docs.field("ProjectileTickContext", "Tick", "number", "Number of server ticks since the projectile was launched.");
        docs.type("ProjectileHitEntityContext", "ProjectileContext", "Callback context for an entity hit.");
        docs.field("ProjectileHitEntityContext", "Entity", "Entity", "Live entity hit by the projectile.");
        docs.field("ProjectileHitEntityContext", "Position", "Vector3", "Projectile hit position.");
        docs.field("ProjectileHitEntityContext", "Damage", "number", "Configured projectile damage.");
        docs.type("ProjectileHitBlockContext", "ProjectileContext", "Callback context for a block hit.");
        docs.field("ProjectileHitBlockContext", "Position", "Vector3", "Projectile hit position.");
        docs.field("ProjectileHitBlockContext", "BlockX", "number", "Hit block X coordinate.");
        docs.field("ProjectileHitBlockContext", "BlockY", "number", "Hit block Y coordinate.");
        docs.field("ProjectileHitBlockContext", "BlockZ", "number", "Hit block Z coordinate.");
        docs.field("ProjectileHitBlockContext", "Face", "string", "Block face hit by the projectile.");
    }
}
