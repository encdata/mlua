package dev.mlua.modules.quest.Service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class MLuaQuestService extends MLuaServiceBase {
    private static final ConcurrentHashMap<String, LuaTable> quests = new ConcurrentHashMap<>();
    private static final Map<UUID, CompoundTag> PLAYER_DATA = new ConcurrentHashMap<>();
    private static final String NBT_KEY = "mlua_quests";

    public static void DestroyAll() {
        quests.clear();
    }
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
            quests.put(questId, def);
            return def;
        }));

        table.set("RemoveQuest", fn((args) -> {
            requireServer();
            quests.remove(args.checkjstring(2));
            return LuaValue.NONE;
        }));

        table.set("GetQuest", fn((args) -> {
            requireServer();
            LuaTable def = quests.get(args.checkjstring(2));
            return def != null ? def : LuaValue.NIL;
        }));

        table.set("GetQuests", fn((args) -> {
            requireServer();
            LuaTable result = new LuaTable();
            for (ConcurrentHashMap.Entry<String, LuaTable> entry : quests.entrySet()) {
                result.set(entry.getKey(), entry.getValue());
            }
            return result;
        }));

        table.set("SetObjective", fn((args) -> {
            requireServer();
            ServerPlayer player = extractPlayer(args.arg(2));
            String questId = args.checkjstring(3);
            String objectiveKey = args.checkjstring(4);
            LuaValue progress = args.arg(5);
            CompoundTag persistentData = PLAYER_DATA.computeIfAbsent(player.getUUID(), k -> new CompoundTag());
            CompoundTag playerQuests = persistentData.getCompound(NBT_KEY);
            CompoundTag questData = playerQuests.getCompound(questId);
            if (progress.isboolean()) {
                questData.putBoolean(objectiveKey, progress.checkboolean());
            } else if (progress.isnumber()) {
                questData.putDouble(objectiveKey, progress.checkdouble());
            }
            playerQuests.put(questId, questData);
            persistentData.put(NBT_KEY, playerQuests);
            return LuaValue.NONE;
        }));

        table.set("GetObjective", fn((args) -> {
            requireServer();
            ServerPlayer player = extractPlayer(args.arg(2));
            String questId = args.checkjstring(3);
            String objectiveKey = args.checkjstring(4);
            CompoundTag questData = PLAYER_DATA.computeIfAbsent(player.getUUID(), k -> new CompoundTag()).getCompound(NBT_KEY).getCompound(questId);
            if (!questData.contains(objectiveKey)) {
                return LuaValue.NIL;
            }
            if (questData.contains(objectiveKey, 1)) {
                return LuaValue.valueOf(questData.getBoolean(objectiveKey));
            }
            return LuaValue.valueOf(questData.getDouble(objectiveKey));
        }));

        table.set("GetObjectives", fn((args) -> {
            requireServer();
            ServerPlayer player = extractPlayer(args.arg(2));
            String questId = args.checkjstring(3);
            CompoundTag questData = PLAYER_DATA.computeIfAbsent(player.getUUID(), k -> new CompoundTag()).getCompound(NBT_KEY).getCompound(questId);
            LuaTable result = new LuaTable();
            for (String key : questData.getAllKeys()) {
                if (key.equals("__started") || key.equals("__completed")) continue;
                if (questData.contains(key, 1)) {
                    result.set(key, LuaValue.valueOf(questData.getBoolean(key)));
                } else {
                    result.set(key, LuaValue.valueOf(questData.getDouble(key)));
                }
            }
            return result;
        }));

        table.set("StartQuest", fn((args) -> {
            requireServer();
            ServerPlayer player = extractPlayer(args.arg(2));
            String questId = args.checkjstring(3);
            if (!quests.containsKey(questId)) {
                return LuaValue.valueOf(false);
            }
            CompoundTag persistentData = PLAYER_DATA.computeIfAbsent(player.getUUID(), k -> new CompoundTag());
            CompoundTag playerQuests = persistentData.getCompound(NBT_KEY);
            CompoundTag questData = playerQuests.getCompound(questId);
            questData.putBoolean("__started", true);
            playerQuests.put(questId, questData);
            persistentData.put(NBT_KEY, playerQuests);
            return LuaValue.valueOf(true);
        }));

        table.set("CompleteQuest", fn((args) -> {
            requireServer();
            ServerPlayer player = extractPlayer(args.arg(2));
            String questId = args.checkjstring(3);
            if (!quests.containsKey(questId)) {
                return LuaValue.valueOf(false);
            }
            CompoundTag persistentData = PLAYER_DATA.computeIfAbsent(player.getUUID(), k -> new CompoundTag());
            CompoundTag playerQuests = persistentData.getCompound(NBT_KEY);
            CompoundTag questData = playerQuests.getCompound(questId);
            if (!questData.getBoolean("__started")) {
                return LuaValue.valueOf(false);
            }
            questData.putBoolean("__completed", true);
            playerQuests.put(questId, questData);
            persistentData.put(NBT_KEY, playerQuests);
            return LuaValue.valueOf(true);
        }));

        table.set("IsQuestStarted", fn((args) -> {
            requireServer();
            ServerPlayer player = extractPlayer(args.arg(2));
            String questId = args.checkjstring(3);
            CompoundTag questData = PLAYER_DATA.computeIfAbsent(player.getUUID(), k -> new CompoundTag()).getCompound(NBT_KEY).getCompound(questId);
            return LuaValue.valueOf(questData.getBoolean("__started"));
        }));

        table.set("IsQuestCompleted", fn((args) -> {
            requireServer();
            ServerPlayer player = extractPlayer(args.arg(2));
            String questId = args.checkjstring(3);
            CompoundTag questData = PLAYER_DATA.computeIfAbsent(player.getUUID(), k -> new CompoundTag()).getCompound(NBT_KEY).getCompound(questId);
            return LuaValue.valueOf(questData.getBoolean("__completed"));
        }));

        table.set("GetPlayerQuests", fn((args) -> {
            requireServer();
            ServerPlayer player = extractPlayer(args.arg(2));
            CompoundTag allQuests = PLAYER_DATA.computeIfAbsent(player.getUUID(), k -> new CompoundTag()).getCompound(NBT_KEY);
            LuaTable result = new LuaTable();
            for (String questId : allQuests.getAllKeys()) {
                CompoundTag questData = allQuests.getCompound(questId);
                LuaTable progress = new LuaTable();
                for (String key : questData.getAllKeys()) {
                    if (questData.contains(key, 1)) {
                        progress.set(key, LuaValue.valueOf(questData.getBoolean(key)));
                    } else {
                        progress.set(key, LuaValue.valueOf(questData.getDouble(key)));
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
