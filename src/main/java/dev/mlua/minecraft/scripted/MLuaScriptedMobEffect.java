package dev.mlua.minecraft.scripted;

import dev.mlua.core.MLuaRuntime;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.mlua.core.MLuaLogger;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import java.lang.reflect.Proxy;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public final class MLuaScriptedMobEffect extends MobEffect {
   private final String behaviorKey;
   private final String descriptionId;
   private final ResourceLocation iconTexture;
   private final boolean visibleInInventory;
   private final boolean visibleInGui;
   private final boolean instant;
   private final int tickInterval;
   private final Map<LivingEntity, Map<String, WeakReference<Object>>> disabledCallbacks = new WeakHashMap<>();

   public MLuaScriptedMobEffect(MobEffectCategory category, int color, String behaviorKey, String descriptionId, ResourceLocation iconTexture, boolean visibleInInventory, boolean visibleInGui, boolean instant, int tickInterval) {
      super(category, color);
      this.behaviorKey = behaviorKey;
      this.descriptionId = descriptionId;
      this.iconTexture = iconTexture;
      this.visibleInInventory = visibleInInventory;
      this.visibleInGui = visibleInGui;
      this.instant = instant;
      this.tickInterval = Math.max(1, tickInterval);
   }

   public String getDescriptionId() {
      return this.descriptionId != null && !this.descriptionId.isBlank() ? this.descriptionId : super.getDescriptionId();
   }

   public boolean isInstantenous() {
      return this.instant;
   }

//--version 1.20.1
   public void applyEffectTick(LivingEntity entity, int amplifier) {
      if (!entity.level().isClientSide) {
         MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("effect", this.behaviorKey);
         this.invoke(entity, "onTick", behaviors, behaviors.callbacks().getOrDefault("onTick", LuaValue.NIL), effectContext(entity, amplifier));
      }
   }
//--endversion
//--version 1.21.1
   public boolean applyEffectTick(LivingEntity entity, int amplifier) {
      if (!entity.level().isClientSide) {
         MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("effect", this.behaviorKey);
         this.invoke(entity, "onTick", behaviors, behaviors.callbacks().getOrDefault("onTick", LuaValue.NIL), effectContext(entity, amplifier));
      }
      return true;
   }
//--endversion

   public void applyInstantenousEffect(Entity source, Entity indirectSource, LivingEntity entity, int amplifier, double health) {
      if (!entity.level().isClientSide) {
         MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("effect", this.behaviorKey);
         LuaTable ctx = effectContext(entity, amplifier);
         ctx.set("Instant", LuaValue.TRUE);
         this.invoke(entity, "onApplied", behaviors, behaviors.callbacks().getOrDefault("onApplied", LuaValue.NIL), ctx);
         this.invokeEnding(entity, behaviors, behaviors.callbacks().getOrDefault("onEnding", LuaValue.NIL), ctx);
      }
   }

//--version 1.20.1
   public boolean isDurationEffectTick(int duration, int amplifier) {
      MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("effect", this.behaviorKey);
      return !behaviors.callbacks().getOrDefault("onTick", LuaValue.NIL).isnil()
         && duration > 0 && duration % this.tickInterval == 0;
   }

   public void addAttributeModifiers(LivingEntity entity, AttributeMap attributes, int amplifier) {
      super.addAttributeModifiers(entity, attributes, amplifier);
      if (!entity.level().isClientSide) {
         MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("effect", this.behaviorKey);
         this.invoke(entity, "onApplied", behaviors, behaviors.callbacks().getOrDefault("onApplied", LuaValue.NIL), effectContext(entity, amplifier));
      }
   }

   public void removeAttributeModifiers(LivingEntity entity, AttributeMap attributes, int amplifier) {
      if (!entity.level().isClientSide) {
         MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("effect", this.behaviorKey);
         this.invokeEnding(entity, behaviors, behaviors.callbacks().getOrDefault("onEnding", LuaValue.NIL), effectContext(entity, amplifier));
      }

      super.removeAttributeModifiers(entity, attributes, amplifier);
   }
//--endversion
//--version 1.21.1
   public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
      MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("effect", this.behaviorKey);
      return !behaviors.callbacks().getOrDefault("onTick", LuaValue.NIL).isnil()
         && duration > 0 && duration % this.tickInterval == 0;
   }

   public void onEffectAdded(LivingEntity entity, int amplifier) {
      super.onEffectAdded(entity, amplifier);
      if (!entity.level().isClientSide) {
         MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("effect", this.behaviorKey);
         this.invoke(entity, "onApplied", behaviors, behaviors.callbacks().getOrDefault("onApplied", LuaValue.NIL), effectContext(entity, amplifier));
      }
   }

   public void onMobRemoved(LivingEntity entity, int amplifier, Entity.RemovalReason reason) {
      if (!entity.level().isClientSide) {
         MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("effect", this.behaviorKey);
         this.invokeEnding(entity, behaviors, behaviors.callbacks().getOrDefault("onEnding", LuaValue.NIL), effectContext(entity, amplifier));
      }
      super.onMobRemoved(entity, amplifier, reason);
   }
