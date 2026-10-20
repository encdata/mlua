package dev.mlua.mcapi.scoreboard;

import java.util.HashMap;
import java.util.Map;

public interface MCObjectiveCriteria {

    String getName();

    MCObjectiveCriteria DUMMY = () -> "dummy";
    MCObjectiveCriteria TRIGGER = () -> "trigger";
    MCObjectiveCriteria DEATH_COUNT = () -> "deathCount";
    MCObjectiveCriteria PLAYER_KILL_COUNT = () -> "playerKillCount";
    MCObjectiveCriteria TOTAL_KILL_COUNT = () -> "totalKillCount";
    MCObjectiveCriteria HEALTH = () -> "health";
    MCObjectiveCriteria XP = () -> "xp";
    MCObjectiveCriteria FOOD = () -> "food";
    MCObjectiveCriteria AIR = () -> "air";
    MCObjectiveCriteria ARMOR = () -> "armor";

    static MCObjectiveCriteria byName(String name) {
        if (name == null) return null;
        Map<String, MCObjectiveCriteria> criteria = new HashMap<>();
        criteria.put("dummy", DUMMY);
        criteria.put("trigger", TRIGGER);
        criteria.put("deathCount", DEATH_COUNT);
        criteria.put("playerKillCount", PLAYER_KILL_COUNT);
        criteria.put("totalKillCount", TOTAL_KILL_COUNT);
        criteria.put("health", HEALTH);
        criteria.put("xp", XP);
        criteria.put("food", FOOD);
        criteria.put("air", AIR);
        criteria.put("armor", ARMOR);
        return criteria.get(name);
    }

}
