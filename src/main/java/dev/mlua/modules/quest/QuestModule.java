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
        return List.of(ServiceEntry.builder("Quest", MLuaQuestService::new)
            .reflective(MLuaQuestService.class)
            .build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.service("Quest", "MLuaQuestService",
                "Service for creating and tracking quests.",
                "services/quest.html"));
        docs.register(MLuaDocBuilder.Registration.signature("Quest", "Quest:SetObjective(player: Player, questId: string, objectiveKey: string, progress: boolean|number)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.event("Quest", "Quest.QuestStarted", "event: { Player: Player, QuestId: string }",
            "Fires once when a player starts a registered quest.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.event("Quest", "Quest.QuestCompleted", "event: { Player: Player, QuestId: string }",
            "Fires once when a started quest is completed.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.event("Quest", "Quest.ObjectiveUpdated", "event: { Player: Player, QuestId: string, ObjectiveKey: string, Value: boolean|number }",
            "Fires after a registered quest objective is persisted for a player.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Quest", "React to saved quest progress",
            "Quest signals fire on the server after progress is saved. Each event includes the live Player instance and the quest id; ObjectiveUpdated also includes the objective key and value.",
            "local Quest = Minecraft:GetService(\"Quest\")\n\nQuest.ObjectiveUpdated:Connect(function(event)\n    if event.QuestId == \"haunted_mine\" and event.ObjectiveKey == \"keys_found\" then\n        print(event.Player.Name, event.Value)\n    end\nend)"));
    }
}
