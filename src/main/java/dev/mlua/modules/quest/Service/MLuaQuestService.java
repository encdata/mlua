package dev.mlua.modules.quest.Service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.runtime.data.MLuaDataManager;
import dev.mlua.modules.quest.MLuaQuestRegistry;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
//--version 26.1
import net.minecraft.nbt.ByteTag;
//--endversion
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

@MLuaDescription("Service for creating and tracking quests.")
public final class MLuaQuestService extends MLuaReflectiveService {
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
        LuaTable table = super.toLua();
        table.set("__side", "Server");
        return table;
    }

    @MLuaDescription("Creates a quest definition for the calling mod.")
    public LuaTable CreateQuest(String questId, String title, String description) {
        requireServer();
        LuaTable definition = new LuaTable();
        definition.set("Id", questId);
        definition.set("Title", title);
        definition.set("Description", description);
        MLuaQuestRegistry.put(runtimeOwner(), ownerModId(), questId, definition);
        return definition;
    }

    @MLuaDescription("Removes a quest definition owned by the calling mod.")
    public void RemoveQuest(String questId) {
        requireServer();
        MLuaQuestRegistry.remove(runtimeOwner(), ownerModId(), questId);
    }

    @MLuaDescription("Returns a quest definition by identifier.")
    public LuaValue GetQuest(String questId) {
        requireServer();
        LuaTable definition = MLuaQuestRegistry.get(runtimeOwner(), ownerModId(), questId);
        return definition == null ? LuaValue.NIL : definition;
    }

    @MLuaDescription("Returns quest definitions registered by the calling mod.")
    public LuaTable GetQuests() {
        requireServer();
        LuaTable result = new LuaTable();
        for (Map.Entry<MLuaQuestRegistry.Key, LuaTable> entry : MLuaQuestRegistry.snapshot(runtimeOwner(), ownerModId()).entrySet()) {
            result.set(entry.getKey().id(), entry.getValue());
        }
        return result;
    }

    @MLuaDescription("Adds or updates an objective in a quest.")
    public void SetObjective(LuaValue playerValue, String questId, String objectiveKey, LuaValue progress) {
        requireServer();
        ServerPlayer player = extractPlayer(playerValue);
        CompoundTag persistentData = MLuaDataManager.playerPersistentTag(player);
        CompoundTag playerQuests = getCompound(persistentData, NBT_KEY);
        if (!persistentData.contains(NBT_KEY)) persistentData.put(NBT_KEY, playerQuests);
        CompoundTag quest = questData(player, persistentData, playerQuests, questId, true);
        if (progress.isboolean()) {
            quest.putBoolean(objectiveKey, progress.checkboolean());
        } else if (progress.isnumber()) {
            quest.putDouble(objectiveKey, progress.checkdouble());
        }
        MLuaDataManager.setPlayerPersistentTag(player, persistentData);
    }

    @MLuaDescription("Returns one objective from a quest.")
    public LuaValue GetObjective(LuaValue playerValue, String questId, String objectiveKey) {
        requireServer();
        ServerPlayer player = extractPlayer(playerValue);
        CompoundTag persistentData = MLuaDataManager.playerPersistentTag(player);
        CompoundTag quest = questData(player, persistentData, getCompound(persistentData, NBT_KEY), questId, false);
        if (!quest.contains(objectiveKey)) return LuaValue.NIL;
        if (isBooleanTag(quest, objectiveKey)) return LuaValue.valueOf(getBoolean(quest, objectiveKey));
        return LuaValue.valueOf(getDouble(quest, objectiveKey));
    }

    @MLuaDescription("Returns all objectives for a quest.")
    public LuaTable GetObjectives(LuaValue playerValue, String questId) {
        requireServer();
        ServerPlayer player = extractPlayer(playerValue);
        CompoundTag persistentData = MLuaDataManager.playerPersistentTag(player);
        CompoundTag quest = questData(player, persistentData, getCompound(persistentData, NBT_KEY), questId, false);
        LuaTable result = new LuaTable();
        for (String key : quest.getAllKeys()) {
            if (key.equals("__started") || key.equals("__completed")) continue;
            if (isBooleanTag(quest, key)) result.set(key, LuaValue.valueOf(getBoolean(quest, key)));
            else result.set(key, LuaValue.valueOf(getDouble(quest, key)));
        }
        return result;
    }

    @MLuaDescription("Marks a quest as started for a player.")
    public boolean StartQuest(LuaValue playerValue, String questId) {
        requireServer();
        ServerPlayer player = extractPlayer(playerValue);
        if (MLuaQuestRegistry.get(runtimeOwner(), ownerModId(), questId) == null) return false;
        CompoundTag persistentData = MLuaDataManager.playerPersistentTag(player);
        CompoundTag playerQuests = getCompound(persistentData, NBT_KEY);
        if (!persistentData.contains(NBT_KEY)) persistentData.put(NBT_KEY, playerQuests);
        CompoundTag quest = questData(player, persistentData, playerQuests, questId, true);
        quest.putBoolean("__started", true);
        MLuaDataManager.setPlayerPersistentTag(player, persistentData);
        return true;
    }

    @MLuaDescription("Marks a quest as completed for a player.")
    public boolean CompleteQuest(LuaValue playerValue, String questId) {
        requireServer();
        ServerPlayer player = extractPlayer(playerValue);
        if (MLuaQuestRegistry.get(runtimeOwner(), ownerModId(), questId) == null) return false;
        CompoundTag persistentData = MLuaDataManager.playerPersistentTag(player);
        CompoundTag playerQuests = getCompound(persistentData, NBT_KEY);
        CompoundTag quest = questData(player, persistentData, playerQuests, questId, false);
        if (!getBoolean(quest, "__started")) return false;
        quest.putBoolean("__completed", true);
        MLuaDataManager.setPlayerPersistentTag(player, persistentData);
        return true;
    }

    @MLuaDescription("Returns whether a player has started a quest.")
    public boolean IsQuestStarted(LuaValue playerValue, String questId) {
        requireServer();
        ServerPlayer player = extractPlayer(playerValue);
        CompoundTag persistentData = MLuaDataManager.playerPersistentTag(player);
        CompoundTag quest = questData(player, persistentData, getCompound(persistentData, NBT_KEY), questId, false);
        return getBoolean(quest, "__started");
    }

    @MLuaDescription("Returns whether a player has completed a quest.")
    public boolean IsQuestCompleted(LuaValue playerValue, String questId) {
        requireServer();
        ServerPlayer player = extractPlayer(playerValue);
        CompoundTag persistentData = MLuaDataManager.playerPersistentTag(player);
        CompoundTag quest = questData(player, persistentData, getCompound(persistentData, NBT_KEY), questId, false);
        return getBoolean(quest, "__completed");
    }

    @MLuaDescription("Returns quest progress recorded for a player.")
    public LuaTable GetPlayerQuests(LuaValue playerValue) {
        requireServer();
        ServerPlayer player = extractPlayer(playerValue);
        CompoundTag persistentData = MLuaDataManager.playerPersistentTag(player);
        CompoundTag allQuests = getCompound(persistentData, NBT_KEY);
        LuaTable result = new LuaTable();
        String prefix = ownerModId().isBlank() ? "" : ownerModId() + ":";
        for (String storedId : new java.util.ArrayList<>(allQuests.getAllKeys())) {
            if (!prefix.isEmpty() && !storedId.startsWith(prefix) && storedId.contains(":")) continue;
            String questId = prefix.isEmpty() ? storedId : (storedId.startsWith(prefix) ? storedId.substring(prefix.length()) : storedId);
            CompoundTag quest = questData(player, persistentData, allQuests, questId, false);
            LuaTable progress = new LuaTable();
            for (String key : quest.getAllKeys()) {
                if (isBooleanTag(quest, key)) progress.set(key, LuaValue.valueOf(getBoolean(quest, key)));
                else progress.set(key, LuaValue.valueOf(getDouble(quest, key)));
            }
            result.set(questId, progress);
        }
        return result;
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

}
