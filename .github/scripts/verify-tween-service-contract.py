#!/usr/bin/env python3
"""Keep tween property validation out of the service facade and avoid redundant snapshots."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "src/main/java/dev/mlua/modules/tween/service/MLuaTweenService.java"
PROPERTIES = ROOT / "src/main/java/dev/mlua/modules/tween/service/MLuaTweenProperties.java"
service = SERVICE.read_text(encoding="utf-8")
properties = PROPERTIES.read_text(encoding="utf-8")


def fail(message: str) -> None:
    print(f"Tween service contract error: {message}", file=sys.stderr)
    raise SystemExit(1)


create = re.search(r"@MLuaDescription\([^\n]*\)\s+public\s+LuaTable Create\([^)]*\)\s*\{(.*?)\n    \}", service, re.DOTALL)
if create is None:
    fail("Create endpoint is missing")
body = create.group(1)
if "requireClientSide(this.name())" not in body:
    fail("Create endpoint lost its client side guard")
if "MLuaTweenProperties.resolve(targetValue, target, goals, registration)" not in body:
    fail("Create endpoint does not delegate target and goal validation")
if any(token in body for token in ("goals.next(", "target.get(\"Get", "MLuaVector.", "MLuaCFrame.")):
    fail("property discovery or goal type validation remains in the service facade")
for token in ("classNameOf(targetValue)", "__mlua_service_name", "goals.next(key)", "MAX_PROPERTIES_PER_TWEEN", "Get\" + property", "Set\" + property", "MLuaVector.isVector3", "MLuaCFrame.isCFrame"):
    if token not in properties:
        fail(f"property resolver is missing {token}")
if "return List.copyOf(properties)" in properties:
    fail("property resolver adds a redundant list copy before MLuaTween takes its immutable snapshot")

print("Tween service contract passed (target and goal validation have a dedicated owner).")
