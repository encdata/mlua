package dev.mlua.modules.advancements.Service;

import dev.mlua.api.MLuaDescription;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.mc.impl.server.MCMinecraftServerImpl;
import dev.mlua.mcapi.entity.MCServerPlayer;
import dev.mlua.mcapi.server.MCMinecraftServer;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
//--version 1.20.1
import dev.mlua.mcapi.advancement.MCAdvancement;
import dev.mlua.mc.impl.advancement.MCAdvancementImpl;
import dev.mlua.mcapi.server.MCPlayerAdvancements;
import dev.mlua.mc.impl.server.MCPlayerAdvancementsImpl;
//--endversion

@MLuaDescription("Provides server-side advancement lookup and progress control.")
public final class MLuaAdvancementService extends MLuaReflectiveService {
   public MLuaAdvancementService(MLuaService.Context context) {
      super(context);
   }

   public String name() {
      return "Advancements";
   }

   public LuaTable toLua() {
      LuaTable table = super.toLua();
      table.set("__side", "Server");
      return table;
   }

   @MLuaDescription("Returns advancement details by identifier, or nil when missing.")
   public LuaValue GetAdvancement(String id) {
      this.requireServerSide(this.name());
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
      return this.advancementInfo(raw.getId().toString(),
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
      return this.advancementInfo(MLuaResourceLocations.parse(id).toString(),
         display == null ? "" : display.getTitle().getString(),
         display == null ? "" : display.getDescription().getString(),
         raw.parent().isPresent() ? LuaValue.valueOf(raw.parent().get().toString()) : LuaValue.NIL, groups);
//--endversion
   }

   @MLuaDescription("Returns a player's completion status and criteria for each advancement.")
   public LuaTable GetPlayerAdvancements(LuaValue playerValue) {
      this.requireServerSide(this.name());
      MCServerPlayer player = this.extractPlayer(playerValue);
      var rawPa = ((net.minecraft.server.level.ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
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
         result.set(advancementId, this.progressInfo(advancementId, progress));
      }
      return result;
   }

   @MLuaDescription("Returns one advancement's progress for a player, or nil when the advancement is missing.")
   public LuaValue GetPlayerAdvancement(LuaValue playerValue, String advancementId) {
      this.requireServerSide(this.name());
      MCServerPlayer player = this.extractPlayer(playerValue);
      var rawPa = ((net.minecraft.server.level.ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
      String canonicalId;
      net.minecraft.advancements.AdvancementProgress progress;
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
      var holder = new net.minecraft.advancements.AdvancementHolder(
         MLuaResourceLocations.parse(advancementId), advancement);
      canonicalId = holder.id().toString();
      progress = rawPa.getOrStartProgress(holder);
//--endversion
      return this.progressInfo(canonicalId, progress);
   }

   private LuaTable progressInfo(String id, net.minecraft.advancements.AdvancementProgress progress) {
      LuaTable result = this.context.mlua().registration().createClassInstance("AdvancementProgress");
      result.set("Id", LuaValue.valueOf(id));
      result.set("Done", LuaValue.valueOf(progress.isDone()));
      result.set("IsDone", LuaValue.valueOf(progress.isDone()));
      LuaTable criteria = new LuaTable();
      for (String criterion : progress.getCompletedCriteria()) {
         LuaTable criterionTable = this.context.mlua().registration().createClassInstance("CriterionProgress");
         criterionTable.set("Name", LuaValue.valueOf(criterion));
         criterionTable.set("Done", LuaValue.TRUE);
         criteria.set(criterion, criterionTable);
      }
      for (String criterion : progress.getRemainingCriteria()) {
         if (criteria.get(criterion).isnil()) {
            LuaTable criterionTable = this.context.mlua().registration().createClassInstance("CriterionProgress");
            criterionTable.set("Name", LuaValue.valueOf(criterion));
            criterionTable.set("Done", LuaValue.FALSE);
            criteria.set(criterion, criterionTable);
         }
      }
      result.set("Progress", criteria);
      result.set("Criteria", criteria);
      return result;
   }

   @MLuaDescription("Grants one criterion for an advancement to a player.")
   public boolean GrantCriteria(LuaValue playerValue, String advancementId, String criterion) {
      this.requireServerSide(this.name());
      MCServerPlayer player = this.extractPlayer(playerValue);
//--version 1.20.1
      MCAdvancement adv = this.resolveAdvancement(advancementId);
      if (adv == null) return false;
      MCPlayerAdvancements pa = MCPlayerAdvancementsImpl.fromReal(
         (net.minecraft.server.level.ServerPlayer)((MCServerPlayerImpl)player).inner());
      var progress = pa.getOrStartProgress(adv);
      if (!progress.getRemainingCriteria().contains(criterion)) return false;
      pa.award(adv, criterion);
      return true;
//--endversion
//--version 1.21.1,26.1
      var rawAdv = this.resolveAdvancementHolder(advancementId);
      if (rawAdv == null) return false;
      var rawPa = ((net.minecraft.server.level.ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
      var holder = new net.minecraft.advancements.AdvancementHolder(MLuaResourceLocations.parse(advancementId), rawAdv);
      if (!rawPa.getOrStartProgress(holder).getRemainingCriteria().contains(criterion)) return false;
      rawPa.award(holder, criterion);
      return true;
//--endversion
   }

   @MLuaDescription("Revokes one criterion for an advancement from a player.")
   public boolean RevokeCriteria(LuaValue playerValue, String advancementId, String criterion) {
      this.requireServerSide(this.name());
      MCServerPlayer player = this.extractPlayer(playerValue);
//--version 1.20.1
      MCAdvancement adv = this.resolveAdvancement(advancementId);
      if (adv == null) return false;
      var rawPa = ((net.minecraft.server.level.ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
      if (!rawPa.getOrStartProgress(((MCAdvancementImpl)adv).inner()).getCompletedCriteria().contains(criterion)) return false;
      rawPa.revoke(((MCAdvancementImpl)adv).inner(), criterion);
      return true;
//--endversion
//--version 1.21.1,26.1
      var rawAdv = this.resolveAdvancementHolder(advancementId);
      if (rawAdv == null) return false;
      var rawPa = ((net.minecraft.server.level.ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
      var holder = new net.minecraft.advancements.AdvancementHolder(MLuaResourceLocations.parse(advancementId), rawAdv);
      if (!rawPa.getOrStartProgress(holder).getCompletedCriteria().contains(criterion)) return false;
      rawPa.revoke(holder, criterion);
      return true;
//--endversion
   }

   @MLuaDescription("Grants all criteria for an advancement to a player.")
   public int GrantAll(LuaValue playerValue, String advancementId) {
      this.requireServerSide(this.name());
      MCServerPlayer player = this.extractPlayer(playerValue);
//--version 1.20.1
      MCAdvancement adv = this.resolveAdvancement(advancementId);
      if (adv == null) return 0;
      var rawPa = ((net.minecraft.server.level.ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
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
      var rawPa = ((net.minecraft.server.level.ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
      var rawHolder = new net.minecraft.advancements.AdvancementHolder(MLuaResourceLocations.parse(advancementId), rawAdv);
      var progress = rawPa.getOrStartProgress(rawHolder);
      int changed = 0;
      for (String criterion : progress.getRemainingCriteria()) {
         rawPa.award(rawHolder, criterion);
         changed++;
      }
      return changed;
//--endversion
   }

   @MLuaDescription("Revokes all criteria for an advancement from a player.")
   public int RevokeAll(LuaValue playerValue, String advancementId) {
      this.requireServerSide(this.name());
      MCServerPlayer player = this.extractPlayer(playerValue);
//--version 1.20.1
      MCAdvancement adv = this.resolveAdvancement(advancementId);
      if (adv == null) return 0;
      var rawPa = ((net.minecraft.server.level.ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
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
      var rawPa = ((net.minecraft.server.level.ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
      var rawHolder = new net.minecraft.advancements.AdvancementHolder(MLuaResourceLocations.parse(advancementId), rawAdv);
      var progress = rawPa.getOrStartProgress(rawHolder);
      int changed = 0;
      for (String criterion : progress.getCompletedCriteria()) {
         rawPa.revoke(rawHolder, criterion);
         changed++;
      }
      return changed;
//--endversion
   }

   @MLuaDescription("Returns details for all registered advancements.")
   public LuaTable GetAllAdvancements() {
      this.requireServerSide(this.name());
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
         LuaTable info = this.advancementInfo(adv.getId().toString(),
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
         LuaTable info = this.advancementInfo(adv.id().toString(),
            display == null ? "" : display.getTitle().getString(),
            display == null ? "" : display.getDescription().getString(),
            advancement.parent().isPresent() ? LuaValue.valueOf(advancement.parent().get().toString()) : LuaValue.NIL, groups);
//--endversion
         list.set(index++, info);
      }
      return list;
   }

   private LuaTable advancementInfo(String id, String title, String description, LuaValue parent,
                                    java.util.List<java.util.List<String>> requirementGroups) {
      LuaTable info = this.context.mlua().registration().createClassInstance("Advancement");
      info.set("Id", LuaValue.valueOf(id));
      info.set("Title", LuaValue.valueOf(title));
      info.set("Description", LuaValue.valueOf(description));
      info.set("Parent", parent);
      LuaTable requirements = new LuaTable();
      for (int groupIndex = 0; groupIndex < requirementGroups.size(); groupIndex++) {
         LuaTable group = new LuaTable();
         java.util.List<String> criteria = requirementGroups.get(groupIndex);
         for (int criterionIndex = 0; criterionIndex < criteria.size(); criterionIndex++) {
            group.set(criterionIndex + 1, LuaValue.valueOf(criteria.get(criterionIndex)));
         }
         requirements.set(groupIndex + 1, group);
      }
      info.set("Requirements", requirements);
      return info;
   }

   private MCMinecraftServer server() {
      return MCMinecraftServerImpl.fromReal(this.context.mlua().server());
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
   private net.minecraft.advancements.Advancement resolveAdvancementHolder(String id) {
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
      if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) return MCServerPlayerImpl.fromReal(serverPlayer);
      throw new LuaError("[MLua] Expected a connected ServerPlayer instance");
   }

}
