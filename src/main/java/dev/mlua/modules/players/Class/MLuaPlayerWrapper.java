package dev.mlua.modules.players.Class;

import com.mojang.authlib.GameProfile;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import dev.mlua.core.service.MLuaServiceProperties;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.math.MLuaVector;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.mcapi.MCApiRuntime;
import dev.mlua.minecraft.binding.MLuaContainer;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.binding.MLuaLevelWrapper;
import dev.mlua.minecraft.binding.MLuaMinecraftEnums;
import dev.mlua.modules.effects.Service.MLuaPotionEffectsService;
import dev.mlua.minecraft.client.MLuaCameraController;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.runtime.data.MLuaDataManager;
import dev.mlua.mcapi.container.MCAbstractContainerMenu;
import dev.mlua.mcapi.core.MCAABB;
import dev.mlua.mcapi.core.MCComponent;
import dev.mlua.mcapi.core.MCMutableComponent;
import dev.mlua.mcapi.core.MCResourceLocation;
import dev.mlua.mcapi.core.MCVec3;
import dev.mlua.mcapi.entity.MCEntity;
import dev.mlua.mcapi.entity.MCLivingEntity;
import dev.mlua.mcapi.entity.MCLocalPlayer;
import dev.mlua.mcapi.entity.MCPlayer;
import dev.mlua.mcapi.entity.MCServerPlayer;
import dev.mlua.mcapi.item.MCItem;
import dev.mlua.mcapi.item.MCItemStack;
import dev.mlua.mcapi.sound.MCSoundEvent;
import dev.mlua.mcapi.sound.MCSoundSource;
import dev.mlua.mcapi.world.MCGameType;
import dev.mlua.mc.impl.container.MCAbstractContainerMenuImpl;
import dev.mlua.mc.impl.container.MCContainerImpl;
import dev.mlua.mc.impl.core.MCComponentImpl;
import dev.mlua.mc.impl.core.MCVec3Impl;
import dev.mlua.mc.impl.entity.MCEntityImpl;
import dev.mlua.mc.impl.entity.MCLivingEntityImpl;
import dev.mlua.mc.impl.entity.MCLocalPlayerImpl;
import dev.mlua.mc.impl.entity.MCPlayerImpl;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.mc.impl.item.MCItemImpl;
import dev.mlua.mc.impl.item.MCItemStackImpl;
import dev.mlua.mc.impl.world.MCGameTypeImpl;
import dev.mlua.mc.impl.world.MCLevelImpl;
import java.lang.reflect.Method;
import java.util.WeakHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.lang.ref.WeakReference;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;

public final class MLuaPlayerWrapper {
   private static final Method PLAYER_GET_GAME_PROFILE = findPlayerGetGameProfile();

   private MLuaPlayerWrapper() {
   }

   public static LuaTable classTable() {
      return classTable(MLuaRuntime.get());
   }

   public static LuaTable classTable(MLuaRegistration registration) {
      if (registration == null) throw new IllegalStateException("Runtime registration is required for Player instances");
      LuaTable classTable = registration.classTable("Player");
      if (classTable == null) throw new IllegalStateException("Lua class is not registered: Player");
      return classTable;
   }

