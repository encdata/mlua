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
//--version 1.20.1
import dev.mlua.mcapi.advancement.MCAdvancement;
import dev.mlua.mc.impl.advancement.MCAdvancementImpl;
import dev.mlua.mcapi.server.MCPlayerAdvancements;
import dev.mlua.mc.impl.server.MCPlayerAdvancementsImpl;
//--endversion

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
      this.requireServer();
//--version 1.20.1
      MCAdvancement adv = this.resolveAdvancement(id);
      if (adv == null) return LuaValue.NIL;
      LuaTable info = new LuaTable();
      info.set("Id", LuaValue.valueOf(id));
      var raw = ((MCAdvancementImpl)adv).inner();
      if (raw.getDisplay() != null) {
         info.set("Title", LuaValue.valueOf(raw.getDisplay().getTitle().getString()));
         info.set("Description", LuaValue.valueOf(raw.getDisplay().getDescription().getString()));
      } else {
         info.set("Title", LuaValue.valueOf(""));
         info.set("Description", LuaValue.valueOf(""));
      }
      info.set("Parent", raw.getParent() != null ? LuaValue.valueOf(raw.getParent().getId().toString()) : LuaValue.NIL);
      LuaTable requirements = new LuaTable();
      String[][] reqs = raw.getRequirements();
      if (reqs != null) {
         for (int i = 0; i < reqs.length; i++) {
            LuaTable group = new LuaTable();
            for (int j = 0; j < reqs[i].length; j++) group.set(j + 1, LuaValue.valueOf(reqs[i][j]));
            requirements.set(i + 1, group);
         }
      }
      info.set("Requirements", requirements);
      return info;
//--endversion
//--version 1.21.1,26.1
      var raw = this.resolveAdvancementHolder(id);
      if (raw == null) return LuaValue.NIL;
      LuaTable info = new LuaTable();
      info.set("Id", LuaValue.valueOf(id));
      var display = raw.display().orElse(null);
      info.set("Title", LuaValue.valueOf(display == null ? "" : display.getTitle().getString()));
      info.set("Description", LuaValue.valueOf(display == null ? "" : display.getDescription().getString()));
      info.set("Parent", raw.parent().map(parent -> LuaValue.valueOf(parent.toString())).orElse(LuaValue.NIL));
      LuaTable requirements = new LuaTable();
      var reqs = raw.requirements().requirements();
      for (int i = 0; i < reqs.size(); i++) {
         LuaTable group = new LuaTable();
         var requirementGroup = reqs.get(i);
         for (int j = 0; j < requirementGroup.size(); j++) group.set(j + 1, LuaValue.valueOf(requirementGroup.get(j)));
         requirements.set(i + 1, group);
      }
      info.set("Requirements", requirements);
      return info;
