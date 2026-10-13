package dev.mlua.modules.quest;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.common.enums.Side;
import dev.mlua.modules.quest.Service.MLuaQuestService;
import java.util.List;

public class QuestModule implements MLuaModule {
    @Override
    public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
        if (side == Side.CLIENT) return;
        MLuaQuestRegistry.clearRuntime(runtimeSession);
    }

    @Override
    public void clearOwnedState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, String ownerModId) {
        MLuaQuestRegistry.clearOwned(runtimeSession, ownerModId);
    }

    @Override
    public String id() { return "quest"; }

    @Override
    public int priority() { return 12; }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder quest = ServiceEntry.builder("Quest", MLuaQuestService::new)
            .description("Service for creating and tracking quests.");
        quest.function("CreateQuest", "Creates a quest definition for the calling mod.")
            .function("RemoveQuest", "Removes a quest definition owned by the calling mod.")
            .function("GetQuest", "Returns a quest definition by identifier.")
            .function("GetQuests", "Returns quest definitions registered by the calling mod.")
            .function("SetObjective", "Adds or updates an objective in a quest.")
            .function("GetObjective", "Returns one objective from a quest.")
            .function("GetObjectives", "Returns all objectives for a quest.")
            .function("StartQuest", "Marks a quest as started for a player.")
            .function("CompleteQuest", "Marks a quest as completed for a player.")
            .function("IsQuestStarted", "Returns whether a player has started a quest.")
            .function("IsQuestCompleted", "Returns whether a player has completed a quest.")
            .function("GetPlayerQuests", "Returns quest progress recorded for a player.");
        return List.of(quest.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("Quest", "MLuaQuestService",
                "Service for creating and tracking quests.",
                "services/quest.html");
    }
}
