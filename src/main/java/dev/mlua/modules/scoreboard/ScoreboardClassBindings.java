package dev.mlua.modules.scoreboard;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;

/** Lua class definitions for the typed scoreboard snapshots returned by the service. */
final class ScoreboardClassBindings {
    private ScoreboardClassBindings() { }

    static void register(MLuaRegistration registration) {
        registration.registerClass("ScoreboardObjective", registration.requireInstanceBaseClassName(),
            "A typed snapshot of a registered scoreboard objective.");
        property(registration, "ScoreboardObjective", "Name", "string", "The objective's name.");
        property(registration, "ScoreboardObjective", "DisplayName", "string", "The objective's display name.");
        property(registration, "ScoreboardObjective", "Criteria", "string", "The objective's criterion identifier.");
        property(registration, "ScoreboardObjective", "Slots", "string[]", "Display slots currently showing this objective.");

        registration.registerClass("ScoreboardTeam", registration.requireInstanceBaseClassName(),
            "A typed snapshot of a scoreboard team and its gameplay rules.");
        property(registration, "ScoreboardTeam", "Name", "string", "The team's name.");
        property(registration, "ScoreboardTeam", "DisplayName", "string", "The team's display name.");
        property(registration, "ScoreboardTeam", "Color", "string", "The team's formatting color, or an empty string for reset.");
        property(registration, "ScoreboardTeam", "Prefix", "string", "The text prefixed to member names.");
        property(registration, "ScoreboardTeam", "Suffix", "string", "The text appended to member names.");
        property(registration, "ScoreboardTeam", "AllowFriendlyFire", "boolean", "Whether team members can damage each other.");
        property(registration, "ScoreboardTeam", "SeeFriendlyInvisibles", "boolean", "Whether team members can see invisible teammates.");
        property(registration, "ScoreboardTeam", "NameTagVisibility", "string", "The team's name-tag visibility rule.");
        property(registration, "ScoreboardTeam", "DeathMessageVisibility", "string", "The team's death-message visibility rule.");
        property(registration, "ScoreboardTeam", "CollisionRule", "string", "The team's entity collision rule.");
        property(registration, "ScoreboardTeam", "Members", "string[]", "The team members in stable name order.");

        registration.registerClass("ScoreboardScore", registration.requireInstanceBaseClassName(),
            "A score holder's value for one scoreboard objective.");
        property(registration, "ScoreboardScore", "PlayerName", "string", "The score holder name.");
        property(registration, "ScoreboardScore", "ObjectiveName", "string", "The objective that owns this score.");
        property(registration, "ScoreboardScore", "Score", "number", "The score value.");
    }

    private static void property(MLuaRegistration registration, String className, String name,
                                 String type, String description) {
        registration.registerClassFunction(className, "Get" + name, new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return self.checktable().get(name); }
        }, description);
        MLuaClassMethods.registerInstanceProperty(registration, className, name, "Get" + name, null,
            type, "server:Server", description);
    }
}
