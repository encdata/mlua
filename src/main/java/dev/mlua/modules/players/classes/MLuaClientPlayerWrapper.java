package dev.mlua.modules.players.classes;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.math.MLuaVector;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.binding.MLuaMinecraftEnums;
import dev.mlua.modules.effects.service.MLuaPotionEffectsService;
import dev.mlua.minecraft.client.MLuaCameraController;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.runtime.data.MLuaDataManager;
import dev.mlua.runtime.client.MLuaClientThread;
import dev.mlua.mcapi.core.MCComponent;
import dev.mlua.mcapi.core.MCVec3;
import dev.mlua.mcapi.entity.MCLocalPlayer;
import dev.mlua.mc.impl.core.MCComponentImpl;
import dev.mlua.mc.impl.entity.MCEntityImpl;
import dev.mlua.mc.impl.entity.MCLocalPlayerImpl;
import dev.mlua.mc.impl.entity.MCPlayerImpl;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import static dev.mlua.modules.players.classes.MLuaPlayerWrapper.*;

/** Owns client-only Player adaptation and local control bindings. */
public final class MLuaClientPlayerWrapper {
   private static final ClassValue<Method> CHAT_MESSAGE_ADD_METHOD = new ClassValue<>() {
      @Override
      protected Method computeValue(Class<?> chatType) {
         for (Method method : chatType.getMethods()) {
            if (!method.getName().equals("addMessage")) continue;
            Class<?>[] parameters = method.getParameterTypes();
            if ((parameters.length == 1 || parameters.length == 4)
               && Component.class.isAssignableFrom(parameters[0])) return method;
         }
         throw new IllegalStateException("No compatible ChatComponent.addMessage overload is available");
      }
   };

   private MLuaClientPlayerWrapper() {}

   public static LuaTable playerEntityTable(Player player, MLuaRegistration registration) {
      if (player instanceof LocalPlayer local) {
         return localPlayerTable(MCLocalPlayerImpl.fromReal(local), registration);
      }
      if (player instanceof AbstractClientPlayer clientPlayer) {
         return clientPlayerTable(clientPlayer, registration);
      }
      return clientPlayerTable(player, registration);
   }

   public static LuaTable clientPlayerTable(AbstractClientPlayer player, MLuaRegistration registration) {
      return clientPlayerTable((Player) player, registration);
   }

