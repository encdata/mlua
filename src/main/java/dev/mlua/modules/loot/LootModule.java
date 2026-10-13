package dev.mlua.modules.loot;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.loot.Service.MLuaLootTableService;
import java.util.List;

public class LootModule implements MLuaModule {
    @Override public String id() { return "loot"; }
    @Override public int priority() { return 12; }
    @Override public List<String> dependencies() { return List.of("core-classes"); }
    @Override public List<ServiceEntry> services() {
        ServiceEntry.Builder loot = ServiceEntry.builder("LootTable", MLuaLootTableService::new)
            .description("Service for creating loot table builders.");
        loot.function("Create", "Creates a live LootTableBuilder with the requested number of pools.")
            .function("Empty", "Returns the serialized JSON for an empty loot table.");
        return List.of(loot.build());
    }
    @Override public void registerClasses(MLuaRegistration registration) {
        MLuaLootTableService.registerBuilderClass(registration);
    }
    @Override public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("LootTable", "MLuaLootTableService", "Service for creating loot table builders.", "services/loot-table.html");
        docs.clazz("LootTableBuilder", "MLuaLootTableService",
            "A live builder for composing a loot table from pools, item entries, and roll settings.",
            "classes/loot-table-builder.html", "Instance");
        docs.method("LootTableBuilder", "LootTableBuilder:SelectPool(index: number)",
            "Selects the one-based pool that receives subsequent entry and roll changes.", "index: one-based pool index", "LootTableBuilder", "shared:Shared", null);
        docs.method("LootTableBuilder", "LootTableBuilder:AddEntry(itemId: string, weight: number, quality: number?)",
            "Adds an item entry to the selected pool.", "itemId: item identifier; weight: positive weight; quality: optional quality", "LootTableBuilder", "shared:Shared", null);
        docs.method("LootTableBuilder", "LootTableBuilder:SetRolls(min: number, max: number?)",
            "Sets the selected pool's minimum and optional maximum rolls.", "min: minimum rolls; max: optional maximum rolls", "LootTableBuilder", "shared:Shared", null);
        docs.method("LootTableBuilder", "LootTableBuilder:SetBonusRolls(min: number, max: number?)",
            "Sets the selected pool's minimum and optional maximum bonus rolls.", "min: minimum bonus rolls; max: optional maximum", "LootTableBuilder", "shared:Shared", null);
        docs.method("LootTableBuilder", "LootTableBuilder:Build()",
            "Serializes the current loot table definition as JSON.", null, "string", "shared:Shared", null);
        docs.signature("LootTable", "LootTable:Create(poolCount: number)", "LootTableBuilder");
        docs.signature("LootTable", "LootTable:Empty()", "string");
        docs.guideStep("LootTable", "Compose multiple loot pools",
            "Create returns a live LootTableBuilder instance. Its methods update that instance's state and return it so calls can be chained.",
            "local LootTable = Minecraft:GetService(\"LootTable\")\nlocal builder = LootTable:Create(2)\n\nbuilder:AddEntry(\"minecraft:rotten_flesh\", 4)\n    :SelectPool(2)\n    :SetRolls(1, 3)\n    :AddEntry(\"minecraft:iron_ingot\", 1, 2)\n\nprint(builder:IsA(\"LootTableBuilder\"))\nprint(builder:Build())");
    }
}
