package dev.mlua.modules.players.classes;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.minecraft.binding.MLuaMinecraftEnums;
import dev.mlua.mc.impl.entity.MCEntityImpl;
import dev.mlua.mc.impl.item.MCItemStackImpl;
import dev.mlua.mcapi.core.MCComponent;
import dev.mlua.mcapi.core.MCMutableComponent;
import dev.mlua.mcapi.core.MCResourceLocation;
import dev.mlua.mcapi.entity.MCServerPlayer;
import dev.mlua.mcapi.item.MCItemStack;
import dev.mlua.mcapi.sound.MCSoundEvent;
import dev.mlua.mcapi.sound.MCSoundSource;
import dev.mlua.mcapi.world.MCGameType;
import dev.mlua.modules.effects.service.MLuaPotionEffectsService;
import dev.mlua.runtime.data.MLuaDataManager;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Owns methods available on a server-backed Player class instance. */
final class MLuaPlayerServerBindings {
    private MLuaPlayerServerBindings() { }

    static void bind(LuaTable table, MCServerPlayer player, ServerPlayer raw, MLuaRegistration registration) {
        MLuaClassMethods.setInstanceMethod(table, "SendMessage", varArg(args -> {
            String rawMessage = args.checkjstring(2);
            Component component;
            try {
                component = Component.literal(MCComponent.fromJson(rawMessage).getString());
            } catch (Exception ignored) {
                component = Component.literal(rawMessage);
            }
            raw.sendSystemMessage(component);
            return LuaValue.NONE;
        }), "Sends a chat message to this player. On the client, displays the message locally.");
        MLuaClassMethods.setInstanceMethod(table, "SendActionBar", varArg(args -> {
            MCMutableComponent message;
            try {
                message = MCComponent.fromJson(args.checkjstring(2));
            } catch (Exception ignored) {
                message = MCComponent.literal(args.checkjstring(2));
            }
            player.displayClientMessage(message, true);
            return LuaValue.NONE;
        }), "Sends an action bar message to this player.");
        MLuaClassMethods.setInstanceMethod(table, "SendTitle", varArg(args -> {
            if (player.getConnection() != null) {
                String rawText = args.checkjstring(2);
                Component component;
                try {
                    component = Component.literal(MCComponent.fromJson(rawText).getString());
                } catch (Exception ignored) {
                    component = Component.literal(rawText);
                }
                raw.connection.send(new ClientboundSetTitleTextPacket(component));
            }
            return LuaValue.NONE;
        }), "Displays a title to this player.");

        MLuaClassMethods.setInstanceMethod(table, "GetExperienceLevel",
            luaFn(() -> LuaValue.valueOf(player.getExperienceLevel())));
        MLuaClassMethods.setInstanceMethod(table, "GetExperienceProgress",
            luaFn(() -> LuaValue.valueOf(player.getExperienceProgress())));
        MLuaClassMethods.setInstanceMethod(table, "GiveExperience", varArg(args -> {
            player.giveExperiencePoints(MLuaLuaArguments.wholeInt(args, 2, "Player:GiveExperience", "points"));
            return LuaValue.NONE;
        }));
        MLuaClassMethods.setInstanceMethod(table, "GiveExperienceLevels", varArg(args -> {
            player.giveExperienceLevels(MLuaLuaArguments.wholeInt(args, 2, "Player:GiveExperienceLevels", "levels"));
            return LuaValue.NONE;
        }));
        MLuaClassMethods.setInstanceMethod(table, "IsOnline", luaFn(() -> LuaValue.valueOf(player.getConnection() != null)));
        MLuaClassMethods.setInstanceMethod(table, "IsCreative", luaFn(() -> LuaValue.valueOf(player.isCreative())));
        MLuaClassMethods.setInstanceMethod(table, "IsSpectator", luaFn(() -> LuaValue.valueOf(player.isSpectator())));
        MLuaClassMethods.setInstanceMethod(table, "IsSleeping", luaFn(() -> LuaValue.valueOf(player.isSleeping())));
        MLuaClassMethods.setInstanceMethod(table, "IsCrouching", luaFn(() -> LuaValue.valueOf(player.isCrouching())));
        MLuaClassMethods.setInstanceMethod(table, "IsSprinting", luaFn(() -> LuaValue.valueOf(player.isSprinting())));
        MLuaClassMethods.setInstanceMethod(table, "GetBlockPosition", luaFn(() -> {
            var position = player.getBlockPosition();
            return MLuaPlayerWrapper.positionTable(position.getX(), position.getY(), position.getZ());
        }));
        MLuaClassMethods.setInstanceMethod(table, "GetEyeCFrame", luaFn(() -> {
            var look = MLuaEntityWrapper.lookAngle(player);
            return MLuaCFrame.fromEntity(player.getX(), raw.getEyeY(), player.getZ(),
                look.x(), look.y(), look.z()).toLua();
        }));
        MLuaClassMethods.setInstanceMethod(table, "GetCameraCFrame", varArg(args -> {
            throw new LuaError("[MLua] player:GetCameraCFrame() is client-side only. Use Minecraft:GetService(\"Camera\") in a .client.lua script.");
        }));

        MLuaClassMethods.setInstanceMethod(table, "GetGameMode", luaFn(() -> gameMode(player)),
            "Returns this player's current game mode.");
        MLuaClassMethods.setInstanceMethod(table, "GetServerGameMode", luaFn(() -> gameMode(player)),
            "Returns this player's authoritative server game mode.");
        MLuaClassMethods.setInstanceMethod(table, "SetGameMode", varArg(args -> {
            MCGameType type = MCGameType.byName(MLuaMinecraftEnums.resolveGameMode(args.arg(2)).getName());
            player.setGameMode(type);
            return LuaValue.NONE;
        }), "Changes this player's game mode. This method is server-side only.");
        MLuaClassMethods.setInstanceMethod(table, "SetLocalGameMode", varArg(args -> unavailableLocalPlayerControl("SetLocalGameMode")));
        MLuaClassMethods.setInstanceMethod(table, "ResetLocalGameMode", varArg(args -> unavailableLocalPlayerControl("ResetLocalGameMode")));
        MLuaClassMethods.setInstanceMethod(table, "CanFly", luaFn(() -> unavailableLocalPlayerControl("CanFly")));
        MLuaClassMethods.setInstanceMethod(table, "SetCanFly", varArg(args -> unavailableLocalPlayerControl("SetCanFly")));
        MLuaClassMethods.setInstanceMethod(table, "IsFlying", luaFn(() -> unavailableLocalPlayerControl("IsFlying")));
        MLuaClassMethods.setInstanceMethod(table, "SetFlying", varArg(args -> unavailableLocalPlayerControl("SetFlying")));
        MLuaClassMethods.setInstanceMethod(table, "HasPermission", varArg(args -> {
            int level = MLuaLuaArguments.wholeInt(args, 2, "Player:HasPermission", "level");
            return LuaValue.valueOf(player.hasPermissions(level));
        }));
        MLuaClassMethods.setInstanceMethod(table, "IsOperator", luaFn(() -> LuaValue.valueOf(player.hasPermissions(4))));
        bindInventory(table, player, registration);
        bindEffects(table, player, raw, registration);
        bindSound(table, player);
        bindData(table, raw);
    }

