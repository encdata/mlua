package dev.mlua.minecraft.scripted;

import dev.mlua.loaderapi.RegistryHandle;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class MLuaScriptedProjectileType {
    public static final String MOD_ID = "mlua";
    public static final String ENTITY_NAME = "scripted_projectile";

    private static RegistryHandle<EntityType<MLuaScriptedProjectile>> registryHandle;

    private MLuaScriptedProjectileType() {}

    public static EntityType<MLuaScriptedProjectile> createType() {
        Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, ENTITY_NAME);
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
        return EntityType.Builder.<MLuaScriptedProjectile>of(MLuaScriptedProjectile::new, MobCategory.MISC)
            .sized(0.25f, 0.25f)
            .clientTrackingRange(10)
            .updateInterval(2)
            .build(key);
    }

    public static void setRegistryHandle(RegistryHandle<EntityType<MLuaScriptedProjectile>> handle) {
        registryHandle = handle;
    }

    public static EntityType<MLuaScriptedProjectile> get() {
        return registryHandle == null ? null : registryHandle.get();
    }
}
