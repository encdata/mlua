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
            .reflective(MLuaLootTableService.class);
        return List.of(loot.build());
    }
    @Override public void registerClasses(MLuaRegistration registration) {
        MLuaLootTableService.registerBuilderClass(registration);
    }
    @Override public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.service("LootTable", "MLuaLootTableService", "Builds loader-independent Minecraft loot table definitions for generated loot resources.", "services/loot-table.html"));
        docs.register(MLuaDocBuilder.Registration.clazz("LootTableBuilder", "MLuaLootTableService",
            "A live builder for composing a loot table from pools, item entries, and roll settings.",
            "classes/loot-table-builder.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.method("LootTableBuilder", "LootTableBuilder:SelectPool(index: whole number >= 1)",
            "Selects the one-based pool that receives subsequent entry and roll changes.", "index: one-based pool index", "LootTableBuilder", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("LootTableBuilder", "LootTableBuilder:AddEntry(itemId: string, weight: whole number >= 1, quality: whole number?)",
            "Adds an item entry to the selected pool.", "itemId: item identifier; weight: positive weight; quality: optional quality", "LootTableBuilder", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("LootTableBuilder", "LootTableBuilder:AddTagEntry(tagId: string, weight?: whole number >= 1, quality?: whole number, expand?: boolean)",
            "Adds a weighted entry that expands an item tag into its matching items.", "tagId: item tag identifier; expand controls whether each matching item is considered separately", "LootTableBuilder", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("LootTableBuilder", "LootTableBuilder:AddEmptyEntry(weight?: whole number >= 1, quality?: whole number)",
            "Adds a weighted empty outcome to the selected pool.", "weight: optional positive weight; quality: optional quality", "LootTableBuilder", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("LootTableBuilder", "LootTableBuilder:SetEntryCount(min: whole number >= 0, max?: whole number >= min)",
            "Sets the most recently added item or tag entry's item count or random count range.", "minimum count and optional maximum count", "LootTableBuilder", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("LootTableBuilder", "LootTableBuilder:SetRolls(min: whole number >= 0, max?: whole number >= min)",
            "Sets the selected pool's minimum and optional maximum rolls.", "min: minimum rolls; max: optional maximum rolls", "LootTableBuilder", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("LootTableBuilder", "LootTableBuilder:SetBonusRolls(min: whole number >= 0, max?: whole number >= min)",
            "Sets the selected pool's minimum and optional maximum bonus rolls.", "min: minimum bonus rolls; max: optional maximum", "LootTableBuilder", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("LootTableBuilder", "LootTableBuilder:Build()",
            "Serializes the current loot table definition as JSON.", null, "string", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.signature("LootTable", "LootTable:Create(poolCount: number)", "LootTableBuilder"));
        docs.register(MLuaDocBuilder.Registration.signature("LootTable", "LootTable:Empty()", "string"));
        docs.register(MLuaDocBuilder.Registration.guideStep("LootTable", "Compose multiple loot pools",
            "Create returns a live LootTableBuilder instance. Its methods update that instance's state and return it so calls can be chained.",
            "local LootTable = Minecraft:GetService(\"LootTable\")\nlocal builder = LootTable:Create(2)\n\nbuilder:AddEntry(\"minecraft:rotten_flesh\", 4)\n    :SelectPool(2)\n    :SetRolls(1, 3)\n    :AddEntry(\"minecraft:iron_ingot\", 1, 2)\n\nprint(builder:IsA(\"LootTableBuilder\"))\nprint(builder:Build())"));
        docs.register(MLuaDocBuilder.Registration.guideStep("LootTable", "Use item tags and empty outcomes",
            "Tag entries let datapack tags control eligible drops. SetEntryCount applies a fixed or uniform count to the latest item or tag entry. Empty entries give a pool a weighted chance to produce no item. Register the built JSON under the custom entity's loot table id.",
            "local LootTable = Minecraft:GetService(\"LootTable\")\nlocal Registry = Minecraft:GetService(\"Registry\")\nlocal drops = LootTable:Create()\n\ndrops:AddTagEntry(\"minecraft:arrows\", 3, 0, true)\n    :SetEntryCount(1, 4)\n    :AddEmptyEntry(1)\n\nRegistry.Loot:RegisterEntity(\"stalker\", drops:Build())"));
    }
}
