#!/usr/bin/env python3
"""Keep world-backed Lua class methods on the owning client or server thread."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DISPATCHER = ROOT / "src/main/java/dev/mlua/core/classes/MLuaClassThreadDispatcher.java"
ACCESS = ROOT / "src/main/java/dev/mlua/runtime/MLuaGameThreadAccess.java"
METHODS = ROOT / "src/main/java/dev/mlua/core/classes/MLuaClassMethods.java"


def fail(message: str) -> None:
    raise SystemExit(f"Class thread-dispatch contract error: {message}")


dispatcher = DISPATCHER.read_text(encoding="utf-8")
access = ACCESS.read_text(encoding="utf-8")
methods = METHODS.read_text(encoding="utf-8")
if "MLuaGameThreadAccess.callUnchecked(level" not in dispatcher:
    fail("live class methods must use the shared client/server world-thread dispatcher")
if "private static Level level(LuaValue receiver)" not in dispatcher:
    fail("class dispatch must resolve client and server Level instances")
for marker in ('"__entity"', '"__block_entity"', '"__level"', '"__world"'):
    if marker not in dispatcher:
        fail(f"class dispatch must resolve the {marker} world owner")
if "MLuaServerThread.call(" in dispatcher or "ServerLevel" in dispatcher:
    fail("class method dispatch must not be restricted to dedicated-server levels")
if "MLuaClientThread.callForClientState(action)" not in access \
        or "MLuaServerThread.callChecked(serverLevel.getServer(), action::call)" not in access:
    fail("shared world dispatch must preserve client and server owner-thread behavior")
if "MLuaClassThreadDispatcher.bind(instance, implementation)" not in methods:
    fail("all registered live class methods must use the shared dispatcher")

print("Class thread-dispatch contract passed (world-backed classes use their owning client/server thread).")