   private static LuaTable clientPlayerTable(Player player,
                                             MLuaRegistration registration) {
      classTable(registration);
      if (player instanceof LocalPlayer local) {
         return localPlayerTable(MCLocalPlayerImpl.fromReal(local), registration);
      }
      LuaTable cachedPlayer = MLuaEntityWrapper.cachedPlayerTable(player, registration);
      if (cachedPlayer != null) {
         LuaValue cachedEntity = cachedPlayer.rawget("__entity");
         if (cachedEntity.isuserdata(Entity.class)
               && cachedEntity.touserdata(Entity.class) == player) {
            return cachedPlayer;
         }
      }
      LuaTable table = MLuaEntityWrapper.livingEntityTableBase(player, registration);
      registration.attachClassInstance(table, "Player", "LivingEntity");
      table.rawset("__entity", LuaValue.userdataOf(player));
      MLuaClassMethods.setInstanceMethod(table, "GetIsLocalPlayer", luaFn(() -> LuaValue.FALSE));
      MLuaClassMethods.setInstanceMethod(table, "GetDisplayName", luaFn(() -> LuaValue.valueOf(player.getDisplayName().getString())));
      MLuaClassMethods.setInstanceMethod(table, "IsOnline", luaFn(() -> LuaValue.TRUE));
      MLuaClassMethods.setInstanceMethod(table, "IsCreative", luaFn(() -> LuaValue.valueOf(player.isCreative())));
      MLuaClassMethods.setInstanceMethod(table, "IsSpectator", luaFn(() -> LuaValue.valueOf(player.isSpectator())));
      MLuaClassMethods.setInstanceMethod(table, "GetGameMode", luaFn(() -> clientGameMode(player)),
         "Returns the game mode reported by the server for this remote player.");
      MLuaClassMethods.setInstanceMethod(table, "GetServerGameMode", luaFn(() -> clientGameMode(player)),
         "Returns the game mode reported by the server, independently of the local client's selected mode.");
      MLuaClassMethods.setInstanceMethod(table, "SetGameMode", varArg(args -> {
         throw new LuaError("[MLua] Player:SetGameMode is server-side only. Client scripts cannot change a player's server game mode.");
      }), "Changes this player's game mode. This method is server-side only.");
      MLuaClassMethods.setInstanceMethod(table, "SetLocalGameMode",
         varArg(args -> unavailableLocalPlayerControl("SetLocalGameMode")));
      MLuaClassMethods.setInstanceMethod(table, "ResetLocalGameMode",
         varArg(args -> unavailableLocalPlayerControl("ResetLocalGameMode")));
      MLuaClassMethods.setInstanceMethod(table, "CanFly",
         luaFn(() -> unavailableLocalPlayerControl("CanFly")));
      MLuaClassMethods.setInstanceMethod(table, "SetCanFly",
         varArg(args -> unavailableLocalPlayerControl("SetCanFly")));
      MLuaClassMethods.setInstanceMethod(table, "IsFlying",
         luaFn(() -> unavailableLocalPlayerControl("IsFlying")));
      MLuaClassMethods.setInstanceMethod(table, "SetFlying",
         varArg(args -> unavailableLocalPlayerControl("SetFlying")));
      LuaValue unavailableFood = luaFn(() -> LuaValue.NIL);
      LuaValue unavailableFoodMutation = varArg(args -> unavailableRemotePlayerState("food"));
      MLuaClassMethods.setInstanceMethod(table, "GetFood", unavailableFood);
      MLuaClassMethods.setInstanceMethod(table, "GetFoodLevel", unavailableFood);
      MLuaClassMethods.setInstanceMethod(table, "SetFood", unavailableFoodMutation);
      MLuaClassMethods.setInstanceMethod(table, "SetFoodLevel", unavailableFoodMutation);
      MLuaClassMethods.setInstanceMethod(table, "GetSaturation", luaFn(() -> LuaValue.NIL));
      MLuaClassMethods.setInstanceMethod(table, "SetSaturation", varArg(args -> unavailableRemotePlayerState("food")));
      MLuaClassMethods.setInstanceMethod(table, "CauseFoodExhaustion", varArg(args -> unavailableRemotePlayerState("food")));
      MLuaClassMethods.setInstanceMethod(table, "GetExperienceLevel", luaFn(() -> LuaValue.NIL));
      MLuaClassMethods.setInstanceMethod(table, "GetExperienceProgress", luaFn(() -> LuaValue.NIL));
      MLuaClassMethods.setInstanceMethod(table, "GetSelectedSlot", luaFn(() -> LuaValue.NIL));
      MLuaClassMethods.setInstanceMethod(table, "SetSelectedSlot", varArg(args -> unavailableRemotePlayerState("inventory selection")));
      MLuaClassMethods.setInstanceMethod(table, "GetViewMode", luaFn(() -> LuaValue.NIL));
      MLuaClassMethods.setInstanceMethod(table, "SetViewMode", varArg(args -> {
         throw new LuaError("[MLua] ViewMode is only available on the local player.");
      }));
      bindLevelMethods(table, player.level(), registration);
      MLuaClassMethods.setInstanceMethod(table, "IsSleeping", luaFn(() -> LuaValue.valueOf(player.isSleeping())));
      MLuaClassMethods.setInstanceMethod(table, "IsCrouching", luaFn(() -> LuaValue.valueOf(player.isCrouching())));
      MLuaClassMethods.setInstanceMethod(table, "IsSprinting", luaFn(() -> LuaValue.valueOf(player.isSprinting())));
      MLuaClassMethods.setInstanceMethod(table, "GetEffects", luaFn(() ->
         MLuaPotionEffectsService.effectsTable(player, registration)));
      MLuaClassMethods.setInstanceMethod(table, "GetBlockPosition", luaFn(() ->
         positionTable(player.blockPosition().getX(), player.blockPosition().getY(), player.blockPosition().getZ())));
      MLuaClassMethods.setInstanceMethod(table, "GetHeldItem", luaFn(() -> {
         var stack = player.getMainHandItem();
         return stack.isEmpty() ? LuaValue.NIL : MLuaItemWrapper.itemTable(stack, registration);
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetOffhandItem", luaFn(() -> {
         var stack = player.getOffhandItem();
         return stack.isEmpty() ? LuaValue.NIL : MLuaItemWrapper.itemTable(stack, registration);
      }));
      MLuaPlayerInventoryBindings.attach(table, MCPlayerImpl.fromReal(player), false, false, registration);
      MLuaClassMethods.setInstanceMethod(table, "GetOpenContainer", luaFn(() -> LuaValue.NIL));
      MLuaClassMethods.setInstanceMethod(table, "GetOpenContainerTitle", luaFn(() -> LuaValue.NIL));
      MLuaClassMethods.setInstanceMethod(table, "GetOpenContainerSize", luaFn(() -> LuaValue.valueOf(0)));
      attachNearbyPlayers(table, player, registration);
      MLuaEntityWrapper.attachEntityProperties(table);
      MLuaEntityWrapper.cachePlayerTable(player, registration, table);
      return table;
   }

