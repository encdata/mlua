package dev.mlua.modules.advancements;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.modules.advancements.Service.MLuaAdvancementService;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import java.util.List;

public final class AdvancementsModule implements MLuaModule {
    @Override
    public String id() {
        return "advancements";
    }

    @Override
    public List<String> dependencies() { return List.of("core-classes"); }

    @Override
    public int priority() { return 12; }

    @Override
    public List<ServiceEntry> services() {
        return List.of(ServiceEntry.builder("Advancements", MLuaAdvancementService::new)
            .reflective(MLuaAdvancementService.class)
            .build());
    }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        registration.registerClass("Advancement", registration.requireInstanceBaseClassName(),
            "A typed view of a registered Minecraft advancement and its display requirements.");
        property(registration, "Advancement", "Id", "string", "Returns the advancement identifier.");
        property(registration, "Advancement", "Title", "string", "Returns the displayed advancement title.");
        property(registration, "Advancement", "Description", "string", "Returns the displayed advancement description.");
        property(registration, "Advancement", "Parent", "string?", "Returns the parent advancement identifier, or nil for a root advancement.");
        property(registration, "Advancement", "Requirements", "string[][]", "Returns requirement groups; each group contains criteria that can satisfy that requirement.");

        registration.registerClass("AdvancementProgress", registration.requireInstanceBaseClassName(),
            "A player's completion state and criterion progress for one advancement.");
        property(registration, "AdvancementProgress", "Id", "string", "Returns the advancement identifier.");
        property(registration, "AdvancementProgress", "IsDone", "boolean", "Returns whether all advancement requirements are complete.");
        property(registration, "AdvancementProgress", "Criteria", "table<string, CriterionProgress>", "Returns progress for each criterion.");
        registration.registerClassFunction("AdvancementProgress", "GetCriterion", new org.luaj.vm2.lib.VarArgFunction() {
            @Override public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                return args.checktable(1).get("Criteria").get(args.checkjstring(2));
            }
        }, "Returns criterion progress by criterion name.");
        registration.registerClassFunction("AdvancementProgress", "HasCompletedCriterion", new org.luaj.vm2.lib.VarArgFunction() {
            @Override public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                LuaValue criterion = args.checktable(1).get("Criteria").get(args.checkjstring(2));
                return LuaValue.valueOf(criterion.istable() && criterion.get("Done").toboolean());
            }
        }, "Checks whether the named criterion has been completed.");
    }

    private static void property(MLuaRegistration registration, String className, String name,
                                 String valueType, String description) {
        registration.registerClassFunction(className, "Get" + name, new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return self.checktable().get(name); }
        }, description);
        MLuaClassMethods.registerInstanceProperty(registration, className, name, "Get" + name, null,
            valueType, "server:Server", description);
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("Advancements", "MLuaAdvancementService",
                "Provides server-side advancement lookup and progress control.",
                "services/advancements.html");
        docs.clazz("Advancement", "MLuaAdvancementService", "A typed view of a registered Minecraft advancement and its display requirements.", "classes/advancement.html", "Instance");
        docs.property("Advancement", "Advancement.Id", "string", "The advancement identifier.", "read:Read");
        docs.property("Advancement", "Advancement.Title", "string", "The displayed advancement title.", "read:Read");
        docs.property("Advancement", "Advancement.Description", "string", "The displayed advancement description.", "read:Read");
        docs.property("Advancement", "Advancement.Parent", "string?", "The parent advancement identifier, or nil for a root advancement.", "read:Read");
        docs.property("Advancement", "Advancement.Requirements", "string[][]", "Requirement groups, each containing criteria that can satisfy the group.", "read:Read");
        docs.clazz("AdvancementProgress", "MLuaAdvancementService", "A player's completion state and criterion progress for one advancement.", "classes/advancement-progress.html", "Instance");
        docs.property("AdvancementProgress", "AdvancementProgress.Id", "string", "The advancement identifier.", "read:Read");
        docs.property("AdvancementProgress", "AdvancementProgress.IsDone", "boolean", "Whether all advancement requirements are complete.", "read:Read");
        docs.property("AdvancementProgress", "AdvancementProgress.Criteria", "table<string, CriterionProgress>", "Per-criterion completion state.", "read:Read");
        docs.property("AdvancementProgress", "AdvancementProgress.Done", "boolean", "Existing completion field, equivalent to IsDone.", "read:Read");
        docs.property("AdvancementProgress", "AdvancementProgress.Progress", "table<string, CriterionProgress>", "Existing criterion map, equivalent to Criteria.", "read:Read");
        docs.method("AdvancementProgress", "AdvancementProgress:GetCriterion(name: string): CriterionProgress?", "Returns progress for one criterion.", "criterion name", "CriterionProgress?", "server:Server", null);
        docs.method("AdvancementProgress", "AdvancementProgress:HasCompletedCriterion(name: string): boolean", "Checks whether a criterion has been completed.", "criterion name", "boolean", "server:Server", null);
        docs.type("CriterionProgress", null, "Completion state for one advancement criterion.");
        docs.field("CriterionProgress", "Done", "boolean", "Whether this criterion has been completed.");
        docs.signature("Advancements", "Advancements:GetAdvancement(id: string): Advancement?", null);
        docs.signature("Advancements", "Advancements:GetAllAdvancements(): Advancement[]", null);
        docs.signature("Advancements", "Advancements:GetPlayerAdvancement(player: Player, id: string): AdvancementProgress?", null);
        docs.signature("Advancements", "Advancements:GetPlayerAdvancements(player: Player): table<string, AdvancementProgress>", null);
        docs.guideStep("Advancements", "Read and change advancement progress",
            "GetAdvancement returns an Advancement class instance; GetPlayerAdvancements maps identifiers to AdvancementProgress instances. Use GetPlayerAdvancement when you need only one id. The original fields remain available, and the class methods provide explicit queries for criteria.",
            "local Advancements = Minecraft:GetService(\"Advancements\")\nlocal advancement = Advancements:GetAdvancement(\"minecraft:story/mine_stone\")\nif advancement then\n    print(advancement:IsA(\"Advancement\"), advancement.Title, advancement.Parent)\n    for _, group in ipairs(advancement.Requirements) do\n        for _, criterion in ipairs(group) do\n            print(criterion)\n        end\n    end\n\n    local progress = Advancements:GetPlayerAdvancements(player)[advancement.Id]\n    if progress then\n        print(progress:IsA(\"AdvancementProgress\"), progress.IsDone)\n        print(progress:HasCompletedCriterion(\"minecraft:stone\"))\n    end\nend");
    }
}
