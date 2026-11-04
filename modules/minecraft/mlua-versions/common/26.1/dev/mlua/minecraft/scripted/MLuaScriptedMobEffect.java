package dev.mlua.minecraft.scripted;

import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public final class MLuaScriptedMobEffect extends MobEffect {
    private final String behaviorKey;
    private final String descriptionId;
    private final Identifier iconTexture;
    private final boolean instant;
    private final int tickInterval;
    private final Map<LivingEntity, Map<String, WeakReference<LuaValue>>> disabledCallbacks = new WeakHashMap<>();

    public MLuaScriptedMobEffect(MobEffectCategory category, int color, String behaviorKey, String descriptionId, Identifier iconTexture, boolean visibleInInventory, boolean visibleInGui, boolean instant, int tickInterval) {
        super(category, color);
        this.behaviorKey = behaviorKey;
        this.descriptionId = descriptionId;
        this.iconTexture = iconTexture;
        this.instant = instant;
        this.tickInterval = Math.max(1, tickInterval);
    }

    @Override public String getDescriptionId() { return descriptionId == null || descriptionId.isBlank() ? super.getDescriptionId() : descriptionId; }
    @Override public boolean isInstantenous() { return instant; }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return !MLuaBehaviorRegistry.getBehavior("effect", this.behaviorKey, "onTick").isnil()
            && duration > 0 && duration % this.tickInterval == 0;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        this.invoke(entity, "onTick", MLuaBehaviorRegistry.getBehavior("effect", this.behaviorKey, "onTick"),
            this.effectContext(entity, amplifier));
        return true;
    }

    @Override
    public void applyInstantenousEffect(ServerLevel level, Entity source, Entity indirectSource,
                                        LivingEntity entity, int amplifier, double health) {
        LuaTable context = this.effectContext(entity, amplifier);
        context.set("Instant", LuaValue.TRUE);
        this.invoke(entity, "onApplied", MLuaBehaviorRegistry.getBehavior("effect", this.behaviorKey, "onApplied"), context);
        this.invokeEnding(entity, MLuaBehaviorRegistry.getBehavior("effect", this.behaviorKey, "onEnding"), context);
    }

    @Override
    public void onEffectAdded(LivingEntity entity, int amplifier) {
        super.onEffectAdded(entity, amplifier);
        if (!entity.level().isClientSide()) {
            this.invoke(entity, "onApplied", MLuaBehaviorRegistry.getBehavior("effect", this.behaviorKey, "onApplied"),
                this.effectContext(entity, amplifier));
        }
    }

    @Override
    public void onMobRemoved(ServerLevel level, LivingEntity entity, int amplifier, Entity.RemovalReason reason) {
        this.invokeEnding(entity, MLuaBehaviorRegistry.getBehavior("effect", this.behaviorKey, "onEnding"),
            this.effectContext(entity, amplifier));
        super.onMobRemoved(level, entity, amplifier, reason);
    }

    private void invoke(LivingEntity entity, String hook, LuaValue callback, LuaTable context) {
        if (callback == null || callback.isnil() || !callback.isfunction() || this.isCallbackDisabled(entity, hook, callback)) return;
        try {
            MLuaBehaviorRegistry.invokeCallback("effect", this.behaviorKey, hook, callback,
                LuaValue.varargsOf(new LuaValue[]{context}));
        } catch (Exception exception) {
            this.disableCallback(entity, hook, callback);
            MLuaLogger.LOGGER.error("MLua mob effect {} callback failed for '{}'; disabled for this entity until replaced",
                hook, this.behaviorKey, exception);
        }
    }

    private synchronized boolean isCallbackDisabled(LivingEntity entity, String hook, LuaValue callback) {
        Map<String, WeakReference<LuaValue>> disabled = this.disabledCallbacks.get(entity);
        if (disabled == null) return false;
        WeakReference<LuaValue> previous = disabled.get(hook);
        if (previous == null) return false;
        if (previous.get() == callback) return true;
        disabled.remove(hook);
        if (disabled.isEmpty()) this.disabledCallbacks.remove(entity);
        return false;
    }

    private synchronized void disableCallback(LivingEntity entity, String hook, LuaValue callback) {
        this.disabledCallbacks.computeIfAbsent(entity, ignored -> new HashMap<>())
            .put(hook, new WeakReference<>(callback));
    }

    private void invokeEnding(LivingEntity entity, LuaValue callback, LuaTable context) {
        try {
            this.invoke(entity, "onEnding", callback, context);
        } finally {
            synchronized (this) {
                this.disabledCallbacks.remove(entity);
            }
        }
    }

    private LuaTable effectContext(LivingEntity entity, int amplifier) {
        LuaTable context = new LuaTable();
        context.set("Entity", MLuaEntityWrapper.entityTable(entity, MLuaRuntime.registrationForLevel(entity.level())));
        context.set("Amplifier", LuaValue.valueOf(amplifier));
        context.set("Effect", LuaValue.valueOf(this.behaviorKey));
        if (this.iconTexture != null) context.set("Icon", LuaValue.valueOf(this.iconTexture.toString()));
        return context;
    }

    public Identifier getIconTexture() { return iconTexture; }
}