   public static LuaTable playerEntityTable(net.minecraft.world.entity.player.Player player,
                                            MLuaRegistration registration) {
      if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
         return playerTable(MCServerPlayerImpl.fromReal(serverPlayer), registration);
      }
      if (player instanceof LocalPlayer localPlayer) {
         return localPlayerTable(MCLocalPlayerImpl.fromReal(localPlayer), registration);
      }
      if (player instanceof AbstractClientPlayer clientPlayer) {
         return clientPlayerTable(clientPlayer, registration);
      }
      return clientPlayerTable(player, registration);
   }

   public static LuaTable playerTable(MCServerPlayer player) {
      net.minecraft.world.entity.Entity raw = ((MCEntityImpl) player).inner();
      return playerTable(player, MLuaRuntime.registrationForLevel(raw.level()));
   }

   public static LuaTable playerTable(MCServerPlayer player, MLuaRegistration registration) {
      classTable(registration);
      net.minecraft.server.level.ServerPlayer raw = (net.minecraft.server.level.ServerPlayer) ((MCEntityImpl) player).inner();
      LuaTable cachedPlayer = MLuaEntityWrapper.cachedPlayerTable(raw, registration);
      if (cachedPlayer != null) {
         LuaValue cachedEntity = cachedPlayer.rawget("__entity");
         if (cachedEntity.isuserdata(net.minecraft.world.entity.Entity.class)
               && cachedEntity.touserdata(net.minecraft.world.entity.Entity.class) == raw) {
            return cachedPlayer;
         }
      }
      LuaTable table = MLuaEntityWrapper.livingEntityTable((net.minecraft.world.entity.LivingEntity) raw, registration);
      registration.attachClassInstance(table, "Player", "LivingEntity");
      table.rawset("__entity", LuaValue.userdataOf(raw));
      MLuaClassMethods.setInstanceMethod(table, "GetDisplayName", luaFn(() -> {
         return LuaValue.valueOf(!MLuaEntityWrapper.entityDisplayName(player).isBlank() ? MLuaEntityWrapper.entityDisplayName(player) : playerName(player));
      }), "Returns this player's display name.");
      MLuaClassMethods.setInstanceMethod(table, "SendMessage", varArg((args) -> {
         String rawMsg = args.checkjstring(2);
         net.minecraft.network.chat.Component component;
         try {
            component = net.minecraft.network.chat.Component.literal(MCComponent.fromJson(rawMsg).getString());
         } catch (Exception e) {
            component = net.minecraft.network.chat.Component.literal(rawMsg);
         }
         raw.sendSystemMessage(component);
         return LuaValue.NONE;
      }), "Sends a chat message to this player. On the client, displays the message locally.");
      MLuaClassMethods.setInstanceMethod(table, "SendActionBar", varArg((args) -> {
         MCMutableComponent msg;
         try {
            msg = MCComponent.fromJson(args.checkjstring(2));
         } catch (Exception var4) {
            msg = MCComponent.literal(args.checkjstring(2));
         }
         player.displayClientMessage(msg, true);
         return LuaValue.NONE;
      }), "Sends an action bar message to this player.");
      MLuaClassMethods.setInstanceMethod(table, "SendTitle", varArg((args) -> {
         if (player.getConnection() != null) {
            String rawText = args.checkjstring(2);
            net.minecraft.network.chat.Component component;
            try {
               component = net.minecraft.network.chat.Component.literal(MCComponent.fromJson(rawText).getString());
            } catch (Exception e) {
               component = net.minecraft.network.chat.Component.literal(rawText);
            }
            raw.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(component));
         }

         return LuaValue.NONE;
      }), "Displays a title to this player.");
      MLuaClassMethods.setInstanceMethod(table, "GetHealth", luaFn(() -> {
         return LuaValue.valueOf((double)player.getHealth());
      }), "Returns this player's current health.");
      MLuaClassMethods.setInstanceMethod(table, "SetHealth", varArg((args) -> {
         player.setHealth((float)args.checkdouble(2));
         return LuaValue.NONE;
      }), "Sets this player's current health.");
      MLuaClassMethods.setInstanceMethod(table, "GetMaxHealth", luaFn(() -> {
         return LuaValue.valueOf((double)player.getMaxHealth());
      }), "Returns this player's maximum health.");
      MLuaClassMethods.setInstanceMethod(table, "GetArmor", luaFn(() -> {
         return LuaValue.valueOf(player.getArmorValue());
      }), "Returns this player's armor value.");
      MLuaClassMethods.setInstanceMethod(table, "GetFood", luaFn(() -> {
         return LuaValue.valueOf(player.getFoodData().getFoodLevel());
      }), "Returns this player's food level.");
      MLuaClassMethods.setInstanceMethod(table, "SetFood", varArg((args) -> {
         player.getFoodData().setFoodLevel(args.checkint(2));
         return LuaValue.NONE;
      }), "Sets this player's food level.");
      MLuaClassMethods.setInstanceMethod(table, "GetFoodLevel", luaFn(() -> {
         return LuaValue.valueOf(player.getFoodData().getFoodLevel());
      }), "Returns this player's food level.");
      MLuaClassMethods.setInstanceMethod(table, "SetFoodLevel", varArg((args) -> {
         player.getFoodData().setFoodLevel(args.checkint(2));
         return LuaValue.NONE;
      }), "Sets this player's food level.");
      MLuaClassMethods.setInstanceMethod(table, "GetSaturation", luaFn(() -> {
         return LuaValue.valueOf((double)player.getFoodData().getSaturationLevel());
      }), "Returns this player's food saturation.");
      MLuaClassMethods.setInstanceMethod(table, "SetSaturation", varArg((args) -> {
         player.getFoodData().setSaturation((float)args.checkdouble(2));
         return LuaValue.NONE;
      }), "Sets this player's food saturation.");
      MLuaClassMethods.setInstanceMethod(table, "GetExperienceLevel", luaFn(() -> {
         return LuaValue.valueOf(player.experienceLevel());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetExperienceProgress", luaFn(() -> {
         return LuaValue.valueOf((double)player.experienceProgress());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GiveExperience", varArg((args) -> {
         player.giveExperiencePoints(args.checkint(2));
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GiveExperienceLevels", varArg((args) -> {
         player.giveExperienceLevels(args.checkint(2));
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "IsOnline", luaFn(() -> {
         return LuaValue.valueOf(player.getConnection() != null && player.isAlive());
      }));
      MLuaClassMethods.setInstanceMethod(table, "IsCreative", luaFn(() -> {
         return LuaValue.valueOf(player.isCreative());
      }));
      MLuaClassMethods.setInstanceMethod(table, "IsSpectator", luaFn(() -> {
         return LuaValue.valueOf(player.isSpectator());
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
      MLuaClassMethods.setInstanceMethod(table, "IsSwimming", luaFn(() -> {
         return LuaValue.valueOf(player.isSwimming());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetBlockPosition", luaFn(() -> {
         return positionTable((double)player.blockPosition().getX(), (double)player.blockPosition().getY(), (double)player.blockPosition().getZ());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetLookDirection", luaFn(() -> {
         MCVec3 look = MLuaEntityWrapper.lookAngle(player);
         return positionTable(look.x(), look.y(), look.z());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetPosition", luaFn(() -> {
         MCVec3 p = MLuaEntityWrapper.position(player);
         return positionTable(p.x(), p.y(), p.z());
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetPosition", varArg((args) -> {
         double[] pos = positionArgs(args, 2);
         player.teleportTo(pos[0], pos[1], pos[2]);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "Teleport", varArg((args) -> {
         double[] pos = positionArgs(args, 2);
         player.teleportTo(pos[0], pos[1], pos[2]);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetRotation", luaFn(() -> {
         return MLuaVector.vector2((double)player.getXRot(), (double)player.getYRot());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetRotationRadians", luaFn(() -> {
         return MLuaVector.vector2(Math.toRadians((double)player.getXRot()), Math.toRadians((double)player.getYRot()));
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetRotationRadians", varArg((args) -> {
         LuaValue value = args.arg(2);
         if (!value.istable()) {
            throw new LuaError("[MLua] SetRotationRadians expects Vector2(pitch, yaw)");
         } else {
            LuaTable rotation = value.checktable();
            double pitch = rotation.get("X").isnumber() ? rotation.get("X").todouble() : rotation.get("x").checkdouble();
            double yaw = rotation.get("Y").isnumber() ? rotation.get("Y").todouble() : rotation.get("y").checkdouble();
            MLuaEntityWrapper.applyRotation(player, MLuaVector.vector2(Math.toDegrees(pitch), Math.toDegrees(yaw)));
            return LuaValue.NONE;
         }
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetRotation", varArg((args) -> {
         player.setYRot((float)args.checkdouble(2));
         player.setXRot((float)args.checkdouble(3));
         return LuaValue.NONE;
      }));
      addCFrameMethods(table, player, registration);
      MLuaClassMethods.setInstanceMethod(table, "GetEyeCFrame", luaFn(() -> {
         MCVec3 look = MLuaEntityWrapper.lookAngle(player);
         return MLuaCFrame.fromEntity(player.getX(), raw.getEyeY(), player.getZ(), look.x(), look.y(), look.z()).toLua();
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetCameraCFrame", varArg((args) -> {
         throw new LuaError("[MLua] player:GetCameraCFrame() is client-side only. Use Minecraft:GetService(\"Camera\") in a .client.lua script.");
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetVelocity", luaFn(() -> {
         MCVec3 v = player.getDeltaMovement();
         return positionTable(v.x(), v.y(), v.z());
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetVelocity", varArg((args) -> {
         double[] vel = positionArgs(args, 2);
         player.setDeltaMovement(MCVec3.of(vel[0], vel[1], vel[2]));
         player.setHurtMarked(true);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "DistanceTo", varArg((args) -> {
         double[] other = positionArgs(args, 2);
         MCVec3 p = MLuaEntityWrapper.position(player);
         double dx = p.x() - other[0];
         double dy = p.y() - other[1];
         double dz = p.z() - other[2];
         return LuaValue.valueOf(Math.sqrt(dx * dx + dy * dy + dz * dz));
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetGameMode", luaFn(() -> {
         return getGameModeLua(player);
      }), "Returns this player's current game mode.");
      MLuaClassMethods.setInstanceMethod(table, "SetGameMode", varArg((args) -> {
         MCGameType type = MCGameType.byName(MLuaMinecraftEnums.resolveGameMode(args.arg(2)).getName());
         player.setGameMode(type);
         return LuaValue.NONE;
      }), "Changes this player's game mode. This method is server-side only.");
      MLuaClassMethods.setInstanceMethod(table, "HasPermission", varArg((args) -> {
         return LuaValue.valueOf(player.hasPermissions(args.checkint(2)));
      }));
      MLuaClassMethods.setInstanceMethod(table, "IsOperator", luaFn(() -> {
         return LuaValue.valueOf(player.hasPermissions(4));
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetSelectedSlot", luaFn(() -> {
         return LuaValue.valueOf(player.getInventory().getSelected() + 1);
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetSelectedSlot", varArg((args) -> {
         int slot = args.checkint(2) - 1;
         if (slot < 0 || slot >= 9) {
            throw new LuaError("[MLua] Selected slot must be between 1 and 9.");
         }
         player.getInventory().setSelected(slot);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetHeldItem", varArg((args) -> {
         return heldItem(player.getMainHandItem(), registration);
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetOffhandItem", varArg((args) -> {
         return heldItem(player.getOffhandItem(), registration);
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetViewMode", varArg((args) -> {
         throw new LuaError("[MLua] Player.ViewMode is client-side only for server player tables.");
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetViewMode", varArg((args) -> {
         throw new LuaError("[MLua] Player.ViewMode can only be written from a .client.lua script.");
      }));
      MLuaClassMethods.setInstanceMethod(table, "GiveItem", varArg((args) -> {
         LuaValue arg = args.arg(2);
         if (arg.istable()) {
            LuaValue idVal = arg.get("Id");
            LuaValue countVal = arg.get("Count");
            if (idVal.isnil()) {
               throw new LuaError("[MLua] Item table missing Id");
            }

            String idx = idVal.tojstring();
            int count = countVal.isnil() ? 1 : countVal.checkint();
            net.minecraft.world.item.Item item = (net.minecraft.world.item.Item) MCApiRuntime.lookupRegistry("item", idx);
            if (item == null) {
               throw new LuaError("[MLua] Unknown item: " + idx);
            }

            player.addItem(MCItemStackImpl.fromReal(new net.minecraft.world.item.ItemStack(item, count)));
         } else {
            String id = arg.checkjstring();
            int countx = args.optint(3, 1);
            net.minecraft.world.item.Item itemx = (net.minecraft.world.item.Item) MCApiRuntime.lookupRegistry("item", id);
            if (itemx == null) {
               throw new LuaError("[MLua] Unknown item: " + id);
            }

            player.addItem(MCItemStackImpl.fromReal(new net.minecraft.world.item.ItemStack(itemx, countx)));
         }

         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "ClearInventory", luaFn(() -> {
         player.getInventory().clearContent();
         return LuaValue.NONE;
      }));
      LuaTable inventory = attachInventoryMethods(table, player, true, true, registration);
      MLuaClassMethods.setInstanceMethod(table, "SetSlot", varArg((args) -> {
         int slot = args.checkint(2) - 1;
         LuaValue itemVal = args.arg(3);
         if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
            throw new LuaError("[MLua] Inventory slot must be between 1 and " + player.getInventory().getContainerSize() + ".");
         }
         if (itemVal.isnil()) {
            player.getInventory().setItem(slot, MCItemStack.EMPTY);
         } else {
            player.getInventory().setItem(slot, MCItemStackImpl.fromReal(MLuaItemWrapper.toItemStack(itemVal)));
         }
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetHotbarSlot", varArg((args) -> {
         return invokeInstance(inventory, "SetHotbarSlot", args.arg(2), args.arg(3));
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetArmorSlot", varArg((args) -> {
         return invokeInstance(inventory, "SetArmorSlot", args.arg(2), args.arg(3));
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetOffhandSlot", varArg((args) -> {
         return invokeInstance(inventory, "SetOffhandSlot", args.arg(2));
      }));
      bindLevelMethods(table, ((MCLevelImpl) player.level()).inner(), registration);
      MLuaClassMethods.setInstanceMethod(table, "GetNearbyEntities", varArg((args) -> {
         double radius = args.checkdouble(2);
         MCAABB box = player.getBoundingBox().inflate(radius);
         LuaTable list = new LuaTable();
         int i = 1;
         for (MCEntity e : player.level().getEntities(player, box)) {
            if (e instanceof MCLivingEntity living) {
               list.set(i++, MLuaEntityWrapper.entityTable((net.minecraft.world.entity.LivingEntity)((MCEntityImpl) living).inner(), registration));
            }
         }
         return list;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetNearbyPlayers", varArg((args) -> {
         double radius = args.checkdouble(2);
         MCAABB box = player.getBoundingBox().inflate(radius);
         LuaTable list = new LuaTable();
         int i = 1;
         net.minecraft.world.level.Level rawLevel = ((MCLevelImpl) player.level()).inner();
         net.minecraft.world.phys.AABB rawBox = new net.minecraft.world.phys.AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
         for (net.minecraft.server.level.ServerPlayer e : rawLevel.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class, rawBox, (p) -> {
            return p != raw;
         })) {
            list.set(i++, playerTable(MCServerPlayerImpl.fromReal(e), registration));
         }
         return list;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GiveEffect", varArg((args) -> {
         return MLuaPotionEffectsService.giveEffect((net.minecraft.world.entity.LivingEntity)((MCEntityImpl) player).inner(), args);
      }));
      MLuaClassMethods.setInstanceMethod(table, "RemoveEffect", varArg((args) -> {
         net.minecraft.world.entity.LivingEntity rawEntity = (net.minecraft.world.entity.LivingEntity)((MCEntityImpl) player).inner();
         //--version 1.21.1,26.1
         return LuaValue.valueOf(rawEntity.removeEffect(MLuaPotionEffectsService.resolveHolderFromLua(args.arg(2))));
         //--endversion
         //--version 1.20.1
         return LuaValue.valueOf(rawEntity.removeEffect(MLuaPotionEffectsService.resolveFromLua(args.arg(2))));
         //--endversion
      }));
      MLuaClassMethods.setInstanceMethod(table, "HasEffect", varArg((args) -> {
         net.minecraft.world.entity.LivingEntity rawEntity = (net.minecraft.world.entity.LivingEntity)((MCEntityImpl) player).inner();
         //--version 1.21.1,26.1
         return LuaValue.valueOf(rawEntity.hasEffect(MLuaPotionEffectsService.resolveHolderFromLua(args.arg(2))));
         //--endversion
         //--version 1.20.1
         return LuaValue.valueOf(rawEntity.hasEffect(MLuaPotionEffectsService.resolveFromLua(args.arg(2))));
         //--endversion
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetEffects", luaFn(() -> {
         return MLuaPotionEffectsService.effectsTable((net.minecraft.world.entity.LivingEntity)((MCEntityImpl) player).inner());
      }));
      MLuaClassMethods.setInstanceMethod(table, "ClearEffects", luaFn(() -> {
         net.minecraft.world.entity.LivingEntity rawEntity = (net.minecraft.world.entity.LivingEntity)((MCEntityImpl) player).inner();
         rawEntity.removeAllEffects();
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "PlaySound", varArg((args) -> {
         MCResourceLocation loc = MCResourceLocation.parse(args.checkjstring(2));
         net.minecraft.resources.ResourceLocation rawLoc = MLuaResourceLocations.parse(loc.toString());
         //--version 26.1
         net.minecraft.sounds.SoundEvent rawSound = BuiltInRegistries.SOUND_EVENT.get(rawLoc).map(reference -> reference.value()).orElse(null);
         //--endversion
         //--version 1.20.1,1.21.1
         net.minecraft.sounds.SoundEvent rawSound = BuiltInRegistries.SOUND_EVENT.get(rawLoc);
         //--endversion
         if (rawSound == null) {
            throw new LuaError("[MLua] Unknown sound: " + args.checkjstring(2));
         }
         float volume = (float)args.optdouble(3, 1.0);
         float pitch = (float)args.optdouble(4, 1.0);
         player.playNotifySound(MCSoundEvent.create(loc), MCSoundSource.PLAYERS, volume, pitch);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetPersistentData", luaFn(() -> MLuaDataManager.playerPersistentData(raw, true)));
      MLuaClassMethods.setInstanceMethod(table, "GetSyncedData", luaFn(() -> MLuaDataManager.playerSyncedData(raw)));
      MLuaClassMethods.setInstanceMethod(table, "SetData", varArg((args) -> {
         LuaTable persistent = MLuaDataManager.playerPersistentData(raw, true);
         persistent.get("Set").call(persistent, LuaValue.valueOf(args.checkjstring(2)), args.arg(3));
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetData", varArg((args) -> {
         LuaTable persistent = MLuaDataManager.playerPersistentData(raw, true);
         return persistent.get("Get").call(persistent, LuaValue.valueOf(args.checkjstring(2)));
      }));
      MLuaClassMethods.setInstanceMethod(table, "RemoveData", varArg((args) -> {
         LuaTable persistent = MLuaDataManager.playerPersistentData(raw, true);
         persistent.get("Remove").call(persistent, LuaValue.valueOf(args.checkjstring(2)));
         return LuaValue.NONE;
      }));
      LuaTable attached = MLuaEntityWrapper.attachEntityProperties(table);
      MLuaEntityWrapper.cachePlayerTable(raw, registration, attached);
      return attached;
   }

   public static LuaTable clientPlayerTable(AbstractClientPlayer player, MLuaRegistration registration) {
      return clientPlayerTable((net.minecraft.world.entity.player.Player) player, registration);
   }

   private static LuaTable clientPlayerTable(net.minecraft.world.entity.player.Player player,
                                             MLuaRegistration registration) {
      classTable(registration);
      if (player instanceof LocalPlayer local) {
         return localPlayerTable(MCLocalPlayerImpl.fromReal(local), registration);
      }
      LuaTable cachedPlayer = MLuaEntityWrapper.cachedPlayerTable(player, registration);
      if (cachedPlayer != null) {
         LuaValue cachedEntity = cachedPlayer.rawget("__entity");
         if (cachedEntity.isuserdata(net.minecraft.world.entity.Entity.class)
               && cachedEntity.touserdata(net.minecraft.world.entity.Entity.class) == player) {
            return cachedPlayer;
         }
      }
      LuaTable table = MLuaEntityWrapper.livingEntityTable(player, registration);
      registration.attachClassInstance(table, "Player", "LivingEntity");
      table.rawset("__entity", LuaValue.userdataOf(player));
      MLuaClassMethods.setInstanceMethod(table, "GetIsLocalPlayer", luaFn(() -> LuaValue.FALSE));
      MLuaClassMethods.setInstanceMethod(table, "GetDisplayName", luaFn(() -> LuaValue.valueOf(player.getDisplayName().getString())));
      MLuaClassMethods.setInstanceMethod(table, "IsOnline", luaFn(() -> LuaValue.TRUE));
      MLuaClassMethods.setInstanceMethod(table, "IsCreative", luaFn(() -> LuaValue.valueOf(player.isCreative())));
      MLuaClassMethods.setInstanceMethod(table, "IsSpectator", luaFn(() -> LuaValue.valueOf(player.isSpectator())));
      MLuaClassMethods.setInstanceMethod(table, "GetGameMode", luaFn(() -> clientGameMode(player)));
      MLuaClassMethods.setInstanceMethod(table, "GetFood", luaFn(() -> LuaValue.NIL));
      MLuaClassMethods.setInstanceMethod(table, "GetFoodLevel", luaFn(() -> LuaValue.NIL));
      MLuaClassMethods.setInstanceMethod(table, "SetFood", varArg(args -> unavailableRemotePlayerState("food")));
      MLuaClassMethods.setInstanceMethod(table, "SetFoodLevel", varArg(args -> unavailableRemotePlayerState("food")));
      MLuaClassMethods.setInstanceMethod(table, "GetSaturation", luaFn(() -> LuaValue.NIL));
      MLuaClassMethods.setInstanceMethod(table, "SetSaturation", varArg(args -> unavailableRemotePlayerState("food")));
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
      MLuaClassMethods.setInstanceMethod(table, "IsSwimming", luaFn(() -> LuaValue.valueOf(player.isSwimming())));
      MLuaClassMethods.setInstanceMethod(table, "GetEffects", luaFn(() -> MLuaPotionEffectsService.effectsTable(player)));
      MLuaClassMethods.setInstanceMethod(table, "GetBlockPosition", luaFn(() ->
         positionTable(player.blockPosition().getX(), player.blockPosition().getY(), player.blockPosition().getZ())));
      MLuaClassMethods.setInstanceMethod(table, "GetLookDirection", luaFn(() -> {
         MCVec3 look = MLuaEntityWrapper.lookAngle(new MCEntityImpl(player));
         return positionTable(look.x(), look.y(), look.z());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetHeldItem", luaFn(() -> {
         var stack = player.getMainHandItem();
         return stack.isEmpty() ? LuaValue.NIL : MLuaItemWrapper.itemTable(stack, registration);
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetOffhandItem", luaFn(() -> {
         var stack = player.getOffhandItem();
         return stack.isEmpty() ? LuaValue.NIL : MLuaItemWrapper.itemTable(stack, registration);
      }));
      attachInventoryMethods(table, MCPlayerImpl.fromReal(player), false, false, registration);
      MLuaClassMethods.setInstanceMethod(table, "GetOpenContainer", luaFn(() -> LuaValue.NIL));
      MLuaClassMethods.setInstanceMethod(table, "GetOpenContainerTitle", luaFn(() -> LuaValue.NIL));
      MLuaClassMethods.setInstanceMethod(table, "GetOpenContainerSize", luaFn(() -> LuaValue.valueOf(0)));
      MLuaEntityWrapper.attachEntityProperties(table);
      MLuaEntityWrapper.cachePlayerTable(player, registration, table);
      return table;
   }

   public static LuaTable localPlayerTable(MCLocalPlayer player) {
      net.minecraft.world.entity.Entity raw = ((MCEntityImpl) player).inner();
      return localPlayerTable(player, MLuaRuntime.registrationForLevel(raw.level()));
   }

   public static LuaTable localPlayerTable(MCLocalPlayer player, MLuaRegistration registration) {
      classTable(registration);
      net.minecraft.client.player.LocalPlayer raw = (net.minecraft.client.player.LocalPlayer) ((MCEntityImpl) player).inner();
      LuaTable cachedPlayer = MLuaEntityWrapper.cachedPlayerTable(raw, registration);
      if (cachedPlayer != null) {
         LuaValue cachedEntity = cachedPlayer.rawget("__entity");
         if (cachedEntity.isuserdata(net.minecraft.world.entity.Entity.class)
               && cachedEntity.touserdata(net.minecraft.world.entity.Entity.class) == raw) {
            return cachedPlayer;
         }
      }
      LuaTable table = MLuaEntityWrapper.livingEntityTable(raw, registration);
      registration.attachClassInstance(table, "Player", "LivingEntity");
      table.rawset("__entity", LuaValue.userdataOf(raw));
      table.rawset("__is_local_player", LuaValue.TRUE);
      MLuaClassMethods.setInstanceMethod(table, "GetDisplayName", luaFn(() -> {
         return LuaValue.valueOf(!MLuaEntityWrapper.entityDisplayName(player).isBlank() ? MLuaEntityWrapper.entityDisplayName(player) : playerName(player));
      }), "Returns this player's display name.");
      MLuaClassMethods.setInstanceMethod(table, "SendMessage", varArg((args) -> {
         String rawMsg = args.checkjstring(2);
         net.minecraft.network.chat.Component component;
         try {
            component = net.minecraft.network.chat.Component.literal(MCComponent.fromJson(rawMsg).getString());
         } catch (Exception var6) {
            component = net.minecraft.network.chat.Component.literal(rawMsg);
         }

         try {
            net.minecraft.client.Minecraft minecraft = MLuaMinecraftClient.instance();
            if (minecraft.gui != null && minecraft.gui.getChat() != null) {
               minecraft.gui.getChat().addMessage(component);
            } else {
               player.displayClientMessage(MCComponentImpl.fromReal(component), false);
            }
         } catch (Exception var5) {
            player.displayClientMessage(MCComponentImpl.fromReal(component), false);
         }
         return LuaValue.NONE;
      }), "Sends a chat message to this player. On the client, displays the message locally.");
      MLuaClassMethods.setInstanceMethod(table, "SendActionBar", varArg((args) -> {
         net.minecraft.network.chat.Component component;
         try {
            component = net.minecraft.network.chat.Component.literal(MCComponent.fromJson(args.checkjstring(2)).getString());
         } catch (Exception var5) {
            component = net.minecraft.network.chat.Component.literal(args.checkjstring(2));
         }

         try {
            MLuaMinecraftClient.showActionBar(component);
         } catch (Exception var4) {
            player.displayClientMessage(MCComponentImpl.fromReal(component), true);
         }
         return LuaValue.NONE;
      }), "Sends an action bar message to this player.");
      MLuaClassMethods.setInstanceMethod(table, "GetHealth", luaFn(() -> {
         return LuaValue.valueOf((double)player.getHealth());
      }), "Returns this player's current health.");
      MLuaClassMethods.setInstanceMethod(table, "GetMaxHealth", luaFn(() -> {
         return LuaValue.valueOf((double)player.getMaxHealth());
      }), "Returns this player's maximum health.");
      MLuaClassMethods.setInstanceMethod(table, "GetArmor", luaFn(() -> {
         return LuaValue.valueOf(player.getArmorValue());
      }), "Returns this player's armor value.");
      MLuaClassMethods.setInstanceMethod(table, "GetFood", luaFn(() -> {
         return LuaValue.valueOf(player.getFoodData().getFoodLevel());
      }), "Returns this player's food level.");
      MLuaClassMethods.setInstanceMethod(table, "GetFoodLevel", luaFn(() -> {
         return LuaValue.valueOf(player.getFoodData().getFoodLevel());
      }), "Returns this player's food level.");
      MLuaClassMethods.setInstanceMethod(table, "SetFood", varArg((args) -> {
         player.getFoodData().setFoodLevel(args.checkint(2));
         return LuaValue.NONE;
      }), "Sets this player's food level.");
      MLuaClassMethods.setInstanceMethod(table, "SetFoodLevel", varArg((args) -> {
         player.getFoodData().setFoodLevel(args.checkint(2));
         return LuaValue.NONE;
      }), "Sets this player's food level.");
      MLuaClassMethods.setInstanceMethod(table, "GetSaturation", luaFn(() -> {
         return LuaValue.valueOf((double)player.getFoodData().getSaturationLevel());
      }), "Returns this player's food saturation.");
      MLuaClassMethods.setInstanceMethod(table, "SetSaturation", varArg((args) -> {
         player.getFoodData().setSaturation((float)args.checkdouble(2));
         return LuaValue.NONE;
      }), "Sets this player's food saturation.");
      MLuaClassMethods.setInstanceMethod(table, "GetExperienceLevel", luaFn(() -> {
         return LuaValue.valueOf(player.experienceLevel());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetExperienceProgress", luaFn(() -> {
         return LuaValue.valueOf((double)player.experienceProgress());
      }));
      MLuaClassMethods.setInstanceMethod(table, "IsCreative", luaFn(() -> {
         return LuaValue.valueOf(player.isCreative());
      }));
      MLuaClassMethods.setInstanceMethod(table, "IsSpectator", luaFn(() -> {
         return LuaValue.valueOf(player.isSpectator());
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
      MLuaClassMethods.setInstanceMethod(table, "IsSwimming", luaFn(() -> {
         return LuaValue.valueOf(player.isSwimming());
      }));
      MLuaClassMethods.setInstanceMethod(table, "IsOnline", luaFn(() -> {
         return LuaValue.valueOf(player.isAlive());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetBlockPosition", luaFn(() -> {
         return positionTable((double)player.blockPosition().getX(), (double)player.blockPosition().getY(), (double)player.blockPosition().getZ());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetLookDirection", luaFn(() -> {
         MCVec3 look = MLuaEntityWrapper.lookAngle(player);
         return positionTable(look.x(), look.y(), look.z());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetPosition", luaFn(() -> {
         MCVec3 p = MLuaEntityWrapper.position(player);
         return positionTable(p.x(), p.y(), p.z());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetRotation", luaFn(() -> {
         return MLuaVector.vector2((double)player.getXRot(), (double)player.getYRot());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetRotationRadians", luaFn(() -> {
         return MLuaVector.vector2(Math.toRadians((double)player.getXRot()), Math.toRadians((double)player.getYRot()));
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetRotationRadians", varArg((args) -> {
         LuaValue value = args.arg(2);
         if (!value.istable()) {
            throw new LuaError("[MLua] SetRotationRadians expects Vector2(pitch, yaw)");
         } else {
            LuaTable rotation = value.checktable();
            double pitch = rotation.get("X").isnumber() ? rotation.get("X").todouble() : rotation.get("x").checkdouble();
            double yaw = rotation.get("Y").isnumber() ? rotation.get("Y").todouble() : rotation.get("y").checkdouble();
            MLuaEntityWrapper.applyRotation(player, MLuaVector.vector2(Math.toDegrees(pitch), Math.toDegrees(yaw)));
            return LuaValue.NONE;
         }
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetRotation", varArg((args) -> {
         MLuaEntityWrapper.applyRotation(player, args.arg(2));
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetVelocity", luaFn(() -> {
         MCVec3 v = player.getDeltaMovement();
         return positionTable(v.x(), v.y(), v.z());
      }));
      addCFrameMethods(table, player, registration);
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
         MLuaCameraController.setViewMode(args.checkjstring(2));
         return LuaValue.NONE;
      }));
      bindLevelMethods(table, raw.clientLevel, registration);
      MLuaClassMethods.setInstanceMethod(table, "GetPersistentData", luaFn(() -> {
         return MLuaDataManager.playerPersistentData(raw, false);
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetSyncedData", luaFn(() -> {
         return MLuaDataManager.playerSyncedData(raw);
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetGameMode", luaFn(() -> {
         net.minecraft.client.Minecraft mc = MLuaMinecraftClient.instance();
         if (mc.gameMode != null) {
            try {
               return MLuaMinecraftEnums.gameModeOf(mc.gameMode.getPlayerMode());
            } catch (Exception var2) {
               return LuaValue.valueOf("unknown");
            }
         } else {
            return LuaValue.valueOf("unknown");
         }
      }), "Returns this player's current game mode.");
      MLuaClassMethods.setInstanceMethod(table, "GetHeldItem", luaFn(() -> {
         return heldItem(player.getMainHandItem(), registration);
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetOffhandItem", luaFn(() -> {
         return heldItem(player.getOffhandItem(), registration);
      }));
      attachInventoryMethods(table, player, false, false, registration);
      MLuaClassMethods.setInstanceMethod(table, "GetSelectedSlot", luaFn(() -> {
         return LuaValue.valueOf(player.getInventory().getSelected() + 1);
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetSelectedSlot", varArg((args) -> {
         int slot = args.checkint(2) - 1;
         if (slot < 0 || slot >= 9) {
            throw new LuaError("[MLua] Selected slot must be between 1 and 9.");
         }
         player.getInventory().setSelected(slot);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetEffects", luaFn(() -> {
         return MLuaPotionEffectsService.effectsTable((net.minecraft.world.entity.LivingEntity)((MCEntityImpl) player).inner());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetNearbyEntities", varArg((args) -> {
         double radius = args.checkdouble(2);
         MCAABB box = player.getBoundingBox().inflate(radius);
         LuaTable list = new LuaTable();
         int i = 1;
         for (MCEntity e : player.level().getEntities(player, box)) {
            if (e instanceof MCLivingEntity living) {
               list.set(i++, MLuaEntityWrapper.entityTable((net.minecraft.world.entity.LivingEntity)((MCEntityImpl) living).inner(), registration));
            }
         }
         return list;
      }));
      LuaTable attached = MLuaEntityWrapper.attachEntityProperties(table);
      MLuaEntityWrapper.cachePlayerTable(raw, registration, attached);
      return attached;
   }

   public static LuaTable positionTable(double x, double y, double z) {
      return MLuaVector.vector3(x, y, z);
   }

   private static void addCFrameMethods(LuaTable table, MCEntity base, MLuaRegistration registration) {
      MLuaClassMethods.setInstanceMethod(table, "GetCFrame", luaFn(() -> {
         return MLuaEntityWrapper.cframe(base).toLua();
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetCFrame", varArg((args) -> {
         MLuaEntityWrapper.applyCFrame(base, MLuaCFrame.fromLua(args.arg(2)));
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetLookVector", luaFn(() -> {
         MCVec3 look = MLuaEntityWrapper.lookAngle(base);
         return positionTable(look.x(), look.y(), look.z());
      }));
      MLuaClassMethods.setInstanceMethod(table, "LookAt", varArg((args) -> {
         double[] target = MLuaEntityWrapper.targetPosition(args.arg(2));
         MCVec3 p = MLuaEntityWrapper.position(base);
         MLuaEntityWrapper.applyCFrame(base, MLuaCFrame.lookAt(p.x(), p.y(), p.z(), target[0], target[1], target[2]));
         return LuaValue.NONE;
      }));
   }

    public static double[] positionArgs(Varargs args, int index) {
      LuaValue first = args.arg(index);
      if (first.istable()) {
         LuaTable vector = first.checktable();
         return new double[]{vector.get("X").optdouble(vector.get("x").checkdouble()), vector.get("Y").optdouble(vector.get("y").checkdouble()), vector.get("Z").optdouble(vector.get("z").checkdouble())};
      } else {
         return new double[]{args.checkdouble(index), args.checkdouble(index + 1), args.checkdouble(index + 2)};
      }
   }

   private static GameProfile gameProfile(MCPlayer player) {
      if (PLAYER_GET_GAME_PROFILE != null) {
         try {
            return (GameProfile)PLAYER_GET_GAME_PROFILE.invoke(((MCPlayerImpl) player).inner());
         } catch (Exception var2) {
         }
      }

      return new GameProfile(player.getUUID(), "");
   }

   private static String playerName(MCPlayer player) {
      GameProfile profile = gameProfile(player);
      String name = profile.getName();
      return name != null ? name : "";
   }

   private static Method findPlayerGetGameProfile() {
      Iterator var0 = List.of("getGameProfile", "getGameProfile").iterator();

      while(var0.hasNext()) {
         String name = (String)var0.next();

         try {
            Method method = net.minecraft.world.entity.player.Player.class.getMethod(name);
            if (method.getReturnType() == GameProfile.class) {
               method.setAccessible(true);
               return method;
            }
         } catch (NoSuchMethodException var4) {
         }
      }

      Method[] var5 = net.minecraft.world.entity.player.Player.class.getMethods();
      int var6 = var5.length;

      for(int var7 = 0; var7 < var6; ++var7) {
         Method method = var5[var7];
         if (method.getParameterCount() == 0 && method.getReturnType() == GameProfile.class) {
            method.setAccessible(true);
            return method;
         }
      }

      return null;
   }

   private static LuaValue getGameModeLua(MCServerPlayer player) {
      net.minecraft.server.level.ServerPlayer raw = (net.minecraft.server.level.ServerPlayer) ((MCEntityImpl) player).inner();
      return MLuaMinecraftEnums.gameModeOf(raw.gameMode.getGameModeForPlayer());
   }

   private static LuaValue clientGameMode(net.minecraft.world.entity.player.Player player) {
      net.minecraft.client.Minecraft minecraft = MLuaMinecraftClient.instance();
      if (minecraft.getConnection() == null) return LuaValue.valueOf("unknown");
      var playerInfo = minecraft.getConnection().getPlayerInfo(player.getUUID());
      if (playerInfo == null || playerInfo.getGameMode() == null) return LuaValue.valueOf("unknown");
      return MLuaMinecraftEnums.gameModeOf(playerInfo.getGameMode());
   }

   private static LuaValue unavailableRemotePlayerState(String state) {
      throw new LuaError("[MLua] " + state + " cannot be read or changed for a remote client player.");
   }

   private static LuaValue heldItem(MCItemStack stack, MLuaRegistration registration) {
      return (LuaValue)(stack.isEmpty() ? LuaValue.NIL : MLuaItemWrapper.itemTable(((MCItemStackImpl)stack).inner(), registration));
   }

   private static void bindLevelMethods(LuaTable table, Level level, MLuaRegistration registration) {
      MLuaClassMethods.setInstanceMethod(table, "GetWorld", luaFn(() -> MLuaLevelWrapper.levelTable(level, registration)));
      MLuaClassMethods.setInstanceMethod(table, "GetLevel", luaFn(() -> MLuaLevelWrapper.levelTable(level, registration)));
      MLuaClassMethods.setInstanceMethod(table, "GetDimension", luaFn(() -> LuaValue.valueOf(level.dimension().location().toString())));
   }

   private static LuaTable attachInventoryMethods(LuaTable table, MCPlayer player, boolean writable,
                                                  boolean includeMutationMethods, MLuaRegistration registration) {
      net.minecraft.world.entity.player.Player rawPlayer = (net.minecraft.world.entity.player.Player) ((MCEntityImpl) player).inner();
      net.minecraft.world.entity.player.Inventory rawInventory = rawPlayer.getInventory();
      dev.mlua.mcapi.container.MCContainer invContainer = MCContainerImpl.fromReal(rawInventory);
      LuaTable inventory = MLuaContainer.inventoryTable(invContainer, writable, registration,
         new MLuaContainer.InventoryLayout(Math.min(9, Math.max(0, rawInventory.getContainerSize() - 5)),
            rawInventory.getContainerSize() - 4, 4, rawInventory.getContainerSize()));
      LuaTable enderChest = MLuaContainer.containerTable(
         MCContainerImpl.fromReal(rawPlayer.getEnderChestInventory()), writable, registration, "Ender Chest");
      Map<Object, WeakReference<LuaTable>> openContainerInstances = new WeakHashMap<>();

      MLuaClassMethods.setInstanceMethod(table, "GetInventory", luaFn(() -> inventory));
      MLuaClassMethods.setInstanceMethod(table, "GetSlot", varArg((args) -> {
         int slot = args.checkint(2);
         int rawIndex = slot - 1;
         if (rawIndex < 0 || rawIndex >= rawInventory.getContainerSize()) {
            return LuaValue.NIL;
         }
         return containerSlot(inventory, slot);
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetInventorySize", luaFn(() -> LuaValue.valueOf(rawInventory.getContainerSize())));
      MLuaClassMethods.setInstanceMethod(table, "GetHotbar", luaFn(() -> {
         return invokeInstance(inventory, "GetHotbar");
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetArmorContents", luaFn(() -> {
         return invokeInstance(inventory, "GetArmorContents");
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetOffhandSlot", luaFn(() -> invokeInstance(inventory, "GetOffhandSlot")));
      MLuaClassMethods.setInstanceMethod(table, "GetEnderChest", luaFn(() -> enderChest));
      MLuaClassMethods.setInstanceMethod(table, "GetOpenContainer", luaFn(() -> openContainerInstance(openContainerInstances,
         rawPlayer.containerMenu, MCAbstractContainerMenuImpl.fromReal(rawPlayer.containerMenu), writable, registration)));
      MLuaClassMethods.setInstanceMethod(table, "GetOpenContainerTitle", luaFn(() -> LuaValue.valueOf(containerTitle(MCAbstractContainerMenuImpl.fromReal(rawPlayer.containerMenu)))));
      MLuaClassMethods.setInstanceMethod(table, "GetOpenContainerSize", luaFn(() -> LuaValue.valueOf(rawPlayer.containerMenu.slots.size())));
      MLuaClassMethods.setInstanceMethod(table, "GetIsLocalPlayer", luaFn(() -> LuaValue.valueOf(table.rawget("__is_local_player").toboolean())));
      if (includeMutationMethods) {
         MLuaClassMethods.setInstanceMethod(table, "ClearInventory", luaFn(() -> {
            rawInventory.clearContent();
            return LuaValue.NONE;
         }));
         MLuaClassMethods.setInstanceMethod(table, "SetInventory", varArg((args) -> {
            LuaValue inventoryValue = args.arg(2);
            if (!inventoryValue.istable()) {
               throw new LuaError("[MLua] SetInventory expects an array-style table of items or nil values.");
            }
            LuaTable inventoryTable = inventoryValue.checktable();
            int size = rawInventory.getContainerSize();
            for (int slot = 1; slot <= size; slot++) {
               LuaValue value = inventoryTable.get(slot);
               rawInventory.setItem(slot - 1, value.isnil() ? net.minecraft.world.item.ItemStack.EMPTY : MLuaItemWrapper.toItemStack(value));
            }
            return LuaValue.NONE;
         }));
      }
      return inventory;
   }

   private static LuaValue containerSlot(LuaTable inventory, int index) {
      LuaValue getSlot = inventory.get("GetSlot");
      return getSlot.invoke(LuaValue.varargsOf(new LuaValue[]{inventory, LuaValue.valueOf(index)})).arg1();
   }

   private static LuaValue invokeInstance(LuaTable instance, String methodName, LuaValue... methodArguments) {
      LuaValue method = instance.get(methodName);
      if (!method.isfunction()) {
         throw new LuaError("[MLua] Registered class instance is missing method '" + methodName + "'.");
      }
      LuaValue[] arguments = new LuaValue[methodArguments.length + 1];
      arguments[0] = instance;
      System.arraycopy(methodArguments, 0, arguments, 1, methodArguments.length);
      return method.invoke(LuaValue.varargsOf(arguments)).arg1();
   }

   private static LuaTable openContainerInstance(Map<Object, WeakReference<LuaTable>> cache, Object menuKey,
                                                MCAbstractContainerMenu menu, boolean writable,
                                                MLuaRegistration registration) {
      synchronized (cache) {
         WeakReference<LuaTable> reference = cache.get(menuKey);
         LuaTable instance = reference != null ? reference.get() : null;
         if (instance != null) return instance;
         instance = MLuaContainer.containerTable(menu, MCComponent.literal(containerTitle(menu)), writable, registration);
         cache.put(menuKey, new WeakReference<>(instance));
         return instance;
      }
   }

   private static String containerTitle(MCAbstractContainerMenu menu) {
      return menu.getType() != null ? menu.getType().toString() : menu.getClass().getSimpleName();
   }

   private static LuaValue luaFn(final Supplier<LuaValue> fn) {
      return new ZeroArgFunction() {
         public LuaValue call() {
            try {
               return (LuaValue)fn.get();
            } catch (LuaError var2) {
               throw var2;
            } catch (Exception var3) {
               throw new LuaError("[MLua] " + var3.getMessage());
            }
         }
      };
   }

   private static LuaValue varArg(final Function<Varargs, LuaValue> fn) {
      return new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            try {
               return (Varargs)fn.apply(args);
            } catch (LuaError var3) {
               throw var3;
            } catch (Exception var4) {
               throw new LuaError("[MLua] " + var4.getMessage());
            }
         }
      };
   }
}