   public static LuaTable localPlayerTable(MCLocalPlayer player) {
      Entity raw = ((MCEntityImpl) player).inner();
      return localPlayerTable(player, MLuaRuntime.registrationForLevel(raw.level()));
   }

   public static LuaTable localPlayerTable(MCLocalPlayer player, MLuaRegistration registration) {
      classTable(registration);
      LocalPlayer raw = (LocalPlayer) ((MCEntityImpl) player).inner();
      LuaTable cachedPlayer = MLuaEntityWrapper.cachedPlayerTable(raw, registration);
      if (cachedPlayer != null) {
         LuaValue cachedEntity = cachedPlayer.rawget("__entity");
         if (cachedEntity.isuserdata(Entity.class)
               && cachedEntity.touserdata(Entity.class) == raw) {
            return cachedPlayer;
         }
      }
      LuaTable table = MLuaEntityWrapper.livingEntityTableBase(raw, registration);
      registration.attachClassInstance(table, "Player", "LivingEntity");
      table.rawset("__entity", LuaValue.userdataOf(raw));
      table.rawset("__is_local_player", LuaValue.TRUE);
      MLuaClassMethods.setInstanceMethod(table, "GetDisplayName", luaFn(() -> {
         return LuaValue.valueOf(!MLuaEntityWrapper.entityDisplayName(player).isBlank() ? MLuaEntityWrapper.entityDisplayName(player) : playerName(player));
      }), "Returns this player's display name.");
      MLuaClassMethods.setInstanceMethod(table, "SendMessage", varArg((args) -> {
         String rawMsg = args.checkjstring(2);
         Component component;
         try {
            component = Component.literal(MCComponent.fromJson(rawMsg).getString());
         } catch (Exception var6) {
            component = Component.literal(rawMsg);
         }

         try {
            Minecraft minecraft = MLuaMinecraftClient.instance();
            if (minecraft.gui != null && minecraft.gui.getChat() != null) {
               addLocalChatMessage(minecraft.gui.getChat(), component);
            } else {
               player.displayClientMessage(MCComponentImpl.fromReal(component), false);
            }
         } catch (Exception var5) {
            player.displayClientMessage(MCComponentImpl.fromReal(component), false);
         }
         return LuaValue.NONE;
      }), "Sends a chat message to this player. On the client, displays the message locally.");
      MLuaClassMethods.setInstanceMethod(table, "SendChatMessage", varArg(args -> {
         requireActiveLocalPlayer(raw, "Player:SendChatMessage");
         String message = args.checkjstring(2);
         Method chatSubmission = ClientChatSubmission.METHOD;
         if (chatSubmission == null) {
            throw new LuaError("[MLua] This Minecraft version does not expose the local chat submission method.");
         }
         Object connection = MLuaMinecraftClient.instance().getConnection();
         if (connection == null) throw new LuaError("[MLua] Player:SendChatMessage requires an active client connection.");
         int submitted = 0;
         for (String line : message.split("\\R", -1)) {
            if (line.isEmpty()) continue;
            try {
               chatSubmission.invoke(connection, line);
               submitted++;
            } catch (java.lang.reflect.InvocationTargetException error) {
               Throwable cause = error.getCause() == null ? error : error.getCause();
               throw new LuaError("[MLua] Player:SendChatMessage failed after " + submitted + " line(s): " + cause.getMessage());
            } catch (ReflectiveOperationException error) {
               throw new LuaError("[MLua] Player:SendChatMessage could not submit chat in this Minecraft version.");
            }
         }
         return LuaValue.valueOf(submitted);
      }), "Submits each non-empty line through Minecraft's normal local-player chat path. This sends chat to the connected server when multiplayer is active and returns the number of submitted lines.");
      MLuaClassMethods.setInstanceMethod(table, "SendActionBar", varArg((args) -> {
         Component component;
         try {
            component = Component.literal(MCComponent.fromJson(args.checkjstring(2)).getString());
         } catch (Exception var5) {
            component = Component.literal(args.checkjstring(2));
         }

         try {
            MLuaMinecraftClient.showActionBar(component);
         } catch (Exception var4) {
            player.displayClientMessage(MCComponentImpl.fromReal(component), true);
         }
         return LuaValue.NONE;
      }), "Sends an action bar message to this player.");
      MLuaPlayerFoodBindings.bind(table, player);
      MLuaClassMethods.setInstanceMethod(table, "GetExperienceLevel", luaFn(() -> {
         return LuaValue.valueOf(player.getExperienceLevel());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetExperienceProgress", luaFn(() -> {
         return LuaValue.valueOf((double)player.getExperienceProgress());
      }));
      MLuaClassMethods.setInstanceMethod(table, "IsCreative", luaFn(() -> {
         GameType localMode = localClientGameMode(raw);
         return LuaValue.valueOf(localMode == null ? player.isCreative()
            : localMode == GameType.CREATIVE);
      }));
      MLuaClassMethods.setInstanceMethod(table, "IsSpectator", luaFn(() -> {
         GameType localMode = localClientGameMode(raw);
         return LuaValue.valueOf(localMode == null ? player.isSpectator()
            : localMode == GameType.SPECTATOR);
      }));
      MLuaClassMethods.setInstanceMethod(table, "IsSleeping", luaFn(() -> {
         return LuaValue.valueOf(player.isSleeping());
      }));
      MLuaClassMethods.setInstanceMethod(table, "IsCrouching", luaFn(() -> {
         return LuaValue.valueOf(player.isCrouching());
      }));
      MLuaClassMethods.setInstanceMethod(table, "IsSprinting", luaFn(() -> {
         return LuaValue.valueOf(player.isSprinting());
      }));
      MLuaClassMethods.setInstanceMethod(table, "Jump", luaFn(() -> {
         return runLocalPlayerAction(raw, "Player:Jump", () ->
            raw.setDeltaMovement(raw.getDeltaMovement().add(0.0, 0.42, 0.0)));
      }), "Requests a jump from the local player using Minecraft's normal movement rules.");
      MLuaClassMethods.setInstanceMethod(table, "SetSprinting", varArg(args -> {
         boolean sprinting = args.checkboolean(2);
         return runLocalPlayerAction(raw, "Player:SetSprinting", () -> raw.setSprinting(sprinting));
      }), "Sets the local player's sprint state; Minecraft and the server still apply their normal movement rules.");
      MLuaClassMethods.setInstanceMethod(table, "Turn", varArg(args -> {
         requireActiveLocalPlayer(raw, "Player:Turn");
         LuaValue delta = args.arg(2);
         double mouseX;
         double mouseY;
         if (MLuaVector.isVector2(delta)) {
            double[] components = MLuaVector.components2(delta);
            mouseX = components[0];
            mouseY = components[1];
         } else if (args.narg() >= 3) {
            mouseX = args.checkdouble(2);
            mouseY = args.checkdouble(3);
         } else {
            throw new LuaError("[MLua] Player:Turn expects a Vector2 mouse delta or mouseDeltaX, mouseDeltaY.");
         }
         if (!Double.isFinite(mouseX) || !Double.isFinite(mouseY)
               || Math.abs(mouseX) > 10_000 || Math.abs(mouseY) > 10_000) {
            throw new LuaError("[MLua] Player:Turn mouse deltas must be finite and within -10000..10000.");
         }
         double turnX = mouseX;
         double turnY = mouseY;
         return runLocalPlayerAction(raw, "Player:Turn", () -> raw.turn(turnX, turnY));
      }), "Applies raw mouse-turn deltas to the local player view using Minecraft's normal turn method.");
      MLuaClassMethods.setInstanceMethod(table, "SwingArm", varArg(args -> {
         InteractionHand hand = localHand(args.arg(2));
         return runLocalPlayerAction(raw, "Player:SwingArm", () -> raw.swing(hand));
      }), "Plays the local player's arm swing for the selected hand.");
      MLuaClassMethods.setInstanceMethod(table, "AttackEntity", varArg(args -> {
         Entity target = localEntityArgument(args.arg(2), "Player:AttackEntity");
         return LuaValue.valueOf(dev.mlua.minecraft.client.MLuaClientEntityActions.attack(raw, target));
      }), "Requests a normal Minecraft attack against an entity and returns true when submitted. The game mode and server validate the hit.");
      MLuaClassMethods.setInstanceMethod(table, "UseItem", varArg(args -> {
         InteractionHand hand = localHand(args.arg(2));
         return MLuaClientThread.callOnClientThread(() -> {
            requireActiveLocalPlayer(raw, "Player:UseItem");
            MLuaMinecraftClient.instance().gameMode.useItem(raw, hand);
            return LuaValue.NONE;
         });
      }), "Requests the normal item-use action for the selected hand.");
      MLuaClassMethods.setInstanceMethod(table, "IsOnline", luaFn(() -> {
         return LuaValue.TRUE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetBlockPosition", luaFn(() -> {
         return positionTable((double)player.getBlockPosition().getX(), (double)player.getBlockPosition().getY(), (double)player.getBlockPosition().getZ());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetEyeCFrame", luaFn(() -> {
         MCVec3 look = MLuaEntityWrapper.lookAngle(player);
         return MLuaCFrame.fromEntity(player.getX(), raw.getEyeY(), player.getZ(), look.x(), look.y(), look.z()).toLua();
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetCameraCFrame", luaFn(() -> {
         return MLuaCameraController.getCFrame().toLua();
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetViewMode", luaFn(() -> {
         return LuaValue.valueOf(MLuaCameraController.getViewMode());
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetViewMode", varArg((args) -> {
         String mode = args.checkjstring(2);
         return MLuaClientThread.callOnClientThread(() -> {
            requireActiveLocalPlayer(raw, "Player:SetViewMode");
            MLuaCameraController.setViewMode(mode);
            return LuaValue.NONE;
         });
      }));
      bindLevelMethods(table, raw.level(), registration);
      MLuaClassMethods.setInstanceMethod(table, "GetPersistentData", luaFn(() -> {
         return MLuaDataManager.playerPersistentData(raw, false);
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetSyncedData", luaFn(() -> {
         return MLuaDataManager.playerSyncedData(raw);
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetGameMode", luaFn(() -> {
         GameType mode = localClientGameMode(raw);
         return mode == null ? LuaValue.valueOf("unknown") : MLuaMinecraftEnums.gameModeOf(mode);
      }), "Returns the game mode this client currently assigns to its local player. After SetLocalGameMode(), it can differ from GetServerGameMode(); this is client-side state, not the server's authoritative mode.");
      MLuaClassMethods.setInstanceMethod(table, "GetServerGameMode", luaFn(() -> clientGameMode(raw)),
         "Returns the game mode reported by the server, independently of the local client's selected mode.");
      MLuaClassMethods.setInstanceMethod(table, "SetGameMode", varArg(args -> {
         throw new LuaError("[MLua] Player:SetGameMode is server-side only. Use Player:SetLocalGameMode() to make the local client treat its player as another game mode.");
      }), "Changes this player's game mode. This method is server-side only; local client scripts can use SetLocalGameMode for a client-only override.");
      MLuaClassMethods.setInstanceMethod(table, "SetLocalGameMode", varArg(args -> {
         GameType mode = MLuaMinecraftEnums.resolveGameMode(args.arg(2));
         return MLuaClientThread.callOnClientThread(() -> {
            requireActiveLocalPlayer(raw, "Player:SetLocalGameMode");
            MultiPlayerGameMode gameMode = MLuaMinecraftClient.instance().gameMode;
            gameMode.setLocalMode(mode);
            return LuaValue.NONE;
         });
      }), "Changes the game mode this client assigns to its local player, making the client behave as though the player were in that mode. This is a local client-side override: it does not change the server's mode or grant server permissions, and the server may overwrite it.");
      MLuaClassMethods.setInstanceMethod(table, "ResetLocalGameMode", varArg(args -> {
         return MLuaClientThread.callOnClientThread(() -> {
            requireActiveLocalPlayer(raw, "Player:ResetLocalGameMode");
            GameType serverMode = serverGameType(raw);
            if (serverMode == null) {
               throw new LuaError("[MLua] Player:ResetLocalGameMode cannot run because the server-reported game mode is unavailable.");
            }
            MLuaMinecraftClient.instance().gameMode.setLocalMode(serverMode);
            return LuaValue.NONE;
         });
      }), "Restores the local client's game mode to the latest mode reported by the server. This does not change server state.");
      MLuaClassMethods.setInstanceMethod(table, "CanFly", luaFn(() -> {
         requireActiveLocalPlayer(raw, "Player:CanFly");
         return LuaValue.valueOf(raw.getAbilities().mayfly);
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetCanFly", varArg(args -> {
         boolean allowed = args.checkboolean(2);
         return runLocalPlayerAction(raw, "Player:SetCanFly", () -> {
            raw.getAbilities().mayfly = allowed;
            if (!allowed) raw.getAbilities().flying = false;
         });
      }), "Changes the local may-fly ability flag. In multiplayer, server ability updates can replace this value.");
      MLuaClassMethods.setInstanceMethod(table, "IsFlying", luaFn(() -> {
         requireActiveLocalPlayer(raw, "Player:IsFlying");
         return LuaValue.valueOf(raw.getAbilities().flying);
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetFlying", varArg(args -> {
         boolean flying = args.checkboolean(2);
         return runLocalPlayerAction(raw, "Player:SetFlying", () -> {
            if (flying && !raw.getAbilities().mayfly) {
               throw new LuaError("[MLua] Player:SetFlying(true) requires Player:SetCanFly(true) or a game mode that allows flight.");
            }
            raw.getAbilities().flying = flying;
         });
      }), "Changes the local flying flag. SetCanFly(true) first when the current game mode does not allow flight; a server may replace both flags.");
      MLuaClassMethods.setInstanceMethod(table, "GetHeldItem", luaFn(() -> {
         return heldItem(player.getMainHandItem(), registration);
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetOffhandItem", luaFn(() -> {
         return heldItem(player.getOffhandItem(), registration);
      }));
      MLuaPlayerInventoryBindings.attach(table, player, false, false, registration);
      MLuaClassMethods.setInstanceMethod(table, "GetSelectedSlot", luaFn(() -> {
         return LuaValue.valueOf(player.getInventory().getSelected() + 1);
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetSelectedSlot", varArg((args) -> {
         int slot = MLuaLuaArguments.wholeInt(args, 2, "Player:SetSelectedSlot", "slot") - 1;
         if (slot < 0 || slot >= 9) {
            throw new LuaError("[MLua] Selected slot must be between 1 and 9.");
         }
         return runLocalPlayerAction(raw, "Player:SetSelectedSlot", () ->
            player.getInventory().setSelected(slot));
      }));
      attachNearbyPlayers(table, raw, registration);
      MLuaClassMethods.setInstanceMethod(table, "GetEffects", luaFn(() -> {
         return MLuaPotionEffectsService.effectsTable((LivingEntity)((MCEntityImpl) player).inner(), registration);
      }));
      LuaTable attached = MLuaEntityWrapper.attachEntityProperties(table);
      MLuaEntityWrapper.cachePlayerTable(raw, registration, attached);
      return attached;
   }

