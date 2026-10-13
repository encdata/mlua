package dev.mlua.modules.quest.Service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.runtime.data.MLuaDataManager;
import dev.mlua.modules.quest.MLuaQuestRegistry;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
//--version 26.1
import net.minecraft.nbt.ByteTag;
//--endversion
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class MLuaQuestService extends MLuaServiceBase {
    private static final String NBT_KEY = "mlua_quests";
    public MLuaQuestService(MLuaService.Context context) {
        super(context);
    }

    @Override
    public String name() {
        return "Quest";
    }

    @Override
    public LuaTable toLua() {
        LuaTable table = new LuaTable();
        table.set("__side", "Server");

        table.set("CreateQuest", fn((args) -> {
            requireServer();
            String questId = args.checkjstring(2);
            String title = args.checkjstring(3);
            String description = args.checkjstring(4);
            LuaTable def = new LuaTable();
            def.set("Id", LuaValue.valueOf(questId));
            def.set("Title", LuaValue.valueOf(title));
            def.set("Description", LuaValue.valueOf(description));
            MLuaQuestRegistry.put(runtimeOwner(), ownerModId(), questId, def);
            return def;
        }));

        table.set("RemoveQuest", fn((args) -> {
            requireServer();
            MLuaQuestRegistry.remove(runtimeOwner(), ownerModId(), args.checkjstring(2));
            return LuaValue.NONE;
        }));

        table.set("GetQuest", fn((args) -> {
            requireServer();
            LuaTable def = MLuaQuestRegistry.get(runtimeOwner(), ownerModId(), args.checkjstring(2));
            return def != null ? def : LuaValue.NIL;
        }));

        table.set("GetQuests", fn((args) -> {
            requireServer();
            LuaTable result = new LuaTable();
            for (Map.Entry<MLuaQuestRegistry.Key, LuaTable> entry : MLuaQuestRegistry.snapshot(runtimeOwner(), ownerModId()).entrySet()) {
                result.set(entry.getKey().id(), entry.getValue());
            }
            return result;
        }));

        table.set("SetObjective", fn((args) -> {
            requireServer();
            ServerPlayer player = extractPlayer(args.arg(2));
            String questId = args.checkjstring(3);
            String objectiveKey = args.checkjstring(4);
            LuaValue progress = args.arg(5);
            CompoundTag persistentData = MLuaDataManager.playerPersistentTag(player);
            CompoundTag playerQuests = getCompound(persistentData, NBT_KEY);
            if (!persistentData.contains(NBT_KEY)) {
                persistentData.put(NBT_KEY, playerQuests);
            }
            CompoundTag questData = questData(player, persistentData, playerQuests, questId, true);
            if (progress.isboolean()) {
                questData.putBoolean(objectiveKey, progress.checkboolean());
            } else if (progress.isnumber()) {
                questData.putDouble(objectiveKey, progress.checkdouble());
            }
            MLuaDataManager.setPlayerPersistentTag(player, persistentData);
            return LuaValue.NONE;
        }));

        table.set("GetObjective", fn((args) -> {
            requireServer();
            ServerPlayer player = extractPlayer(args.arg(2));
            String questId = args.checkjstring(3);
            String objectiveKey = args.checkjstring(4);
            CompoundTag persistentData = MLuaDataManager.playerPersistentTag(player);
            CompoundTag questData = questData(player, persistentData,
                getCompound(persistentData, NBT_KEY), questId, false);
            if (!questData.contains(objectiveKey)) {
                return LuaValue.NIL;
            }
            if (isBooleanTag(questData, objectiveKey)) {
                return LuaValue.valueOf(getBoolean(questData, objectiveKey));
            }
            return LuaValue.valueOf(getDouble(questData, objectiveKey));
        }));

        table.set("GetObjectives", fn((args) -> {
            requireServer();
            ServerPlayer player = extractPlayer(args.arg(2));
            String questId = args.checkjstring(3);
            CompoundTag persistentData = MLuaDataManager.playerPersistentTag(player);
            CompoundTag questData = questData(player, persistentData,
                getCompound(persistentData, NBT_KEY), questId, false);
            LuaTable result = new LuaTable();
            for (String key : questData.getAllKeys()) {
                if (key.equals("__started") || key.equals("__completed")) continue;
                if (isBooleanTag(questData, key)) {
                    result.set(key, LuaValue.valueOf(getBoolean(questData, key)));
                } else {
                    result.set(key, LuaValue.valueOf(getDouble(questData, key)));
                }
            }
            return result;
        }));

        table.set("StartQuest", fn((args) -> {
            requireServer();
            ServerPlayer player = extractPlayer(args.arg(2));
            String questId = args.checkjstring(3);
            if (MLuaQuestRegistry.get(runtimeOwner(), ownerModId(), questId) == null) {
                return LuaValue.valueOf(false);
            }
            CompoundTag persistentData = MLuaDataManager.playerPersistentTag(player);
            CompoundTag playerQuests = getCompound(persistentData, NBT_KEY);
            if (!persistentData.contains(NBT_KEY)) {
                persistentData.put(NBT_KEY, playerQuests);
            }
            CompoundTag questData = questData(player, persistentData, playerQuests, questId, true);
            questData.putBoolean("__started", true);
            MLuaDataManager.setPlayerPersistentTag(player, persistentData);
            return LuaValue.valueOf(true);
        }));

        table.set("CompleteQuest", fn((args) -> {
            requireServer();
            ServerPlayer player = extractPlayer(args.arg(2));
            String questId = args.checkjstring(3);
            if (MLuaQuestRegistry.get(runtimeOwner(), ownerModId(), questId) == null) {
                return LuaValue.valueOf(false);
            }
            CompoundTag persistentData = MLuaDataManager.playerPersistentTag(player);
            CompoundTag playerQuests = getCompound(persistentData, NBT_KEY);
            CompoundTag questData = questData(player, persistentData, playerQuests, questId, false);
            if (!getBoolean(questData, "__started")) {
                return LuaValue.valueOf(false);
            }
            questData.putBoolean("__completed", true);
            MLuaDataManager.setPlayerPersistentTag(player, persistentData);
            return LuaValue.valueOf(true);
        }));

        table.set("IsQuestStarted", fn((args) -> {
            requireServer();
            ServerPlayer player = extractPlayer(args.arg(2));
            String questId = args.checkjstring(3);
            CompoundTag persistentData = MLuaDataManager.playerPersistentTag(player);
            CompoundTag questData = questData(player, persistentData,
                getCompound(persistentData, NBT_KEY), questId, false);
            return LuaValue.valueOf(getBoolean(questData, "__started"));
        }));

        table.set("IsQuestCompleted", fn((args) -> {
            requireServer();
            ServerPlayer player = extractPlayer(args.arg(2));
            String questId = args.checkjstring(3);
            CompoundTag persistentData = MLuaDataManager.playerPersistentTag(player);
            CompoundTag questData = questData(player, persistentData,
                getCompound(persistentData, NBT_KEY), questId, false);
            return LuaValue.valueOf(getBoolean(questData, "__completed"));
        }));

        table.set("GetPlayerQuests", fn((args) -> {
            requireServer();
            ServerPlayer player = extractPlayer(args.arg(2));
            CompoundTag persistentData = MLuaDataManager.playerPersistentTag(player);
            CompoundTag allQuests = getCompound(persistentData, NBT_KEY);
            LuaTable result = new LuaTable();
            String prefix = ownerModId().isBlank() ? "" : ownerModId() + ":";
            for (String storedId : new java.util.ArrayList<>(allQuests.getAllKeys())) {
                if (!prefix.isEmpty() && !storedId.startsWith(prefix) && storedId.contains(":")) continue;
                String questId = prefix.isEmpty() ? storedId : (storedId.startsWith(prefix) ? storedId.substring(prefix.length()) : storedId);
                CompoundTag questData = questData(player, persistentData, allQuests, questId, false);
                LuaTable progress = new LuaTable();
                for (String key : questData.getAllKeys()) {
                    if (isBooleanTag(questData, key)) {
                        progress.set(key, LuaValue.valueOf(getBoolean(questData, key)));
                    } else {
                        progress.set(key, LuaValue.valueOf(getDouble(questData, key)));
                    }
                }
                result.set(questId, progress);
            }
            return result;
        }));

        return table;
    }

    private void requireServer() {
        if (this.context.mlua().side() != Side.SERVER) {
            throw new LuaError("[MLua] Quest service methods are server-side only");
        }
    }

    private Object runtimeOwner() { return this.context.mlua().session(); }

    private String ownerModId() {
        String owner = this.context.mlua().ownerModId();
        return owner == null ? "" : owner;
    }

    private String storedQuestId(String questId) {
        return MLuaQuestRegistry.persistentId(ownerModId(), questId);
    }

    private CompoundTag questData(ServerPlayer player, CompoundTag persistentData,
                                  CompoundTag playerQuests, String questId, boolean create) {
      String storedId = storedQuestId(questId);
      if (!playerQuests.contains(storedId) && !storedId.equals(questId) && playerQuests.contains(questId)) {
          playerQuests.put(storedId, getCompound(playerQuests, questId).copy());
          MLuaDataManager.setPlayerPersistentTag(player, persistentData);
      }
        if (!playerQuests.contains(storedId) && create) playerQuests.put(storedId, new CompoundTag());
        return getCompound(playerQuests, storedId);
    }

    //--version 1.20.1,1.21.1
    private static CompoundTag getCompound(CompoundTag tag, String key) { return tag.getCompound(key); }
    private static boolean getBoolean(CompoundTag tag, String key) { return tag.getBoolean(key); }
    private static double getDouble(CompoundTag tag, String key) { return tag.getDouble(key); }
    private static boolean isBooleanTag(CompoundTag tag, String key) { return tag.contains(key, 1); }
    //--endversion
    //--version 26.1
    private static CompoundTag getCompound(CompoundTag tag, String key) { return tag.getCompoundOrEmpty(key); }
    private static boolean getBoolean(CompoundTag tag, String key) { return tag.getBooleanOr(key, false); }
    private static double getDouble(CompoundTag tag, String key) { return tag.getDoubleOr(key, 0.0D); }
    private static boolean isBooleanTag(CompoundTag tag, String key) { return tag.get(key) instanceof ByteTag; }
    //--endversion

    private static ServerPlayer extractPlayer(LuaValue playerVal) {
        LuaTable t = playerVal.checktable();
        LuaValue entityObj = t.get("__entity");
        if (entityObj.isuserdata(Player.class)) {
            return (ServerPlayer) entityObj.checkuserdata(Player.class);
        }
        throw new LuaError("[MLua] Expected a Player table");
    }

    private static VarArgFunction fn(final Function<Varargs, LuaValue> body) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                    return body.apply(args);
                } catch (LuaError e) {
                    throw e;
                } catch (Exception e) {
                    throw new LuaError("[MLua] " + e.getMessage());
                }
            }
        };
    }
}
