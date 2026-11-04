package dev.mlua.modules.advancements.service;

import dev.mlua.module.MLuaRegistration;
import java.util.List;
import net.minecraft.advancements.AdvancementProgress;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Builds immutable Lua class instances returned by advancement queries. */
final class MLuaAdvancementViews {
    private MLuaAdvancementViews() { }

    static LuaTable progressInfo(MLuaRegistration registration, String id, AdvancementProgress progress) {
        LuaTable result = registration.createClassInstance("AdvancementProgress");
        result.rawset("Id", LuaValue.valueOf(id));
        result.rawset("Done", LuaValue.valueOf(progress.isDone()));
        result.rawset("IsDone", LuaValue.valueOf(progress.isDone()));
        LuaTable criteria = new LuaTable();
        for (String criterion : progress.getCompletedCriteria()) {
            criteria.set(criterion, criterionProgress(registration, criterion, true));
        }
        for (String criterion : progress.getRemainingCriteria()) {
            criteria.set(criterion, criterionProgress(registration, criterion, false));
        }
        result.rawset("Progress", criteria);
        result.rawset("Criteria", criteria);
        result.freeze();
        return result;
    }

    static LuaTable advancementInfo(MLuaRegistration registration, String id, String title,
                                    String description, LuaValue parent,
                                    List<List<String>> requirementGroups) {
        LuaTable info = registration.createClassInstance("Advancement");
        info.rawset("Id", LuaValue.valueOf(id));
        info.rawset("Title", LuaValue.valueOf(title));
        info.rawset("Description", LuaValue.valueOf(description));
        info.rawset("Parent", parent);
        LuaTable requirements = new LuaTable();
        for (int groupIndex = 0; groupIndex < requirementGroups.size(); groupIndex++) {
            LuaTable group = new LuaTable();
            List<String> criteria = requirementGroups.get(groupIndex);
            for (int criterionIndex = 0; criterionIndex < criteria.size(); criterionIndex++) {
                group.set(criterionIndex + 1, LuaValue.valueOf(criteria.get(criterionIndex)));
            }
            requirements.set(groupIndex + 1, group);
        }
        info.rawset("Requirements", requirements);
        info.freeze();
        return info;
    }

    private static LuaTable criterionProgress(MLuaRegistration registration, String name, boolean done) {
        LuaTable criterion = registration.createClassInstance("CriterionProgress");
        criterion.rawset("Name", LuaValue.valueOf(name));
        criterion.rawset("Done", LuaValue.valueOf(done));
        criterion.freeze();
        return criterion;
    }
}