   private static InteractionHand localHand(LuaValue value) {
      if (value.isnil()) return InteractionHand.MAIN_HAND;
      if (!value.isstring()) {
         throw new LuaError("[MLua] Hand must be 'main' or 'off'.");
      }
      return switch (value.checkjstring().toLowerCase(java.util.Locale.ROOT)) {
         case "main", "main_hand" -> InteractionHand.MAIN_HAND;
         case "off", "off_hand" -> InteractionHand.OFF_HAND;
         default -> throw new LuaError("[MLua] Hand must be 'main' or 'off'.");
      };
   }

   private static void requireActiveLocalPlayer(LocalPlayer player, String operation) {
      Minecraft minecraft = MLuaMinecraftClient.instance();
      if (!dev.mlua.runtime.client.MLuaClientThread.isClientExecutionContext()) {
         throw new LuaError("[MLua] " + operation + " must run on Minecraft's client thread.");
      }
      if (minecraft.player != player || minecraft.gameMode == null || minecraft.level == null) {
         throw new LuaError("[MLua] " + operation + " requires the active local player in a loaded world.");
      }
   }

   private static LuaValue runLocalPlayerAction(LocalPlayer player, String operation, Runnable action) {
      return MLuaClientThread.callOnClientThread(() -> {
         requireActiveLocalPlayer(player, operation);
         action.run();
         return LuaValue.NONE;
      });
   }

