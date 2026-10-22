#!/usr/bin/env python3
"""Keep scoreboard team behavior behind its service-owned component."""

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "src/main/java/dev/mlua/modules/scoreboard/service/MLuaScoreboardService.java"
TEAMS = ROOT / "src/main/java/dev/mlua/modules/scoreboard/service/MLuaScoreboardTeamOperations.java"
OBJECTIVES = ROOT / "src/main/java/dev/mlua/modules/scoreboard/service/MLuaScoreboardObjectiveOperations.java"
service = SERVICE.read_text(encoding="utf-8")
teams = TEAMS.read_text(encoding="utf-8")
objectives = OBJECTIVES.read_text(encoding="utf-8")


def fail(message: str) -> None:
    print(f"Scoreboard service contract error: {message}", file=sys.stderr)
    raise SystemExit(1)


delegates = {
    "CreateTeam": "createTeam",
    "RemoveTeam": "removeTeam",
    "GetTeam": "getTeam",
    "AddPlayerToTeam": "addPlayerToTeam",
    "RemovePlayerFromTeam": "removePlayerFromTeam",
    "GetTeams": "getTeams",
    "ConfigureTeam": "configureTeam",
    "SetTeamFriendlyFire": "setFriendlyFire",
    "SetTeamSeeFriendlyInvisibles": "setSeeFriendlyInvisibles",
    "SetTeamNameTagVisibility": "setNameTagVisibility",
    "SetTeamDeathMessageVisibility": "setDeathMessageVisibility",
    "SetTeamCollisionRule": "setCollisionRule",
    "SetTeamColor": "setColor",
}

for public_name, operation in delegates.items():
    method = re.search(
        rf"@MLuaDescription\([^\n]*\)\s+public\s+[^{{]+\b{public_name}\s*\([^{{]*\)\s*\{{([^{{}}]*)\}}",
        service,
        re.DOTALL,
    )
    if method is None:
        fail(f"annotated compatibility method {public_name} is missing")
    if f"this.teamOperations.{operation}(" not in method.group(1):
        fail(f"compatibility method {public_name} no longer delegates to {operation}")

if "private final MLuaScoreboardTeamOperations teamOperations" not in service:
    fail("scoreboard service does not own its team operations component")
if "private final MLuaScoreboardObjectiveOperations objectiveOperations" not in service:
    fail("scoreboard service does not own its objective operations component")
if "MCPlayerTeam" in service or "PlayerTeam." in service or "teamTable(" in service:
    fail("team mutation, validation, or snapshot code remains in the scoreboard facade")
for operation in (
    "createTeam(", "addPlayerToTeam(", "setFriendlyFire(", "setNameTagVisibility(",
    "teamTable(", "sortedMembers.sort(", "parseCollisionRule(",
):
    if operation not in teams:
        fail(f"team operations component is missing {operation}")

objective_delegates = {
    "GetObjective": "getObjective",
    "SetDisplaySlot": "setDisplaySlot",
    "CreateObjective": "createObjective",
    "RemoveObjective": "removeObjective",
    "GetScores": "getScores",
    "SetScore": "setScore",
    "GetScore": "getScore",
    "ResetScore": "resetScore",
    "GetObjectives": "getObjectives",
    "SetObjectiveDisplayName": "setObjectiveDisplayName",
}
for public_name, operation in objective_delegates.items():
    method = re.search(
        rf"@MLuaDescription\([^\n]*\)\s+public\s+[^{{]+\b{public_name}\s*\([^{{]*\)\s*\{{([^{{}}]*)\}}",
        service,
        re.DOTALL,
    )
    if method is None:
        fail(f"annotated compatibility method {public_name} is missing")
    if f"this.objectiveOperations.{operation}(" not in method.group(1):
        fail(f"compatibility method {public_name} no longer delegates to {operation}")
if any(name in service for name in ("objectiveTable(", "addObjective(", "getOrCreatePlayerScore(")):
    fail("objective, score mutation, or snapshot logic remains in the scoreboard facade")
for operation in (
    "objectiveTable(", "getPlayerScores(", "SetDisplaySlot", "ScoreHolder.forNameOnly(",
    "Scoreboard.DISPLAY_SLOT_LIST", "BlankFormat.INSTANCE",
):
    if operation not in objectives:
        fail(f"objective operations component is missing {operation}")

print("Scoreboard service contract passed (team and objective APIs delegate to their components).")
