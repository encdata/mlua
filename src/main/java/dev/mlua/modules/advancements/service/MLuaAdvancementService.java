package dev.mlua.modules.advancements.service;

import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServerThreadReflectiveService;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

@MLuaDescription("Provides server-side advancement lookup and progress control.")
public final class MLuaAdvancementService extends MLuaServerThreadReflectiveService {
   private final MLuaAdvancementOperations operations;

   public MLuaAdvancementService(MLuaService.Context context) {
      super(context);
      this.operations = new MLuaAdvancementOperations(context);
   }

   @Override
   public String name() {
      return "Advancements";
   }

   @Override
   public LuaTable toLua() {
      LuaTable table = super.toLua();
      table.set("__side", "Server");
      return table;
   }

   @MLuaDescription("Returns advancement details by identifier, or nil when missing.")
   public LuaValue GetAdvancement(String id) {
      this.requireServerSide(this.name());
      return this.operations.getAdvancement(id);
   }

   @MLuaDescription("Returns a player's completion status and criteria for each advancement.")
   public LuaTable GetPlayerAdvancements(LuaValue playerValue) {
      this.requireServerSide(this.name());
      return this.operations.getPlayerAdvancements(playerValue);
   }

   @MLuaDescription("Returns one advancement's progress for a player, or nil when the advancement is missing.")
   public LuaValue GetPlayerAdvancement(LuaValue playerValue, String advancementId) {
      this.requireServerSide(this.name());
      return this.operations.getPlayerAdvancement(playerValue, advancementId);
   }

   @MLuaDescription("Grants one criterion for an advancement to a player.")
   public boolean GrantCriteria(LuaValue playerValue, String advancementId, String criterion) {
      this.requireServerSide(this.name());
      return this.operations.grantCriteria(playerValue, advancementId, criterion);
   }

   @MLuaDescription("Revokes one criterion for an advancement from a player.")
   public boolean RevokeCriteria(LuaValue playerValue, String advancementId, String criterion) {
      this.requireServerSide(this.name());
      return this.operations.revokeCriteria(playerValue, advancementId, criterion);
   }

   @MLuaDescription("Grants all criteria for an advancement to a player.")
   public int GrantAll(LuaValue playerValue, String advancementId) {
      this.requireServerSide(this.name());
      return this.operations.grantAll(playerValue, advancementId);
   }

   @MLuaDescription("Revokes all criteria for an advancement from a player.")
   public int RevokeAll(LuaValue playerValue, String advancementId) {
      this.requireServerSide(this.name());
      return this.operations.revokeAll(playerValue, advancementId);
   }

   @MLuaDescription("Returns details for all registered advancements.")
   public LuaTable GetAllAdvancements() {
      this.requireServerSide(this.name());
      return this.operations.getAllAdvancements();
   }
}
