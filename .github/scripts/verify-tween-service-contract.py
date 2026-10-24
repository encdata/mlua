#!/usr/bin/env python3
"""Keep tween property validation out of the service facade and avoid redundant snapshots."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "src/main/java/dev/mlua/modules/tween/service/MLuaTweenService.java"
TWEEN = ROOT / "src/main/java/dev/mlua/modules/tween/service/MLuaTween.java"
REGISTRY = ROOT / "src/main/java/dev/mlua/modules/tween/service/MLuaTweenRegistry.java"
PROPERTIES = ROOT / "src/main/java/dev/mlua/modules/tween/service/MLuaTweenProperties.java"
MODULE = ROOT / "src/main/java/dev/mlua/modules/tween/TweenModule.java"
service = SERVICE.read_text(encoding="utf-8")
tween = TWEEN.read_text(encoding="utf-8")
registry = REGISTRY.read_text(encoding="utf-8")
properties = PROPERTIES.read_text(encoding="utf-8")
module = MODULE.read_text(encoding="utf-8")


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
for method in ("Play", "Pause", "Resume", "Cancel", "GetPlaybackState", "GetError", "GetCompleted", "Destroy"):
    if f'{{"{method}",' not in module or 'registerInstanceMethod(registration, "Tween", method[0], method[1])' not in module:
        fail(f"Tween.{method} is missing from the registered class API")
if 'registerInstanceProperty(registration, "Tween", "Completed", "GetCompleted"' not in module:
    fail("Tween.Completed is missing from the registered read-only property API")
if 'handle.set("GetCompleted"' not in service or 'handle.rawset("__tween_completed"' not in service:
    fail("Tween instances do not expose their registered Completed property getter")
if 'tween.dispose(); return LuaValue.NIL;' not in service:
    fail("Tween:Destroy does not release its runtime state")
if "MLuaTweenRegistry.track(tween)" not in service:
    fail("created Tween handles are not tracked for addon and runtime cleanup")
if "MLuaTweenRegistry.untrack(this)" not in tween:
    fail("destroyed Tween handles remain in the lifecycle tracker")
if "private volatile Object ownerSession;" not in tween:
    fail("tween runtime ownership must be visible to concurrent frame sampling and teardown")
if "new WeakHashMap<>()" not in registry or "OWNED.remove(runtimeSession)" not in registry:
    fail("runtime cleanup does not release inactive tween handles without retaining abandoned handles")
if "tracked.stream().filter(tween -> ownerModId.equals(tween.ownerModId()))" not in registry:
    fail("addon cleanup does not include created, paused, and completed tween handles")
if "catch (VirtualMachineError | ThreadDeath fatal)" not in tween or "catch (RuntimeException failure)" not in tween:
    fail("render-frame tween sampling must let fatal JVM errors propagate")

print("Tween service contract passed (property validation and lifecycle ownership are explicit).")
