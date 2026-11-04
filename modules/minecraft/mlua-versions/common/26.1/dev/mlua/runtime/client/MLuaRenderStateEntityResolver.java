package dev.mlua.runtime.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** Resolves the live entity for 26.1 render states, which no longer carry an entity id. */
public final class MLuaRenderStateEntityResolver {
    private record Association(WeakReference<Entity> entity, Float partialTick) { }
    private static final Map<EntityRenderState, Association> ENTITIES_BY_STATE = new WeakHashMap<>();

    private MLuaRenderStateEntityResolver() {}

    public static synchronized void associate(EntityRenderState state, Entity entity, float partialTick) {
        if (state != null && entity != null) {
            ENTITIES_BY_STATE.put(state, new Association(new WeakReference<>(entity),
                Float.isFinite(partialTick) ? Math.max(0.0F, Math.min(1.0F, partialTick)) : null));
        }
    }

    public static Entity findEntity(EntityRenderState state) {
        if (state == null) return null;
        synchronized (MLuaRenderStateEntityResolver.class) {
            Association association = ENTITIES_BY_STATE.get(state);
            Entity entity = association == null ? null : association.entity().get();
            if (matches(state, entity)) return entity;
        }
        if (state instanceof AvatarRenderState avatar && avatar.id != 0) {
            ClientLevel level = Minecraft.getInstance().level;
            Entity entity = level == null ? null : level.getEntity(avatar.id);
            if (matches(state, entity)) return entity;
        }
        // Render states do not provide a reliable generic entity identifier. Guessing by
        // position and type can attach an effect to a different nearby mob, so an
        // unassociated state intentionally has no live entity.
        return null;
    }

    public static int findEntityId(EntityRenderState state) {
        Entity entity = findEntity(state);
        return entity == null ? -1 : entity.getId();
    }

    public static Float findPartialTick(EntityRenderState state) {
        if (state == null) return null;
        synchronized (MLuaRenderStateEntityResolver.class) {
            Association association = ENTITIES_BY_STATE.get(state);
            return association == null ? null : association.partialTick();
        }
    }

    private static boolean matches(EntityRenderState state, Entity entity) {
        ClientLevel level = Minecraft.getInstance().level;
        return entity != null && !entity.isRemoved() && level != null && entity.level() == level
            && (state.entityType == null || entity.getType() == state.entityType);
    }

}