    private static void bindInventory(LuaTable table, MCServerPlayer player, MLuaRegistration registration) {
        MLuaClassMethods.setInstanceMethod(table, "GetSelectedSlot", luaFn(() -> LuaValue.valueOf(player.getInventory().getSelected() + 1)));
        MLuaClassMethods.setInstanceMethod(table, "SetSelectedSlot", varArg(args -> {
            int slot = MLuaLuaArguments.wholeInt(args, 2, "Player:SetSelectedSlot", "slot") - 1;
            if (slot < 0 || slot >= 9) throw new LuaError("[MLua] Selected slot must be between 1 and 9.");
            player.getInventory().setSelected(slot);
            return LuaValue.NONE;
        }));
        MLuaClassMethods.setInstanceMethod(table, "GetHeldItem", varArg(args -> heldItem(player.getMainHandItem(), registration)));
        MLuaClassMethods.setInstanceMethod(table, "GetOffhandItem", varArg(args -> heldItem(player.getOffhandItem(), registration)));
        MLuaClassMethods.setInstanceMethod(table, "GetViewMode", varArg(args -> {
            throw new LuaError("[MLua] Player.ViewMode is client-side only for server player tables.");
        }));
        MLuaClassMethods.setInstanceMethod(table, "SetViewMode", varArg(args -> {
            throw new LuaError("[MLua] Player.ViewMode can only be written from a .client.lua script.");
        }));
        MLuaClassMethods.setInstanceMethod(table, "GiveItem", varArg(args -> {
            ItemStack stack = MLuaItemWrapper.toItemStack(args.arg(2), registration);
            if (stack.isEmpty()) return LuaValue.FALSE;
            return LuaValue.valueOf(player.addItem(MCItemStackImpl.fromReal(stack)));
        }));
        LuaTable inventory = MLuaPlayerInventoryBindings.attach(table, player, true, true, registration);
        MLuaClassMethods.setInstanceMethod(table, "SetSlot", varArg(args ->
            MLuaClassMethods.invokeInstanceMethod(inventory, "SetSlot", args.arg(2), args.arg(3))));
        MLuaClassMethods.setInstanceMethod(table, "SetHotbarSlot", varArg(args ->
            MLuaClassMethods.invokeInstanceMethod(inventory, "SetHotbarSlot", args.arg(2), args.arg(3))));
        MLuaClassMethods.setInstanceMethod(table, "SetArmorSlot", varArg(args ->
            MLuaClassMethods.invokeInstanceMethod(inventory, "SetArmorSlot", args.arg(2), args.arg(3))));
        MLuaClassMethods.setInstanceMethod(table, "SetOffhandSlot", varArg(args ->
            MLuaClassMethods.invokeInstanceMethod(inventory, "SetOffhandSlot", args.arg(2))));
    }

