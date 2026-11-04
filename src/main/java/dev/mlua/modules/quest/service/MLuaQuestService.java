package dev.mlua.modules.quest.service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaServerThreadReflectiveService;
import dev.mlua.modules.quest.MLuaQuestRegistry;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

@MLuaDescription("Service for creating and tracking quests.")
public final class MLuaQuestService extends MLuaServerThreadReflectiveService {
    public static final MLuaSignal QUEST_STARTED = new MLuaSignal();
    public static final MLuaSignal QUEST_COMPLETED = new MLuaSignal();
    public static final MLuaSignal OBJECTIVE_UPDATED = new MLuaSignal();

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
        String owner = this.context.mlua().ownerModId();
        table.set("QuestStarted", MLuaLuaBindings.signalInstance(QUEST_STARTED,
            this.context.mlua().globals(), this.context.mlua().scheduler(), owner, Side.SERVER));
        table.set("QuestCompleted", MLuaLuaBindings.signalInstance(QUEST_COMPLETED,
            this.context.mlua().globals(), this.context.mlua().scheduler(), owner, Side.SERVER));
        table.set("ObjectiveUpdated", MLuaLuaBindings.signalInstance(OBJECTIVE_UPDATED,
            this.context.mlua().globals(), this.context.mlua().scheduler(), owner, Side.SERVER));
        return table;
    }

    @MLuaDescription("Creates a quest definition for the calling mod and returns a snapshot of the stored definition.")
    public LuaTable CreateQuest(String questId, String title, String description) {
        this.requireServerSide(this.name());
        LuaTable definition = new LuaTable();
        definition.set("Id", questId);
        definition.set("Title", title);
        definition.set("Description", description);
        MLuaQuestRegistry.put(runtimeOwner(), ownerModId(), questId, definition);
        return MLuaQuestRegistry.get(runtimeOwner(), ownerModId(), questId);
    }

    @MLuaDescription("Removes a quest definition owned by the calling mod.")
    public void RemoveQuest(String questId) {
        this.requireServerSide(this.name());
        MLuaQuestRegistry.remove(runtimeOwner(), ownerModId(), questId);
    }

    @MLuaDescription("Returns a quest definition by identifier.")
    public LuaValue GetQuest(String questId) {
        this.requireServerSide(this.name());
        LuaTable definition = MLuaQuestRegistry.get(runtimeOwner(), ownerModId(), questId);
        return definition == null ? LuaValue.NIL : definition;
    }

    @MLuaDescription("Returns quest definitions registered by the calling mod.")
    public LuaTable GetQuests() {
        this.requireServerSide(this.name());
        LuaTable result = new LuaTable();
        java.util.List<Map.Entry<MLuaQuestRegistry.Key, LuaTable>> quests = new java.util.ArrayList<>(
            MLuaQuestRegistry.snapshot(runtimeOwner(), ownerModId()).entrySet());
        quests.sort(java.util.Comparator.comparing(entry -> entry.getKey().id()));
        for (Map.Entry<MLuaQuestRegistry.Key, LuaTable> entry : quests) {
            result.set(entry.getKey().id(), entry.getValue());
        }
        return result;
    }

    @MLuaDescription("Adds or updates a boolean or finite numeric objective for a registered quest. Returns false when the quest is unknown.")
    public boolean SetObjective(LuaValue playerValue, String questId, String objectiveKey, LuaValue progress) {
        this.requireServerSide(this.name());
        if (MLuaQuestRegistry.get(runtimeOwner(), ownerModId(), questId) == null) return false;
        if (objectiveKey == null || objectiveKey.isBlank() || objectiveKey.equals("__started")
                || objectiveKey.equals("__completed")) {
            throw new LuaError("[MLua] Quest:SetObjective expects a non-empty objective key other than __started or __completed.");
        }
        if (!progress.isboolean() && !progress.isnumber()) {
            throw new LuaError("[MLua] Quest:SetObjective progress must be a boolean or finite number.");
        }
        if (progress.isnumber() && !Double.isFinite(progress.todouble())) {
            throw new LuaError("[MLua] Quest:SetObjective progress must be a boolean or finite number.");
        }
        ServerPlayer player = extractPlayer(playerValue);
        MLuaQuestProgressStore.setObjective(player, ownerModId(), questId, objectiveKey, progress);
        fireEvent(OBJECTIVE_UPDATED, player, questId, objectiveKey, progress);
        return true;
    }

    @MLuaDescription("Returns one objective from a quest.")
    public LuaValue GetObjective(LuaValue playerValue, String questId, String objectiveKey) {
        this.requireServerSide(this.name());
        ServerPlayer player = extractPlayer(playerValue);
        return MLuaQuestProgressStore.objective(player, ownerModId(), questId, objectiveKey);
    }

    @MLuaDescription("Returns all objectives for a quest.")
    public LuaTable GetObjectives(LuaValue playerValue, String questId) {
        this.requireServerSide(this.name());
        ServerPlayer player = extractPlayer(playerValue);
        return MLuaQuestProgressStore.objectives(player, ownerModId(), questId);
    }

    @MLuaDescription("Marks a quest as started for a player.")
    public boolean StartQuest(LuaValue playerValue, String questId) {
        this.requireServerSide(this.name());
        ServerPlayer player = extractPlayer(playerValue);
        if (MLuaQuestRegistry.get(runtimeOwner(), ownerModId(), questId) == null) return false;
        if (MLuaQuestProgressStore.start(player, ownerModId(), questId) == MLuaQuestProgressStore.Update.CHANGED) {
            fireEvent(QUEST_STARTED, player, questId, null, LuaValue.NIL);
        }
        return true;
    }

    @MLuaDescription("Marks a quest as completed for a player.")
    public boolean CompleteQuest(LuaValue playerValue, String questId) {
        this.requireServerSide(this.name());
        ServerPlayer player = extractPlayer(playerValue);
        if (MLuaQuestRegistry.get(runtimeOwner(), ownerModId(), questId) == null) return false;
        MLuaQuestProgressStore.Update result = MLuaQuestProgressStore.complete(player, ownerModId(), questId);
        if (result == MLuaQuestProgressStore.Update.REJECTED) return false;
        if (result == MLuaQuestProgressStore.Update.CHANGED) {
            fireEvent(QUEST_COMPLETED, player, questId, null, LuaValue.NIL);
        }
        return true;
    }

    @MLuaDescription("Returns whether a player has started a quest.")
    public boolean IsQuestStarted(LuaValue playerValue, String questId) {
        this.requireServerSide(this.name());
        ServerPlayer player = extractPlayer(playerValue);
        return MLuaQuestProgressStore.isStarted(player, ownerModId(), questId);
    }

    @MLuaDescription("Returns whether a player has completed a quest.")
    public boolean IsQuestCompleted(LuaValue playerValue, String questId) {
        this.requireServerSide(this.name());
        ServerPlayer player = extractPlayer(playerValue);
        return MLuaQuestProgressStore.isCompleted(player, ownerModId(), questId);
    }

    @MLuaDescription("Returns quest progress recorded for a player.")
    public LuaTable GetPlayerQuests(LuaValue playerValue) {
        this.requireServerSide(this.name());
        ServerPlayer player = extractPlayer(playerValue);
        return MLuaQuestProgressStore.playerQuests(player, ownerModId());
    }

    private Object runtimeOwner() { return this.context.mlua().session(); }

    private void fireEvent(MLuaSignal signal, ServerPlayer player, String questId,
                           String objectiveKey, LuaValue progress) {
        LuaTable event = new LuaTable();
        event.set("Player", dev.mlua.modules.players.service.MLuaPlayersService.playerTable(
            player, this.context.mlua().registration()));
        event.set("QuestId", questId);
        if (objectiveKey != null) {
            event.set("ObjectiveKey", objectiveKey);
            event.set("Value", progress);
        }
        Object session = this.context.mlua().session();
        if (session instanceof MLuaRuntimeSession runtimeSession) {
            signal.fireOnSession(runtimeSession, Side.SERVER, event);
        } else {
            signal.fireOnSide(Side.SERVER, event);
        }
    }

    private String ownerModId() {
        String owner = this.context.mlua().ownerModId();
        return owner == null ? "" : owner;
    }

    private ServerPlayer extractPlayer(LuaValue playerVal) {
        return (ServerPlayer) MLuaEntityWrapper.requireLuaInstance(playerVal, context.mlua().registration(), "Player", "Player");
    }

}