//--endversion

   public ResourceLocation getIconTexture() {
      return this.iconTexture;
   }

   @SuppressWarnings({"rawtypes", "unchecked"})
   public void initializeClient(Consumer consumer) {
      try {
         Class<?> extensionsClass = Class.forName("net.minecraftforge.client.extensions.common.IClientMobEffectExtensions");
         ResourceLocation texture = this.iconTexture;
         boolean inventoryVisible = this.visibleInInventory;
         boolean guiVisible = this.visibleInGui;
         Object proxy = Proxy.newProxyInstance(
            MLuaScriptedMobEffect.class.getClassLoader(),
            new Class<?>[]{extensionsClass},
            (instance, method, args) -> {
               if (method.getDeclaringClass() == Object.class) {
                  return switch (method.getName()) {
                     case "toString" -> "MLuaMobEffectExtensions";
                     case "hashCode" -> System.identityHashCode(instance);
                     case "equals" -> instance == (args != null && args.length > 0 ? args[0] : null);
                     default -> method.invoke(instance, args);
                  };
               }

               return switch (method.getName()) {
                  case "isVisibleInInventory" -> inventoryVisible;
                  case "isVisibleInGui" -> guiVisible;
                  case "renderInventoryIcon" -> texture != null && MLuaScriptedMobEffect.blitIcon((GuiGraphics) args[2], texture, (int) args[3] + 6, (int) args[4] + 7, (int) args[5]);
                  case "renderGuiIcon" -> texture != null && MLuaScriptedMobEffect.blitIcon((GuiGraphics) args[2], texture, (int) args[3] + 3, (int) args[4] + 3, ((Float) args[5]).intValue());
                  default -> null;
               };
            }
         );
         consumer.accept(proxy);
      } catch (ClassNotFoundException ignored) {
      }
   }

   private static boolean blitIcon(GuiGraphics graphics, ResourceLocation texture, int x, int y, int blitOffset) {
      RenderSystem.enableBlend();
      graphics.blit(texture, x, y, blitOffset, 0.0F, 0.0F, 18, 18, 18, 18);
      return true;
   }

   private void invoke(LivingEntity entity, String hook, MLuaBehaviorRegistry.Snapshot behaviors,
                       LuaValue callback, LuaTable context) {
      Object definitionIdentity = behaviors == null ? null : behaviors.identity();
      if (callback == null || callback.isnil() || !callback.isfunction()
            || this.isCallbackDisabled(entity, hook, definitionIdentity)) return;
      try {
         MLuaBehaviorRegistry.invokeCallback(behaviors, callback,
            LuaValue.varargsOf(new LuaValue[]{context}));
      } catch (Exception exception) {
         this.disableCallback(entity, hook, definitionIdentity);
         MLuaLogger.LOGGER.error("MLua mob effect {} callback failed for '{}'; disabled for this entity until replaced",
            hook, this.behaviorKey, exception);
      }
   }

   private synchronized boolean isCallbackDisabled(LivingEntity entity, String hook, Object definitionIdentity) {
      Map<String, WeakReference<Object>> disabled = this.disabledCallbacks.get(entity);
      if (disabled == null) return false;
      WeakReference<Object> previous = disabled.get(hook);
      if (previous == null) return false;
      if (previous.get() == definitionIdentity) return true;
      disabled.remove(hook);
      if (disabled.isEmpty()) this.disabledCallbacks.remove(entity);
      return false;
   }

   private synchronized void disableCallback(LivingEntity entity, String hook, Object definitionIdentity) {
      this.disabledCallbacks.computeIfAbsent(entity, ignored -> new HashMap<>())
         .put(hook, new WeakReference<>(definitionIdentity));
   }

   private void invokeEnding(LivingEntity entity, MLuaBehaviorRegistry.Snapshot behaviors,
                             LuaValue callback, LuaTable context) {
      try {
         this.invoke(entity, "onEnding", behaviors, callback, context);
      } finally {
         synchronized (this) {
            this.disabledCallbacks.remove(entity);
         }
      }
   }

   private LuaTable effectContext(LivingEntity entity, int amplifier) {
      LuaTable ctx = new LuaTable();
      ctx.set("Entity", MLuaEntityWrapper.entityTable(entity, MLuaRuntime.registrationForLevel(entity.level())));
      ctx.set("Amplifier", LuaValue.valueOf(amplifier));
      ctx.set("Effect", LuaValue.valueOf(this.behaviorKey));
      if (this.iconTexture != null) {
         ctx.set("Icon", LuaValue.valueOf(this.iconTexture.toString()));
      }

      return ctx;
   }
}
