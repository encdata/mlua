#!/usr/bin/env python3
"""Keep advancement API reflection on the facade and domain work in its component."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "src/main/java/dev/mlua/modules/advancements/service/MLuaAdvancementService.java"
OPERATIONS = ROOT / "src/main/java/dev/mlua/modules/advancements/service/MLuaAdvancementOperations.java"
service = SERVICE.read_text(encoding="utf-8")
operations = OPERATIONS.read_text(encoding="utf-8")


def fail(message: str) -> None:
    print(f"Advancement service contract error: {message}", file=sys.stderr)
    raise SystemExit(1)


if "private final MLuaAdvancementOperations operations" not in service:
    fail("service facade does not own the advancement operations component")

methods = {
    "GetAdvancement": "getAdvancement",
    "GetPlayerAdvancements": "getPlayerAdvancements",
    "GetPlayerAdvancement": "getPlayerAdvancement",
    "GrantCriteria": "grantCriteria",
    "RevokeCriteria": "revokeCriteria",
    "GrantAll": "grantAll",
    "RevokeAll": "revokeAll",
    "GetAllAdvancements": "getAllAdvancements",
}
for name, delegate in methods.items():
    match = re.search(rf"@MLuaDescription\([^\n]*\)\s+public\s+[^{{]+\b{name}\s*\([^{{]*\)\s*\{{([^{{}}]*)\}}", service, re.DOTALL)
    if match is None:
        fail(f"annotated public endpoint {name} is missing")
    if "requireServerSide(this.name())" not in match.group(1):
        fail(f"endpoint {name} lost the server side guard")
    if f"this.operations.{delegate}(" not in match.group(1):
        fail(f"endpoint {name} no longer delegates to the operations component")

if any(token in service for token in ("AdvancementProgress", "ServerPlayer", "advancementInfo(", "getOrStartProgress(")):
    fail("Minecraft advancement queries or progress construction remain in the facade")
for token in ("advancementInfo(", "getOrStartProgress(", "getRemainingCriteria(", "getCompletedCriteria(", "extractPlayer(", "resolveAdvancement"):
    if token not in operations:
        fail(f"operations component is missing domain behavior {token}")

print("Advancement service contract passed (annotated endpoints delegate to the domain component).")
