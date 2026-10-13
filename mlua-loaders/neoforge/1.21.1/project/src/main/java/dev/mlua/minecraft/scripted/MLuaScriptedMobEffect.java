package dev.mlua.minecraft.scripted;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.mlua.core.MLuaLogger;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import java.lang.reflect.Proxy;
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

   public boolean applyEffectTick(LivingEntity entity, int amplifier) {
      if (!entity.level().isClientSide) {
         this.invoke("onTick", MLuaBehaviorRegistry.getBehavior("effect", this.behaviorKey, "onTick"), effectContext(entity, amplifier));
      }
      return true;
   }

   public void applyInstantenousEffect(Entity source, Entity indirectSource, LivingEntity entity, int amplifier, double health) {
      if (!entity.level().isClientSide) {
         LuaTable ctx = effectContext(entity, amplifier);
         ctx.set("Instant", LuaValue.TRUE);
         this.invoke("onApplied", MLuaBehaviorRegistry.getBehavior("effect", this.behaviorKey, "onApplied"), ctx);
         this.invoke("onEnding", MLuaBehaviorRegistry.getBehavior("effect", this.behaviorKey, "onEnding"), ctx);
      }
   }

   public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
      return !MLuaBehaviorRegistry.getBehavior("effect", this.behaviorKey, "onTick").isnil() && duration > 0 && duration % this.tickInterval == 0;
   }

   public void onEffectAdded(LivingEntity entity, int amplifier) {
      super.onEffectAdded(entity, amplifier);
      if (!entity.level().isClientSide) {
         this.invoke("onApplied", MLuaBehaviorRegistry.getBehavior("effect", this.behaviorKey, "onApplied"), effectContext(entity, amplifier));
      }
   }

   public void onMobRemoved(LivingEntity entity, int amplifier, Entity.RemovalReason reason) {
      if (!entity.level().isClientSide) {
         this.invoke("onEnding", MLuaBehaviorRegistry.getBehavior("effect", this.behaviorKey, "onEnding"), effectContext(entity, amplifier));
      }
      super.onMobRemoved(entity, amplifier, reason);
   }

   public ResourceLocation getIconTexture() {
      return this.iconTexture;
   }

   @SuppressWarnings({"rawtypes", "unchecked"})
   public void initializeClient(Consumer consumer) {
      try {
         Class<?> extensionsClass = Class.forName("net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions");
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

   private void invoke(String hook, LuaValue callback, LuaTable context) {
      if (!callback.isnil()) {
         try {
            callback.invoke(context);
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua mob effect {} callback failed for '{}': {}", new Object[]{hook, this.behaviorKey, exception.getMessage()});
         }
      }
   }

   private LuaTable effectContext(LivingEntity entity, int amplifier) {
      LuaTable ctx = new LuaTable();
      ctx.set("Entity", MLuaEntityWrapper.entityTable(entity));
      ctx.set("Amplifier", LuaValue.valueOf(amplifier));
      ctx.set("Effect", LuaValue.valueOf(this.behaviorKey));
      if (this.iconTexture != null) {
         ctx.set("Icon", LuaValue.valueOf(this.iconTexture.toString()));
      }

      return ctx;
   }
}
