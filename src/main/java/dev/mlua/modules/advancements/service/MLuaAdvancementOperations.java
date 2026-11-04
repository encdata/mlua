package dev.mlua.modules.advancements.service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.core.service.MLuaService;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.mc.impl.server.MCMinecraftServerImpl;
import dev.mlua.mcapi.entity.MCServerPlayer;
import dev.mlua.mcapi.server.MCMinecraftServer;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
//--version 1.20.1
import dev.mlua.mcapi.advancement.MCAdvancement;
import dev.mlua.mc.impl.advancement.MCAdvancementImpl;
import dev.mlua.mcapi.server.MCPlayerAdvancements;
import dev.mlua.mc.impl.server.MCPlayerAdvancementsImpl;
//--endversion
//--version 1.21.1,26.1
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
//--endversion
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.level.ServerPlayer;

final class MLuaAdvancementOperations {
   private final MLuaService.Context context;

   MLuaAdvancementOperations(MLuaService.Context context) {
      this.context = context;
   }
   LuaValue getAdvancement(String id) {
//--version 1.20.1
      MCAdvancement adv = this.resolveAdvancement(id);
      if (adv == null) return LuaValue.NIL;
      var raw = ((MCAdvancementImpl)adv).inner();
      var display = raw.getDisplay();
      java.util.List<java.util.List<String>> groups = new java.util.ArrayList<>();
      String[][] reqs = raw.getRequirements();
      if (reqs != null) {
         for (String[] requirement : reqs) groups.add(java.util.Arrays.asList(requirement));
      }
      return MLuaAdvancementViews.advancementInfo(this.context.mlua().registration(), raw.getId().toString(),
         display == null ? "" : display.getTitle().getString(),
         display == null ? "" : display.getDescription().getString(),
         raw.getParent() == null ? LuaValue.NIL : LuaValue.valueOf(raw.getParent().getId().toString()), groups);
//--endversion
//--version 1.21.1,26.1
      var raw = this.resolveAdvancementHolder(id);
      if (raw == null) return LuaValue.NIL;
      var display = raw.display().orElse(null);
      java.util.List<java.util.List<String>> groups = new java.util.ArrayList<>();
      var reqs = raw.requirements().requirements();
      for (var requirement : reqs) groups.add(java.util.List.copyOf(requirement));
      return MLuaAdvancementViews.advancementInfo(this.context.mlua().registration(), MLuaResourceLocations.parse(id).toString(),
         display == null ? "" : display.getTitle().getString(),
         display == null ? "" : display.getDescription().getString(),
         raw.parent().isPresent() ? LuaValue.valueOf(raw.parent().get().toString()) : LuaValue.NIL, groups);
//--endversion
   }
   LuaTable getPlayerAdvancements(LuaValue playerValue) {
      MCServerPlayer player = this.extractPlayer(playerValue);
      var rawPa = ((ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
      LuaTable result = new LuaTable();
      var rawServer = ((MCMinecraftServerImpl)this.server()).inner();
      for (var adv : rawServer.getAdvancements().getAllAdvancements()) {
         var progress = rawPa.getOrStartProgress(adv);
         String advancementId;
//--version 1.20.1
         advancementId = adv.getId().toString();
//--endversion
//--version 1.21.1,26.1
         advancementId = adv.id().toString();
//--endversion
         result.set(advancementId, MLuaAdvancementViews.progressInfo(this.context.mlua().registration(), advancementId, progress));
      }
      return result;
   }
   LuaValue getPlayerAdvancement(LuaValue playerValue, String advancementId) {
      MCServerPlayer player = this.extractPlayer(playerValue);
      var rawPa = ((ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
      String canonicalId;
      AdvancementProgress progress;
//--version 1.20.1
      MCAdvancement advancement = this.resolveAdvancement(advancementId);
      if (advancement == null) return LuaValue.NIL;
      var rawAdvancement = ((MCAdvancementImpl)advancement).inner();
      canonicalId = rawAdvancement.getId().toString();
      progress = rawPa.getOrStartProgress(rawAdvancement);
//--endversion
//--version 1.21.1,26.1
      var advancement = this.resolveAdvancementHolder(advancementId);
      if (advancement == null) return LuaValue.NIL;
      var holder = new AdvancementHolder(
         MLuaResourceLocations.parse(advancementId), advancement);
      canonicalId = holder.id().toString();
      progress = rawPa.getOrStartProgress(holder);
//--endversion
      return MLuaAdvancementViews.progressInfo(this.context.mlua().registration(), canonicalId, progress);
   }


   boolean grantCriteria(LuaValue playerValue, String advancementId, String criterion) {
      MCServerPlayer player = this.extractPlayer(playerValue);
//--version 1.20.1
      MCAdvancement adv = this.resolveAdvancement(advancementId);
      if (adv == null) return false;
      MCPlayerAdvancements pa = MCPlayerAdvancementsImpl.fromReal(
         (ServerPlayer)((MCServerPlayerImpl)player).inner());
      return pa.award(adv, criterion);
//--endversion
//--version 1.21.1,26.1
      var rawAdv = this.resolveAdvancementHolder(advancementId);
      if (rawAdv == null) return false;
      var rawPa = ((ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
      var holder = new AdvancementHolder(MLuaResourceLocations.parse(advancementId), rawAdv);
      return rawPa.award(holder, criterion);
//--endversion
   }
   boolean revokeCriteria(LuaValue playerValue, String advancementId, String criterion) {
      MCServerPlayer player = this.extractPlayer(playerValue);
//--version 1.20.1
      MCAdvancement adv = this.resolveAdvancement(advancementId);
      if (adv == null) return false;
      var rawPa = ((ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
      var progress = rawPa.getOrStartProgress(((MCAdvancementImpl)adv).inner());
      if (!contains(progress.getCompletedCriteria(), criterion)) return false;
      rawPa.revoke(((MCAdvancementImpl)adv).inner(), criterion);
      return true;
//--endversion
//--version 1.21.1,26.1
      var rawAdv = this.resolveAdvancementHolder(advancementId);
      if (rawAdv == null) return false;
      var rawPa = ((ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
      var holder = new AdvancementHolder(MLuaResourceLocations.parse(advancementId), rawAdv);
      if (!contains(rawPa.getOrStartProgress(holder).getCompletedCriteria(), criterion)) return false;
      rawPa.revoke(holder, criterion);
      return true;
//--endversion
   }
   int grantAll(LuaValue playerValue, String advancementId) {
      MCServerPlayer player = this.extractPlayer(playerValue);
//--version 1.20.1
      MCAdvancement adv = this.resolveAdvancement(advancementId);
      if (adv == null) return 0;
      var rawPa = ((ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
      var rawAdv = ((MCAdvancementImpl)adv).inner();
      var progress = rawPa.getOrStartProgress(rawAdv);
      int changed = 0;
      for (String criterion : progress.getRemainingCriteria()) {
         rawPa.award(rawAdv, criterion);
         changed++;
      }
      return changed;
//--endversion
//--version 1.21.1,26.1
      var rawAdv = this.resolveAdvancementHolder(advancementId);
      if (rawAdv == null) return 0;
      var rawPa = ((ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
      var rawHolder = new AdvancementHolder(MLuaResourceLocations.parse(advancementId), rawAdv);
      var progress = rawPa.getOrStartProgress(rawHolder);
      int changed = 0;
      for (String criterion : progress.getRemainingCriteria()) {
         rawPa.award(rawHolder, criterion);
         changed++;
      }
      return changed;
//--endversion
   }
   int revokeAll(LuaValue playerValue, String advancementId) {
      MCServerPlayer player = this.extractPlayer(playerValue);
//--version 1.20.1
      MCAdvancement adv = this.resolveAdvancement(advancementId);
      if (adv == null) return 0;
      var rawPa = ((ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
      var rawAdv = ((MCAdvancementImpl)adv).inner();
      var progress = rawPa.getOrStartProgress(rawAdv);
      int changed = 0;
      for (String criterion : progress.getCompletedCriteria()) {
         rawPa.revoke(rawAdv, criterion);
         changed++;
      }
      return changed;
//--endversion
//--version 1.21.1,26.1
      var rawAdv = this.resolveAdvancementHolder(advancementId);
      if (rawAdv == null) return 0;
      var rawPa = ((ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
      var rawHolder = new AdvancementHolder(MLuaResourceLocations.parse(advancementId), rawAdv);
      var progress = rawPa.getOrStartProgress(rawHolder);
      int changed = 0;
      for (String criterion : progress.getCompletedCriteria()) {
         rawPa.revoke(rawHolder, criterion);
         changed++;
      }
      return changed;
//--endversion
   }
   LuaTable getAllAdvancements() {
      LuaTable list = new LuaTable();
      int index = 1;
      var rawServer = ((MCMinecraftServerImpl)this.server()).inner();
      var advancements = new java.util.ArrayList<>(rawServer.getAdvancements().getAllAdvancements());
//--version 1.20.1
      advancements.sort(java.util.Comparator.comparing(advancement -> advancement.getId().toString()));
//--endversion
//--version 1.21.1,26.1
      advancements.sort(java.util.Comparator.comparing(advancement -> advancement.id().toString()));
//--endversion
      for (var adv : advancements) {
//--version 1.20.1
         var display = adv.getDisplay();
         java.util.List<java.util.List<String>> groups = new java.util.ArrayList<>();
         String[][] requirements = adv.getRequirements();
         if (requirements != null) for (String[] requirement : requirements) {
            groups.add(java.util.Arrays.asList(requirement));
         }
         LuaTable info = MLuaAdvancementViews.advancementInfo(this.context.mlua().registration(), adv.getId().toString(),
            display == null ? "" : display.getTitle().getString(),
            display == null ? "" : display.getDescription().getString(),
            adv.getParent() == null ? LuaValue.NIL : LuaValue.valueOf(adv.getParent().getId().toString()), groups);
//--endversion
//--version 1.21.1,26.1
         var advancement = adv.value();
         var display = advancement.display().orElse(null);
         java.util.List<java.util.List<String>> groups = new java.util.ArrayList<>();
         var requirements = advancement.requirements().requirements();
         for (var requirement : requirements) groups.add(java.util.List.copyOf(requirement));
         LuaTable info = MLuaAdvancementViews.advancementInfo(this.context.mlua().registration(), adv.id().toString(),
            display == null ? "" : display.getTitle().getString(),
            display == null ? "" : display.getDescription().getString(),
            advancement.parent().isPresent() ? LuaValue.valueOf(advancement.parent().get().toString()) : LuaValue.NIL, groups);
//--endversion
         list.set(index++, info);
      }
      return list;
   }



   private MCMinecraftServer server() {
      return MCMinecraftServerImpl.fromReal(this.context.mlua().server());
   }

   private static boolean contains(Iterable<String> values, String expected) {
      for (String value : values) if (expected.equals(value)) return true;
      return false;
   }

//--version 1.20.1
   private MCAdvancement resolveAdvancement(String id) {
      MCMinecraftServer server = this.server();
      if (server == null) {
         return null;
      }
      var location = MLuaResourceLocations.parse(id);
      var raw = ((MCMinecraftServerImpl)server).inner().getAdvancements().getAdvancement(location);
      return raw == null ? null : MCAdvancementImpl.fromReal(raw);
   }
//--endversion
//--version 1.21.1,26.1
   private Advancement resolveAdvancementHolder(String id) {
      MCMinecraftServer server = this.server();
      if (server == null) {
         return null;
      }
      var location = MLuaResourceLocations.parse(id);
      var raw = ((MCMinecraftServerImpl) server).inner().getAdvancements().get(location);
      return raw == null ? null : raw.value();
   }
//--endversion

   private MCServerPlayer extractPlayer(LuaValue playerTable) {
      var player = MLuaEntityWrapper.requireLuaInstance(playerTable, this.context.mlua().registration(), "Player", "Player");
      if (player instanceof ServerPlayer serverPlayer) return MCServerPlayerImpl.fromReal(serverPlayer);
      throw new LuaError("[MLua] Expected a connected ServerPlayer instance");
   }

}
