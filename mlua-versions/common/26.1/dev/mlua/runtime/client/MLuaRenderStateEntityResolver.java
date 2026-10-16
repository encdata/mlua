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
    private static final Map<EntityRenderState, WeakReference<Entity>> ENTITIES_BY_STATE = new WeakHashMap<>();

    private MLuaRenderStateEntityResolver() {}

    public static synchronized void associate(EntityRenderState state, Entity entity) {
        if (state != null && entity != null) ENTITIES_BY_STATE.put(state, new WeakReference<>(entity));
    }

    public static Entity findEntity(EntityRenderState state) {
        if (state == null) return null;
        synchronized (MLuaRenderStateEntityResolver.class) {
            WeakReference<Entity> reference = ENTITIES_BY_STATE.get(state);
            Entity entity = reference == null ? null : reference.get();
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

    private static boolean matches(EntityRenderState state, Entity entity) {
        ClientLevel level = Minecraft.getInstance().level;
        return entity != null && !entity.isRemoved() && level != null && entity.level() == level
            && (state.entityType == null || entity.getType() == state.entityType);
    }

}