   private static GameType localClientGameMode(LocalPlayer player) {
      Minecraft minecraft = MLuaMinecraftClient.instance();
      if (minecraft.player != player || minecraft.level == null) return null;
      MultiPlayerGameMode gameMode = minecraft.gameMode;
      if (gameMode == null) return null;
      try {
         return gameMode.getPlayerMode();
      } catch (RuntimeException unavailable) {
         return null;
      }
   }

   private static LuaValue clientGameMode(Player player) {
      GameType mode = serverGameType(player);
      return mode == null ? LuaValue.valueOf("unknown") : MLuaMinecraftEnums.gameModeOf(mode);
   }

   private static GameType serverGameType(Player player) {
      Minecraft minecraft = MLuaMinecraftClient.instance();
      if (minecraft.getConnection() == null) return null;
      var playerInfo = minecraft.getConnection().getPlayerInfo(player.getUUID());
      return playerInfo == null ? null : playerInfo.getGameMode();
   }

   private static final class ClientChatSubmission {
      private static final Method METHOD = findClientChatSubmissionMethod();
   }

   private static Method findClientChatSubmissionMethod() {
      try {
         Class<?> connectionType = Minecraft.class.getMethod("getConnection").getReturnType();
         for (String name : List.of("sendChat", "sendChatMessage")) {
            try {
               Method method = connectionType.getMethod(name, String.class);
               if (method.getReturnType() == Void.TYPE) return method;
            } catch (ReflectiveOperationException ignored) { }
            try {
               Method method = connectionType.getDeclaredMethod(name, String.class);
               if (method.getReturnType() == Void.TYPE) {
                  method.setAccessible(true);
                  return method;
               }
            } catch (ReflectiveOperationException ignored) { }
         }
      } catch (ReflectiveOperationException ignored) { }
      return null;
   }

   private static Entity localEntityArgument(LuaValue value, String operation) {
      LuaValue raw = value.istable() ? value.checktable().rawget("__entity") : value;
      if (raw.isuserdata(Entity.class)) {
         return (Entity) raw.touserdata(Entity.class);
      }
      throw new LuaError("[MLua] " + operation + " requires a live Entity instance.");
   }

   private static void addLocalChatMessage(Object chat, Component component) throws ReflectiveOperationException {
      Method method = CHAT_MESSAGE_ADD_METHOD.get(chat.getClass());
      Class<?>[] parameters = method.getParameterTypes();
      Object[] arguments = parameters.length == 1 ? new Object[]{component}
         : new Object[]{component, null, defaultMessageSource(parameters[2]), null};
      method.invoke(chat, arguments);
   }

   private static Object defaultMessageSource(Class<?> sourceType) {
      if (!sourceType.isEnum()) return null;
      Object[] values = sourceType.getEnumConstants();
      for (Object value : values) {
         String name = ((Enum<?>) value).name();
         if (name.equalsIgnoreCase("SYSTEM") || name.equalsIgnoreCase("SYSTEM_MESSAGE")) return value;
      }
      return values.length == 0 ? null : values[0];
   }

}
