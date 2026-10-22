#!/usr/bin/env python3
"""Keep scoreboard team behavior behind its service-owned component."""

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "src/main/java/dev/mlua/modules/scoreboard/service/MLuaScoreboardService.java"
TEAMS = ROOT / "src/main/java/dev/mlua/modules/scoreboard/service/MLuaScoreboardTeamOperations.java"
service = SERVICE.read_text(encoding="utf-8")
teams = TEAMS.read_text(encoding="utf-8")


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
if "MCPlayerTeam" in service or "PlayerTeam." in service or "teamTable(" in service:
    fail("team mutation, validation, or snapshot code remains in the scoreboard facade")
for operation in (
    "createTeam(", "addPlayerToTeam(", "setFriendlyFire(", "setNameTagVisibility(",
    "teamTable(", "sortedMembers.sort(", "parseCollisionRule(",
):
    if operation not in teams:
        fail(f"team operations component is missing {operation}")

print("Scoreboard service contract passed (team API delegates and component ownership checked).")