//--endversion
   }

   @MLuaDescription("Returns a player's completion status and criteria for each advancement.")
   public LuaTable GetPlayerAdvancements(LuaValue playerValue) {
      this.requireServer();
      MCServerPlayer player = this.extractPlayer(playerValue);
      var rawPa = ((net.minecraft.server.level.ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
      LuaTable result = new LuaTable();
      var rawServer = ((MCMinecraftServerImpl)this.server()).inner();
      for (var adv : rawServer.getAdvancements().getAllAdvancements()) {
         var progress = rawPa.getOrStartProgress(adv);
         LuaTable advTable = new LuaTable();
         advTable.set("Done", LuaValue.valueOf(progress.isDone()));
         LuaTable criteria = new LuaTable();
         for (String criterion : progress.getCompletedCriteria()) {
            LuaTable criterionTable = new LuaTable();
            criterionTable.set("Done", LuaValue.TRUE);
            criteria.set(criterion, criterionTable);
         }
         for (String criterion : progress.getRemainingCriteria()) {
            if (criteria.get(criterion).isnil()) {
               LuaTable criterionTable = new LuaTable();
               criterionTable.set("Done", LuaValue.FALSE);
               criteria.set(criterion, criterionTable);
            }
         }
         advTable.set("Progress", criteria);
//--version 1.20.1
         result.set(adv.getId().toString(), advTable);
//--endversion
//--version 1.21.1,26.1
         result.set(adv.id().toString(), advTable);
//--endversion
      }
      return result;
   }

   @MLuaDescription("Grants one criterion for an advancement to a player.")
   public boolean GrantCriteria(LuaValue playerValue, String advancementId, String criterion) {
      this.requireServer();
      MCServerPlayer player = this.extractPlayer(playerValue);
//--version 1.20.1
      MCAdvancement adv = this.resolveAdvancement(advancementId);
      if (adv == null) return false;
      MCPlayerAdvancements pa = MCPlayerAdvancementsImpl.fromReal(
         (net.minecraft.server.level.ServerPlayer)((MCServerPlayerImpl)player).inner());
      pa.award(adv, criterion);
      return true;
//--endversion
//--version 1.21.1,26.1
      var rawAdv = this.resolveAdvancementHolder(advancementId);
      if (rawAdv == null) return false;
      var rawPa = ((net.minecraft.server.level.ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
      rawPa.award(new net.minecraft.advancements.AdvancementHolder(MLuaResourceLocations.parse(advancementId), rawAdv), criterion);
      return true;
//--endversion
   }

   @MLuaDescription("Revokes one criterion for an advancement from a player.")
   public boolean RevokeCriteria(LuaValue playerValue, String advancementId, String criterion) {
      this.requireServer();
      MCServerPlayer player = this.extractPlayer(playerValue);
//--version 1.20.1
      MCAdvancement adv = this.resolveAdvancement(advancementId);
      if (adv == null) return false;
      var rawPa = ((net.minecraft.server.level.ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
      rawPa.revoke(((MCAdvancementImpl)adv).inner(), criterion);
      return true;
//--endversion
//--version 1.21.1,26.1
      var rawAdv = this.resolveAdvancementHolder(advancementId);
      if (rawAdv == null) return false;
      var rawPa = ((net.minecraft.server.level.ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
      rawPa.revoke(new net.minecraft.advancements.AdvancementHolder(MLuaResourceLocations.parse(advancementId), rawAdv), criterion);
      return true;
//--endversion
   }

   @MLuaDescription("Grants all criteria for an advancement to a player.")
   public void GrantAll(LuaValue playerValue, String advancementId) {
      this.requireServer();
      MCServerPlayer player = this.extractPlayer(playerValue);
//--version 1.20.1
      MCAdvancement adv = this.resolveAdvancement(advancementId);
      if (adv == null) return;
      var rawPa = ((net.minecraft.server.level.ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
      var rawAdv = ((MCAdvancementImpl)adv).inner();
      var progress = rawPa.getOrStartProgress(rawAdv);
      for (String criterion : progress.getRemainingCriteria()) rawPa.award(rawAdv, criterion);
//--endversion
//--version 1.21.1,26.1
      var rawAdv = this.resolveAdvancementHolder(advancementId);
      if (rawAdv == null) return;
      var rawPa = ((net.minecraft.server.level.ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
      var rawHolder = new net.minecraft.advancements.AdvancementHolder(MLuaResourceLocations.parse(advancementId), rawAdv);
      var progress = rawPa.getOrStartProgress(rawHolder);
      for (String criterion : progress.getRemainingCriteria()) rawPa.award(rawHolder, criterion);
//--endversion
   }

   @MLuaDescription("Revokes all criteria for an advancement from a player.")
   public void RevokeAll(LuaValue playerValue, String advancementId) {
      this.requireServer();
      MCServerPlayer player = this.extractPlayer(playerValue);
//--version 1.20.1
      MCAdvancement adv = this.resolveAdvancement(advancementId);
      if (adv == null) return;
      var rawPa = ((net.minecraft.server.level.ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
      var rawAdv = ((MCAdvancementImpl)adv).inner();
      var progress = rawPa.getOrStartProgress(rawAdv);
      for (String criterion : progress.getCompletedCriteria()) rawPa.revoke(rawAdv, criterion);
//--endversion
//--version 1.21.1,26.1
      var rawAdv = this.resolveAdvancementHolder(advancementId);
      if (rawAdv == null) return;
      var rawPa = ((net.minecraft.server.level.ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
      var rawHolder = new net.minecraft.advancements.AdvancementHolder(MLuaResourceLocations.parse(advancementId), rawAdv);
      var progress = rawPa.getOrStartProgress(rawHolder);
      for (String criterion : progress.getCompletedCriteria()) rawPa.revoke(rawHolder, criterion);
//--endversion
   }

   @MLuaDescription("Returns details for all registered advancements.")
   public LuaTable GetAllAdvancements() {
      this.requireServer();
      LuaTable list = new LuaTable();
      int index = 1;
      var rawServer = ((MCMinecraftServerImpl)this.server()).inner();
      for (var adv : rawServer.getAdvancements().getAllAdvancements()) {
         LuaTable info = new LuaTable();
//--version 1.20.1
         info.set("Id", LuaValue.valueOf(adv.getId().toString()));
         if (adv.getDisplay() != null) info.set("Title", LuaValue.valueOf(adv.getDisplay().getTitle().getString()));
         else info.set("Title", LuaValue.valueOf(""));
//--endversion
//--version 1.21.1,26.1
         info.set("Id", LuaValue.valueOf(adv.id().toString()));
         var display = adv.value().display().orElse(null);
         info.set("Title", LuaValue.valueOf(display != null ? display.getTitle().getString() : ""));
//--endversion
         list.set(index++, info);
      }
      return list;
   }

   private void requireServer() {
      if (this.context.mlua().side() != Side.SERVER) {
         throw new LuaError("[MLua] Advancement service methods are server-side only");
      }
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
      if (!playerTable.istable()) {
         throw new LuaError("[MLua] Expected a player table");
      }
      LuaValue ud = playerTable.get("__entity");
      if (ud != null && ud.isuserdata()) {
         Object obj = ud.checkuserdata(net.minecraft.server.level.ServerPlayer.class);
         if (obj instanceof net.minecraft.server.level.ServerPlayer sp) {
            return MCServerPlayerImpl.fromReal(sp);
         }
      }
      throw new LuaError("[MLua] Expected a valid player table");
   }

}
