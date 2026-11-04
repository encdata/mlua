package dev.mlua.runtime.client;

import static dev.mlua.runtime.client.MLuaRenderHookRegistry.*;
import static dev.mlua.runtime.client.MLuaRenderCallbackInvoker.*;

import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import java.util.List;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.luaj.vm2.LuaValue;

/** Resolves held-item targets and builds the frame-local Lua contexts for their render passes. */
final class MLuaRenderItemCallbackDispatcher {
    private MLuaRenderItemCallbackDispatcher() { }

    static void renderThirdPerson(LivingEntity entity, ItemStack stack,
            HumanoidArm arm, ItemDisplayContext displayContext,
            Object poseStack, Object bufferSource, int packedLight) {
        renderThirdPerson(entity, stack, arm, displayContext, poseStack, bufferSource, packedLight, null);
    }

    static void renderThirdPerson(LivingEntity entity, ItemStack stack,
            HumanoidArm arm, ItemDisplayContext displayContext,
            Object poseStack, Object bufferSource, int packedLight, Float partialTick) {
        if (entity == null || stack == null || stack.isEmpty()) return;
        String itemId = heldItemId(stack);
        if (itemId == null) return;
        HookBucket hooks = bucket(HookKind.THIRD_PERSON_ITEM, itemId);
        Float framePartialTick = partialTick == null ? MLuaRenderPartialTickResolver.currentClient()
            : MLuaRenderPartialTickResolver.normalize(partialTick);
        MLuaClientRenderManager.renderHeldItemModels(
            MLuaClientRenderManager.RenderTarget.THIRD_PERSON_ITEM, itemId, poseStack,
            bufferSource, packedLight, framePartialTick == null ? 1.0F : framePartialTick);
        if (hooks == null) return;
        List<Hook> renderHooks = renderHooks(hooks);
        if (renderHooks.isEmpty()) return;
        String hand = entity.getMainArm() == arm ? "MAIN_HAND" : "OFF_HAND";
        for (Hook hook : renderHooks) {
            invokeSafely(hook, () -> {
                LuaValue entityValue = MLuaEntityWrapper.entityTable(entity, hook.context.registration());
                return LuaValue.varargsOf(entityValue, renderContext(hook, "ThirdPersonItem",
                    poseStack, bufferSource, null, framePartialTick == null ? 0.0F : framePartialTick,
                    framePartialTick != null, packedLight, 0, null,
                    "Entity", entityValue,
                    "ItemStack", userdata(stack), "Hand", LuaValue.valueOf(hand),
                    "Arm", LuaValue.valueOf(arm.name()),
                    "DisplayContext", LuaValue.valueOf(displayContext.name())));
            });
        }
    }

    static boolean hasThirdPersonCallbacks(ItemStack stack) {
        String itemId = heldItemId(stack);
        if (itemId == null) return false;
        HookBucket hooks = bucket(HookKind.THIRD_PERSON_ITEM, itemId);
        return hooks != null && !hooks.isEmpty();
    }

    static boolean hasThirdPersonRenderers(ItemStack stack) {
        String itemId = heldItemId(stack);
        if (itemId == null) return false;
        HookBucket hooks = bucket(HookKind.THIRD_PERSON_ITEM, itemId);
        return (hooks != null && !hooks.isEmpty()) || MLuaClientRenderManager.hasHeldItemModels(
            MLuaClientRenderManager.RenderTarget.THIRD_PERSON_ITEM, itemId);
    }

    static void renderFirstPerson(AbstractClientPlayer player, InteractionHand hand, ItemStack stack,
            Object poseStack, Object bufferSource, float partialTick,
            float pitch, float swingProgress, float equipProgress, int packedLight) {
        renderFirstPerson(player, hand, stack, poseStack, bufferSource, Float.valueOf(partialTick),
            pitch, swingProgress, equipProgress, packedLight);
    }

    static void renderFirstPerson(AbstractClientPlayer player, InteractionHand hand, ItemStack stack,
            Object poseStack, Object bufferSource, Float partialTick,
            float pitch, float swingProgress, float equipProgress, int packedLight) {
        if (player == null || stack == null || stack.isEmpty()) return;
        String itemId = heldItemId(stack);
        if (itemId == null) return;
        HookBucket hooks = bucket(HookKind.FIRST_PERSON_ITEM, itemId);
        Float validatedPartialTick = partialTick == null ? null : MLuaRenderPartialTickResolver.normalize(partialTick);
        MLuaClientRenderManager.renderHeldItemModels(
            MLuaClientRenderManager.RenderTarget.FIRST_PERSON_ITEM, itemId, poseStack,
            bufferSource, packedLight, validatedPartialTick == null ? 1.0F : validatedPartialTick);
        if (hooks == null) return;
        List<Hook> renderHooks = renderHooks(hooks);
        if (renderHooks.isEmpty()) return;
        float validatedPitch = finiteOrZero(pitch);
        float validatedSwingProgress = unitOrZero(swingProgress);
        float validatedEquipProgress = unitOrZero(equipProgress);
        HumanoidArm arm = player.getMainArm();
        if (hand == InteractionHand.OFF_HAND) {
            arm = arm == HumanoidArm.LEFT ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
        }
        final HumanoidArm renderedArm = arm;
        ItemDisplayContext displayContext = renderedArm == HumanoidArm.LEFT
            ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        for (Hook hook : renderHooks) {
            invokeSafely(hook, () -> {
                LuaValue playerValue = MLuaEntityWrapper.entityTable(player, hook.context.registration());
                return LuaValue.varargsOf(playerValue, renderContext(hook, "FirstPersonItem",
                    poseStack, bufferSource, null, validatedPartialTick == null ? 0.0F : validatedPartialTick,
                    validatedPartialTick != null, packedLight, 0, null,
                    "Entity", playerValue,
                    "Player", playerValue,
                    "ItemStack", userdata(stack), "Hand", LuaValue.valueOf(hand.name()),
                    "Arm", LuaValue.valueOf(renderedArm.name()),
                    "DisplayContext", LuaValue.valueOf(displayContext.name()),
                    "Pitch", LuaValue.valueOf(validatedPitch), "SwingProgress", LuaValue.valueOf(validatedSwingProgress),
                    "EquipProgress", LuaValue.valueOf(validatedEquipProgress)));
            });
        }
    }

    private static String heldItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemKey == null ? null : itemKey.toString();
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }

    private static float unitOrZero(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 0.0F;
    }
}
