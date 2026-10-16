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
    }
}
