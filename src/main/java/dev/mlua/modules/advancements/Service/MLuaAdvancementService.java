package dev.mlua.modules.advancements.Service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.mc.impl.server.MCMinecraftServerImpl;
import dev.mlua.mcapi.entity.MCServerPlayer;
import dev.mlua.mcapi.server.MCMinecraftServer;
import dev.mlua.minecraft.MLuaResourceLocations;
import java.util.function.Function;
//--version 1.20.1
import dev.mlua.mcapi.advancement.MCAdvancement;
import dev.mlua.mc.impl.advancement.MCAdvancementImpl;
import dev.mlua.mcapi.advancement.MCAdvancementProgress;
import dev.mlua.mc.impl.advancement.MCAdvancementProgressImpl;
import dev.mlua.mcapi.server.MCPlayerAdvancements;
import dev.mlua.mc.impl.server.MCPlayerAdvancementsImpl;
import dev.mlua.mcapi.core.MCResourceLocation;
//--endversion

public final class MLuaAdvancementService extends MLuaServiceBase {
   public MLuaAdvancementService(MLuaService.Context context) {
      super(context);
   }

   public String name() {
      return "Advancements";
   }

   public LuaTable toLua() {
      LuaTable table = new LuaTable();
      table.set("__side", "Server");

      table.set("GetAdvancement", varArg((args) -> {
         this.requireServer();
         String id = args.checkjstring(2);
//--version 1.20.1
         MCAdvancement adv = this.resolveAdvancement(id);
         if (adv == null) {
            return LuaValue.NIL;
         }
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
               LuaTable orGroup = new LuaTable();
               for (int j = 0; j < reqs[i].length; j++) {
                  orGroup.set(j + 1, LuaValue.valueOf(reqs[i][j]));
               }
               requirements.set(i + 1, orGroup);
            }
         }
         info.set("Requirements", requirements);
         return info;
//--endversion
//--version 1.21.1,26.1
         var raw = this.resolveAdvancementHolder(id);
         if (raw == null) {
            return LuaValue.NIL;
         }
         LuaTable info = new LuaTable();
         info.set("Id", LuaValue.valueOf(id));
         var display = raw.display().orElse(null);
         if (display != null) {
            info.set("Title", LuaValue.valueOf(display.getTitle().getString()));
            info.set("Description", LuaValue.valueOf(display.getDescription().getString()));
         } else {
            info.set("Title", LuaValue.valueOf(""));
            info.set("Description", LuaValue.valueOf(""));
         }
         if (raw.parent().isPresent()) {
            info.set("Parent", LuaValue.valueOf(raw.parent().get().toString()));
         } else {
            info.set("Parent", LuaValue.NIL);
         }
         LuaTable requirements = new LuaTable();
         var reqs = raw.requirements().requirements();
         for (int i = 0; i < reqs.size(); i++) {
            LuaTable orGroup = new LuaTable();
            var group = reqs.get(i);
            for (int j = 0; j < group.size(); j++) {
               orGroup.set(j + 1, LuaValue.valueOf(group.get(j)));
            }
            requirements.set(i + 1, orGroup);
         }
         info.set("Requirements", requirements);
         return info;
//--endversion
      }));

      table.set("GetPlayerAdvancements", varArg((args) -> {
         this.requireServer();
         MCServerPlayer player = this.extractPlayer(args.arg(2));
         var rawPa = ((net.minecraft.server.level.ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
         LuaTable result = new LuaTable();
         var rawServer = ((MCMinecraftServerImpl)this.server()).inner();
         for (var adv : rawServer.getAdvancements().getAllAdvancements()) {
            var progress = rawPa.getOrStartProgress(adv);
            LuaTable advTable = new LuaTable();
            advTable.set("Done", LuaValue.valueOf(progress.isDone()));
            LuaTable criteria = new LuaTable();
            for (String c : progress.getCompletedCriteria()) {
               LuaTable cTable = new LuaTable();
               cTable.set("Done", LuaValue.TRUE);
               criteria.set(c, cTable);
            }
            for (String c : progress.getRemainingCriteria()) {
               if (criteria.get(c).isnil()) {
                  LuaTable cTable = new LuaTable();
                  cTable.set("Done", LuaValue.FALSE);
                  criteria.set(c, cTable);
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
      }));

      table.set("GrantCriteria", varArg((args) -> {
         this.requireServer();
         MCServerPlayer player = this.extractPlayer(args.arg(2));
//--version 1.20.1
         MCAdvancement adv = this.resolveAdvancement(args.checkjstring(3));
         if (adv == null) {
            return LuaValue.FALSE;
         }
         String criteria = args.checkjstring(4);
         MCPlayerAdvancements pa = MCPlayerAdvancementsImpl.fromReal(
            (net.minecraft.server.level.ServerPlayer)((MCServerPlayerImpl)player).inner());
         pa.award(adv, criteria);
         return LuaValue.TRUE;
//--endversion
//--version 1.21.1,26.1
         var rawAdv = this.resolveAdvancementHolder(args.checkjstring(3));
         if (rawAdv == null) {
            return LuaValue.FALSE;
         }
         String criteria = args.checkjstring(4);
         var rawPa = ((net.minecraft.server.level.ServerPlayer) ((MCServerPlayerImpl) player).inner()).getAdvancements();
         rawPa.award(new net.minecraft.advancements.AdvancementHolder(MLuaResourceLocations.parse(args.checkjstring(3)), rawAdv), criteria);
         return LuaValue.TRUE;
//--endversion
      }));

      table.set("RevokeCriteria", varArg((args) -> {
         this.requireServer();
         MCServerPlayer player = this.extractPlayer(args.arg(2));
//--version 1.20.1
         MCAdvancement adv = this.resolveAdvancement(args.checkjstring(3));
         if (adv == null) {
            return LuaValue.FALSE;
         }
         String criteria = args.checkjstring(4);
         var rawPa = ((net.minecraft.server.level.ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
         rawPa.revoke(((MCAdvancementImpl)adv).inner(), criteria);
         return LuaValue.TRUE;
//--endversion
//--version 1.21.1,26.1
         var rawAdv = this.resolveAdvancementHolder(args.checkjstring(3));
         if (rawAdv == null) {
            return LuaValue.FALSE;
         }
         String criteria = args.checkjstring(4);
         var rawPa = ((net.minecraft.server.level.ServerPlayer) ((MCServerPlayerImpl) player).inner()).getAdvancements();
         rawPa.revoke(new net.minecraft.advancements.AdvancementHolder(MLuaResourceLocations.parse(args.checkjstring(3)), rawAdv), criteria);
         return LuaValue.TRUE;
//--endversion
      }));

      table.set("GrantAll", varArg((args) -> {
         this.requireServer();
         MCServerPlayer player = this.extractPlayer(args.arg(2));
//--version 1.20.1
         MCAdvancement adv = this.resolveAdvancement(args.checkjstring(3));
         if (adv == null) {
            return LuaValue.NONE;
         }
         var rawPa = ((net.minecraft.server.level.ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
         var rawAdv = ((MCAdvancementImpl)adv).inner();
         var progress = rawPa.getOrStartProgress(rawAdv);
         for (String c : progress.getRemainingCriteria()) {
            rawPa.award(rawAdv, c);
         }
         return LuaValue.NONE;
//--endversion
//--version 1.21.1,26.1
         var rawAdv = this.resolveAdvancementHolder(args.checkjstring(3));
         if (rawAdv == null) {
            return LuaValue.NONE;
         }
         var rawPa = ((net.minecraft.server.level.ServerPlayer) ((MCServerPlayerImpl) player).inner()).getAdvancements();
         var rawHolder = new net.minecraft.advancements.AdvancementHolder(MLuaResourceLocations.parse(args.checkjstring(3)), rawAdv);
         var progress = rawPa.getOrStartProgress(rawHolder);
         for (String criterion : progress.getRemainingCriteria()) {
            rawPa.award(rawHolder, criterion);
         }
         return LuaValue.NONE;
//--endversion
      }));

      table.set("RevokeAll", varArg((args) -> {
         this.requireServer();
         MCServerPlayer player = this.extractPlayer(args.arg(2));
//--version 1.20.1
         MCAdvancement adv = this.resolveAdvancement(args.checkjstring(3));
         if (adv == null) {
            return LuaValue.NONE;
         }
         var rawPa = ((net.minecraft.server.level.ServerPlayer)((MCServerPlayerImpl)player).inner()).getAdvancements();
         var rawAdv = ((MCAdvancementImpl)adv).inner();
         var progress = rawPa.getOrStartProgress(rawAdv);
         for (String c : progress.getCompletedCriteria()) {
            rawPa.revoke(rawAdv, c);
         }
         return LuaValue.NONE;
//--endversion
//--version 1.21.1,26.1
         var rawAdv = this.resolveAdvancementHolder(args.checkjstring(3));
         if (rawAdv == null) {
            return LuaValue.NONE;
         }
         var rawPa = ((net.minecraft.server.level.ServerPlayer) ((MCServerPlayerImpl) player).inner()).getAdvancements();
         var rawHolder = new net.minecraft.advancements.AdvancementHolder(MLuaResourceLocations.parse(args.checkjstring(3)), rawAdv);
         var progress = rawPa.getOrStartProgress(rawHolder);
         for (String criterion : progress.getCompletedCriteria()) {
            rawPa.revoke(rawHolder, criterion);
         }
         return LuaValue.NONE;
//--endversion
      }));

      table.set("GetAllAdvancements", varArg((args) -> {
         this.requireServer();
         LuaTable list = new LuaTable();
         int index = 1;
         var rawServer = ((MCMinecraftServerImpl)this.server()).inner();
         for (var adv : rawServer.getAdvancements().getAllAdvancements()) {
            LuaTable info = new LuaTable();
//--version 1.20.1
            info.set("Id", LuaValue.valueOf(adv.getId().toString()));
            if (adv.getDisplay() != null) {
               info.set("Title", LuaValue.valueOf(adv.getDisplay().getTitle().getString()));
            } else {
               info.set("Title", LuaValue.valueOf(""));
            }
//--endversion
//--version 1.21.1,26.1
            info.set("Id", LuaValue.valueOf(adv.id().toString()));
            var display = adv.value().display().orElse(null);
            info.set("Title", LuaValue.valueOf(display != null ? display.getTitle().getString() : ""));
//--endversion
            list.set(index++, info);
         }
         return list;
      }));

      return table;
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

   private static VarArgFunction varArg(final Function<Varargs, LuaValue> fn) {
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
