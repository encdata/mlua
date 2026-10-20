package dev.mlua.modules.advancements;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.modules.advancements.service.MLuaAdvancementService;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import java.util.List;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

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
        registration.registerClassFunction("AdvancementProgress", "GetCriterion", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return args.checktable(1).get("Criteria").get(args.checkjstring(2));
            }
        }, "Returns criterion progress by criterion name.");
        registration.registerClassFunction("AdvancementProgress", "HasCompletedCriterion", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue criterion = args.checktable(1).get("Criteria").get(args.checkjstring(2));
                return LuaValue.valueOf(criterion.istable() && criterion.get("Done").toboolean());
            }
        }, "Checks whether the named criterion has been completed.");
        registration.registerClassFunction("AdvancementProgress", "GetCompletedCriteria", criteriaByState(true),
            "Returns the names of completed criteria.");
        registration.registerClassFunction("AdvancementProgress", "GetRemainingCriteria", criteriaByState(false),
            "Returns the names of criteria that are not completed.");

        registration.registerClass("CriterionProgress", registration.requireInstanceBaseClassName(),
            "Completion state for one criterion in a player's advancement progress.");
        property(registration, "CriterionProgress", "Name", "string", "Returns the criterion name.");
        property(registration, "CriterionProgress", "Done", "boolean", "Returns whether this criterion has been completed.");
        registration.registerClassFunction("CriterionProgress", "IsDone", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return LuaValue.valueOf(self.checktable().get("Done").toboolean()); }
        }, "Checks whether this criterion has been completed.");
    }

    private static void property(MLuaRegistration registration, String className, String name,
                                 String valueType, String description) {
        registration.registerClassFunction(className, "Get" + name, new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return self.checktable().get(name); }
        }, description);
        MLuaClassMethods.registerInstanceProperty(registration, className, name, "Get" + name, null,
            valueType, "server:Server", description);
    }

    private static VarArgFunction criteriaByState(boolean completed) {
        return new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue criteria = args.checktable(1).get("Criteria");
                LuaTable result = new LuaTable();
                if (!criteria.istable()) return result;
                int index = 1;
                java.util.List<String> names = new java.util.ArrayList<>();
                for (LuaValue key : criteria.checktable().keys()) {
                    LuaValue value = criteria.get(key);
                    if (value.istable() && value.get("Done").toboolean() == completed) {
                        names.add(key.checkjstring());
                    }
                }
                java.util.Collections.sort(names);
                for (String name : names) result.set(index++, LuaValue.valueOf(name));
                return result;
            }
        };
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.service("Advancements", "MLuaAdvancementService",
                "Provides server-side advancement lookup and progress control.",
                "services/advancements.html"));
        docs.register(MLuaDocBuilder.Registration.clazz("Advancement", "MLuaAdvancementService", "A typed view of a registered Minecraft advancement and its display requirements.", "classes/advancement.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.property("Advancement", "Advancement.Id", "string", "The advancement identifier.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Advancement", "Advancement.Title", "string", "The displayed advancement title.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Advancement", "Advancement.Description", "string", "The displayed advancement description.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Advancement", "Advancement.Parent", "string?", "The parent advancement identifier, or nil for a root advancement.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Advancement", "Advancement.Requirements", "string[][]", "Requirement groups, each containing criteria that can satisfy the group.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.clazz("AdvancementProgress", "MLuaAdvancementService", "A player's completion state and criterion progress for one advancement.", "classes/advancement-progress.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.property("AdvancementProgress", "AdvancementProgress.Id", "string", "The advancement identifier.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("AdvancementProgress", "AdvancementProgress.IsDone", "boolean", "Whether all advancement requirements are complete.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("AdvancementProgress", "AdvancementProgress.Criteria", "table<string, CriterionProgress>", "Per-criterion completion state.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("AdvancementProgress", "AdvancementProgress.Done", "boolean", "Existing completion field, equivalent to IsDone.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("AdvancementProgress", "AdvancementProgress.Progress", "table<string, CriterionProgress>", "Existing criterion map, equivalent to Criteria.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.method("AdvancementProgress", "AdvancementProgress:GetCriterion(name: string): CriterionProgress?", "Returns progress for one criterion.", "criterion name", "CriterionProgress?", "server:Server", null));
        docs.register(MLuaDocBuilder.Registration.method("AdvancementProgress", "AdvancementProgress:HasCompletedCriterion(name: string): boolean", "Checks whether a criterion has been completed.", "criterion name", "boolean", "server:Server", null));
        docs.register(MLuaDocBuilder.Registration.method("AdvancementProgress", "AdvancementProgress:GetCompletedCriteria(): string[]", "Returns completed criterion names.", null, "string[]", "server:Server", null));
        docs.register(MLuaDocBuilder.Registration.method("AdvancementProgress", "AdvancementProgress:GetRemainingCriteria(): string[]", "Returns criterion names that are not complete.", null, "string[]", "server:Server", null));
        docs.register(MLuaDocBuilder.Registration.clazz("CriterionProgress", "MLuaAdvancementService", "Completion state for one criterion in a player's advancement progress.", "classes/criterion-progress.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.property("CriterionProgress", "CriterionProgress.Name", "string", "The criterion name.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("CriterionProgress", "CriterionProgress.Done", "boolean", "Whether this criterion has been completed.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.method("CriterionProgress", "CriterionProgress:IsDone(): boolean", "Checks whether this criterion has been completed.", null, "boolean", "server:Server", null));
        docs.register(MLuaDocBuilder.Registration.signature("Advancements", "Advancements:GetAdvancement(id: string): Advancement?", null));
        docs.register(MLuaDocBuilder.Registration.signature("Advancements", "Advancements:GetAllAdvancements(): Advancement[]", null));
        docs.register(MLuaDocBuilder.Registration.signature("Advancements", "Advancements:GetPlayerAdvancement(player: Player, id: string): AdvancementProgress?", null));
        docs.register(MLuaDocBuilder.Registration.signature("Advancements", "Advancements:GetPlayerAdvancements(player: Player): table<string, AdvancementProgress>", null));
        docs.register(MLuaDocBuilder.Registration.signature("Advancements", "Advancements:GrantCriteria(player: Player, id: string, criterion: string): boolean", null));
        docs.register(MLuaDocBuilder.Registration.signature("Advancements", "Advancements:RevokeCriteria(player: Player, id: string, criterion: string): boolean", null));
        docs.register(MLuaDocBuilder.Registration.signature("Advancements", "Advancements:GrantAll(player: Player, id: string): number", null));
        docs.register(MLuaDocBuilder.Registration.signature("Advancements", "Advancements:RevokeAll(player: Player, id: string): number", null));
        docs.register(MLuaDocBuilder.Registration.guideStep("Advancements", "Read and change advancement progress",
            "GetAdvancement returns an Advancement class instance; GetPlayerAdvancements maps identifiers to AdvancementProgress instances. Use GetPlayerAdvancement when you need only one id. Criterion entries are CriterionProgress instances, and progress also exposes completed and remaining criterion name lists. GrantCriteria and RevokeCriteria return true only when that criterion changed. GrantAll and RevokeAll return the number of criteria changed; they return zero if the advancement does not exist or nothing needed changing.",
            "local Advancements = Minecraft:GetService(\"Advancements\")\nlocal advancement = Advancements:GetAdvancement(\"minecraft:story/mine_stone\")\nif advancement then\n    print(advancement:IsA(\"Advancement\"), advancement.Title, advancement.Parent)\n    for _, group in ipairs(advancement.Requirements) do\n        for _, criterion in ipairs(group) do\n            print(criterion)\n        end\n    end\n\n    local progress = Advancements:GetPlayerAdvancements(player)[advancement.Id]\n    if progress then\n        print(progress:IsA(\"AdvancementProgress\"), progress.IsDone)\n        print(progress:HasCompletedCriterion(\"minecraft:stone\"))\n    end\nend"));
    }
}
