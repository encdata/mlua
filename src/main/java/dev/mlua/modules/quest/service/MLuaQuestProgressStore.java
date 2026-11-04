package dev.mlua.modules.quest.service;

import dev.mlua.modules.quest.MLuaQuestRegistry;
import dev.mlua.runtime.data.MLuaDataManager;
import java.util.ArrayList;
import net.minecraft.nbt.CompoundTag;
//--version 26.1
import net.minecraft.nbt.ByteTag;
//--endversion
import net.minecraft.server.level.ServerPlayer;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Owns persisted, addon-scoped quest progress for server players. */
final class MLuaQuestProgressStore {
    private static final String NBT_KEY = "mlua_quests";
    private static final String STARTED_KEY = "__started";
    private static final String COMPLETED_KEY = "__completed";

    enum Update { REJECTED, UNCHANGED, CHANGED }

    private MLuaQuestProgressStore() { }

    static void setObjective(ServerPlayer player, String owner, String questId,
                             String objectiveKey, LuaValue progress) {
        CompoundTag persistentData = MLuaDataManager.playerPersistentTag(player);
        CompoundTag playerQuests = getCompound(persistentData, NBT_KEY);
        if (!persistentData.contains(NBT_KEY)) persistentData.put(NBT_KEY, playerQuests);
        CompoundTag quest = questData(player, persistentData, playerQuests, owner, questId, true);
        if (progress.isboolean()) quest.putBoolean(objectiveKey, progress.checkboolean());
        else quest.putDouble(objectiveKey, progress.checkdouble());
        MLuaDataManager.setPlayerPersistentTag(player, persistentData);
    }

    static LuaValue objective(ServerPlayer player, String owner, String questId, String objectiveKey) {
        CompoundTag persistentData = MLuaDataManager.playerPersistentTag(player);
        CompoundTag quest = questData(player, persistentData, getCompound(persistentData, NBT_KEY),
            owner, questId, false);
        if (!quest.contains(objectiveKey)) return LuaValue.NIL;
        return isBooleanTag(quest, objectiveKey)
            ? LuaValue.valueOf(getBoolean(quest, objectiveKey))
            : LuaValue.valueOf(getDouble(quest, objectiveKey));
    }

    static LuaTable objectives(ServerPlayer player, String owner, String questId) {
        CompoundTag persistentData = MLuaDataManager.playerPersistentTag(player);
        CompoundTag quest = questData(player, persistentData, getCompound(persistentData, NBT_KEY),
            owner, questId, false);
        LuaTable result = new LuaTable();
        for (String key : dev.mlua.mc.impl.nbt.MCNbtAccess.keys(quest)) {
            if (STARTED_KEY.equals(key) || COMPLETED_KEY.equals(key)) continue;
            if (isBooleanTag(quest, key)) result.set(key, LuaValue.valueOf(getBoolean(quest, key)));
            else result.set(key, LuaValue.valueOf(getDouble(quest, key)));
        }
        return result;
    }

    static Update start(ServerPlayer player, String owner, String questId) {
        CompoundTag persistentData = MLuaDataManager.playerPersistentTag(player);
        CompoundTag playerQuests = getCompound(persistentData, NBT_KEY);
        if (!persistentData.contains(NBT_KEY)) persistentData.put(NBT_KEY, playerQuests);
        CompoundTag quest = questData(player, persistentData, playerQuests, owner, questId, true);
        if (getBoolean(quest, STARTED_KEY)) return Update.UNCHANGED;
        quest.putBoolean(STARTED_KEY, true);
        MLuaDataManager.setPlayerPersistentTag(player, persistentData);
        return Update.CHANGED;
    }

    static Update complete(ServerPlayer player, String owner, String questId) {
        CompoundTag persistentData = MLuaDataManager.playerPersistentTag(player);
        CompoundTag playerQuests = getCompound(persistentData, NBT_KEY);
        CompoundTag quest = questData(player, persistentData, playerQuests, owner, questId, false);
        if (!getBoolean(quest, STARTED_KEY)) return Update.REJECTED;
        if (getBoolean(quest, COMPLETED_KEY)) return Update.UNCHANGED;
        quest.putBoolean(COMPLETED_KEY, true);
        MLuaDataManager.setPlayerPersistentTag(player, persistentData);
        return Update.CHANGED;
    }

    static boolean isStarted(ServerPlayer player, String owner, String questId) {
        return getBoolean(questFor(player, owner, questId), STARTED_KEY);
    }

    static boolean isCompleted(ServerPlayer player, String owner, String questId) {
        return getBoolean(questFor(player, owner, questId), COMPLETED_KEY);
    }

    static LuaTable playerQuests(ServerPlayer player, String owner) {
        CompoundTag persistentData = MLuaDataManager.playerPersistentTag(player);
        CompoundTag allQuests = getCompound(persistentData, NBT_KEY);
        LuaTable result = new LuaTable();
        String prefix = owner == null || owner.isBlank() ? "" : owner + ":";
        for (String storedId : new ArrayList<>(dev.mlua.mc.impl.nbt.MCNbtAccess.keys(allQuests))) {
            if (!prefix.isEmpty() && !storedId.startsWith(prefix) && storedId.contains(":")) continue;
            String questId = prefix.isEmpty() ? storedId
                : storedId.startsWith(prefix) ? storedId.substring(prefix.length()) : storedId;
            CompoundTag quest = questData(player, persistentData, allQuests, owner, questId, false);
            LuaTable progress = new LuaTable();
            for (String key : dev.mlua.mc.impl.nbt.MCNbtAccess.keys(quest)) {
                if (isBooleanTag(quest, key)) progress.set(key, LuaValue.valueOf(getBoolean(quest, key)));
                else progress.set(key, LuaValue.valueOf(getDouble(quest, key)));
            }
            result.set(questId, progress);
        }
        return result;
    }

    private static CompoundTag questFor(ServerPlayer player, String owner, String questId) {
        CompoundTag persistentData = MLuaDataManager.playerPersistentTag(player);
        return questData(player, persistentData, getCompound(persistentData, NBT_KEY), owner, questId, false);
    }

    private static CompoundTag questData(ServerPlayer player, CompoundTag persistentData,
                                         CompoundTag playerQuests, String owner, String questId,
                                         boolean create) {
        String storedId = MLuaQuestRegistry.persistentId(owner, questId);
        if (!playerQuests.contains(storedId) && !storedId.equals(questId) && playerQuests.contains(questId)) {
            playerQuests.put(storedId, getCompound(playerQuests, questId).copy());
            MLuaDataManager.setPlayerPersistentTag(player, persistentData);
        }
        if (!playerQuests.contains(storedId) && create) playerQuests.put(storedId, new CompoundTag());
        return getCompound(playerQuests, storedId);
    }

    //--version 1.20.1,1.21.1
    private static boolean getBoolean(CompoundTag tag, String key) { return tag.getBoolean(key); }
    private static boolean isBooleanTag(CompoundTag tag, String key) { return tag.contains(key, 1); }
    //--endversion
    //--version 26.1
    private static boolean getBoolean(CompoundTag tag, String key) { return tag.getBooleanOr(key, false); }
    private static boolean isBooleanTag(CompoundTag tag, String key) { return tag.get(key) instanceof ByteTag; }
    //--endversion

    private static CompoundTag getCompound(CompoundTag tag, String key) {
        return dev.mlua.mc.impl.nbt.MCNbtAccess.compound(tag, key);
    }

    private static double getDouble(CompoundTag tag, String key) {
        return dev.mlua.mc.impl.nbt.MCNbtAccess.decimal(tag, key);
    }
}
