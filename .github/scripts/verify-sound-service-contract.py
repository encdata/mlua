#!/usr/bin/env python3
"""Keep sound handle behavior in its factory and SoundService as a stable Lua facade."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "src/main/java/dev/mlua/modules/sound/service/MLuaSoundService.java"
FACTORY = ROOT / "src/main/java/dev/mlua/modules/sound/service/MLuaSoundHandleFactory.java"
service = SERVICE.read_text(encoding="utf-8")
factory = FACTORY.read_text(encoding="utf-8")


def fail(message: str) -> None:
    print(f"Sound service contract error: {message}", file=sys.stderr)
    raise SystemExit(1)


if "private final MLuaSoundHandleFactory handleFactory" not in service:
    fail("SoundService does not own a SoundHandle factory")
for endpoint in ("PlayLocal", "PlayAtPlayer", "Play", "GetAll"):
    if f"public LuaValue {endpoint}(" not in service and f"public LuaTable {endpoint}(" not in service:
        fail(f"public endpoint {endpoint} is missing")
if "handleFactory.create(id, owner)" not in service:
    fail("sound handle creation no longer delegates to the factory")
if "private LuaTable soundTable(" in service or 'operations.set("SetVolume"' in service:
    fail("SoundHandle instance behavior remains in the service facade")
for token in ('operations.set("SetVolume"', 'operations.set("FollowEntity"', 'operations.set("GetFollowOffset"',
              'MLuaClassMethods.setInstanceMethod', 'SoundHandle:" + operation', 'MLuaSoundOwnershipRegistry.Owner owner'):
    if token not in factory:
        fail(f"sound handle factory is missing {token}")

print("Sound service contract passed (sound handles have a dedicated factory).")