    private static void bindEffects(LuaTable table, MCServerPlayer player, ServerPlayer raw, MLuaRegistration registration) {
        MLuaClassMethods.setInstanceMethod(table, "GiveEffect", varArg(args ->
            MLuaPotionEffectsService.giveEffect((LivingEntity) ((MCEntityImpl) player).inner(), args)));
        MLuaClassMethods.setInstanceMethod(table, "RemoveEffect", varArg(args -> {
            LivingEntity entity = (LivingEntity) ((MCEntityImpl) player).inner();
            //--version 1.21.1,26.1
            return LuaValue.valueOf(entity.removeEffect(MLuaPotionEffectsService.resolveHolderFromLua(args.arg(2))));
            //--endversion
            //--version 1.20.1
            return LuaValue.valueOf(entity.removeEffect(MLuaPotionEffectsService.resolveFromLua(args.arg(2))));
            //--endversion
        }));
        MLuaClassMethods.setInstanceMethod(table, "HasEffect", varArg(args -> {
            LivingEntity entity = (LivingEntity) ((MCEntityImpl) player).inner();
            //--version 1.21.1,26.1
            return LuaValue.valueOf(entity.hasEffect(MLuaPotionEffectsService.resolveHolderFromLua(args.arg(2))));
            //--endversion
            //--version 1.20.1
            return LuaValue.valueOf(entity.hasEffect(MLuaPotionEffectsService.resolveFromLua(args.arg(2))));
            //--endversion
        }));
        MLuaClassMethods.setInstanceMethod(table, "GetEffects", luaFn(() ->
            MLuaPotionEffectsService.effectsTable((LivingEntity) ((MCEntityImpl) player).inner(), registration)));
        MLuaClassMethods.setInstanceMethod(table, "ClearEffects", luaFn(() -> {
            ((LivingEntity) ((MCEntityImpl) player).inner()).removeAllEffects();
            return LuaValue.NONE;
        }));
    }

    private static void bindSound(LuaTable table, MCServerPlayer player) {
        MLuaClassMethods.setInstanceMethod(table, "PlaySound", varArg(args -> {
            MCResourceLocation location = MCResourceLocation.parse(args.checkjstring(2));
            ResourceLocation rawLocation = MLuaResourceLocations.parse(location.toString());
            //--version 26.1
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(rawLocation).map(reference -> reference.value()).orElse(null);
            //--endversion
            //--version 1.20.1,1.21.1
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(rawLocation);
            //--endversion
            if (sound == null) throw new LuaError("[MLua] Unknown sound: " + args.checkjstring(2));
            float volume = MLuaLuaArguments.optionalNonNegativeFiniteFloat(args, 3, 1.0f, "Player:PlaySound", "volume");
            float pitch = MLuaLuaArguments.optionalNonNegativeFiniteFloat(args, 4, 1.0f, "Player:PlaySound", "pitch");
            player.playNotifySound(MCSoundEvent.create(location), MCSoundSource.PLAYERS, volume, pitch);
            return LuaValue.NONE;
        }));
    }

    private static void bindData(LuaTable table, ServerPlayer raw) {
        MLuaClassMethods.setInstanceMethod(table, "GetPersistentData", luaFn(() -> MLuaDataManager.playerPersistentData(raw, true)));
        MLuaClassMethods.setInstanceMethod(table, "GetSyncedData", luaFn(() -> MLuaDataManager.playerSyncedData(raw)));
        MLuaClassMethods.setInstanceMethod(table, "SetData", varArg(args -> {
            LuaTable persistent = MLuaDataManager.playerPersistentData(raw, true);
            persistent.get("Set").call(persistent, LuaValue.valueOf(args.checkjstring(2)), args.arg(3));
            return LuaValue.NONE;
        }));
        MLuaClassMethods.setInstanceMethod(table, "GetData", varArg(args -> {
            LuaTable persistent = MLuaDataManager.playerPersistentData(raw, true);
            return persistent.get("Get").call(persistent, LuaValue.valueOf(args.checkjstring(2)));
        }));
        MLuaClassMethods.setInstanceMethod(table, "RemoveData", varArg(args -> {
            LuaTable persistent = MLuaDataManager.playerPersistentData(raw, true);
            persistent.get("Remove").call(persistent, LuaValue.valueOf(args.checkjstring(2)));
            return LuaValue.NONE;
        }));
    }

    private static LuaValue gameMode(MCServerPlayer player) {
        ServerPlayer raw = (ServerPlayer) ((MCEntityImpl) player).inner();
        return MLuaMinecraftEnums.gameModeOf(raw.gameMode.getGameModeForPlayer());
    }

    private static LuaValue heldItem(MCItemStack stack, MLuaRegistration registration) {
        return stack.isEmpty() ? LuaValue.NIL : MLuaItemWrapper.itemTable(((MCItemStackImpl) stack).inner(), registration);
    }

    private static LuaValue unavailableLocalPlayerControl(String method) {
        throw new LuaError("[MLua] Player:" + method
            + " is only available for the local player in a .client.lua script.");
    }

    private static LuaValue luaFn(Supplier<LuaValue> function) {
        return MLuaClassMethods.zeroArg(function);
    }

    private static LuaValue varArg(Function<Varargs, LuaValue> function) {
        return MLuaClassMethods.varArg(function);
    }
}
