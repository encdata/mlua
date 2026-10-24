#!/usr/bin/env python3
"""Guard cross-loader gameplay event semantics that MLua exposes to Lua."""

from pathlib import Path
import json
import re
import sys

from mlua_target_sources import inherited_loader_source


ROOT = Path(__file__).resolve().parents[2]
BRIDGES = {
    ("1.20.1", "forge"): ROOT / "src/main/java/dev/mlua/loader/impl/forge/ForgeEventBridge.java",
    ("1.21.1", "forge"): ROOT / "src/main/java/dev/mlua/loader/impl/forge/ForgeEventBridge.java",
    ("1.21.1", "neoforge"): ROOT / "mlua-loaders/neoforge/shared/project/src/main/java/dev/mlua/loader/impl/neoforge/NeoForgeEventBridge.java",
    ("26.1", "neoforge"): ROOT / "mlua-loaders/neoforge/shared/project/src/main/java/dev/mlua/loader/impl/neoforge/NeoForgeEventBridge.java",
    ("26.1.2", "neoforge"): ROOT / "mlua-loaders/neoforge/shared/project/src/main/java/dev/mlua/loader/impl/neoforge/NeoForgeEventBridge.java",
    ("1.20.1", "fabric"): ROOT / "mlua-loaders/fabric/1.20.1/project/src/main/java/dev/mlua/loader/impl/fabric/FabricEventBridge.java",
    ("1.21.1", "fabric"): ROOT / "mlua-loaders/fabric/1.20.1/project/src/main/java/dev/mlua/loader/impl/fabric/FabricEventBridge.java",
    ("26.1", "fabric"): ROOT / "mlua-loaders/fabric/26.1/project/src/main/java/dev/mlua/loader/impl/fabric/FabricEventBridge.java",
    ("26.1.1", "fabric"): ROOT / "mlua-loaders/fabric/26.1/project/src/main/java/dev/mlua/loader/impl/fabric/FabricEventBridge.java",
    ("26.1.2", "fabric"): ROOT / "mlua-loaders/fabric/26.1/project/src/main/java/dev/mlua/loader/impl/fabric/FabricEventBridge.java",
}
LIFECYCLE_TRACKER = ROOT / "mlua-loader-api/src/main/java/dev/mlua/loaderapi/ClientWorldLifecycleTracker.java"
EVENT_SERVICE = ROOT / "src/main/java/dev/mlua/modules/events/service/MLuaEventsService.java"
WORKFLOW = ROOT / ".github/workflows/compile-java.yml"
EVENT_CONTRACT = ROOT / "mlua-loader-api/src/main/java/dev/mlua/loaderapi/EventBridge.java"


def fail(message: str) -> None:
    print(f"Event bridge contract error: {message}", file=sys.stderr)
    raise SystemExit(1)


contract_source = EVENT_CONTRACT.read_text(encoding="utf-8")
required_hooks = set(re.findall(r"(?m)^\s*(?!default\b)void\s+(on\w+)\s*\(", contract_source))
if not required_hooks:
    fail("could not identify required hooks in the shared EventBridge contract")

targets_manifest = json.loads((ROOT / "gradle/mlua-targets.json").read_text(encoding="utf-8"))["targets"]
manifest_pairs = {
    (str(target["minecraft"]), loader)
    for target in targets_manifest
    for loader in target["loaders"]
}
if manifest_pairs != set(BRIDGES):
    missing = sorted(manifest_pairs - set(BRIDGES))
    stale = sorted(set(BRIDGES) - manifest_pairs)
    fail(f"EventBridge target map differs from the supported-target manifest (missing={missing}, stale={stale})")

FABRIC_PICKUP_MIXINS = set()
for version, loader in manifest_pairs:
    if loader != "fabric":
        continue
    path = inherited_loader_source(
        "fabric", version,
        "project/src/main/java/dev/mlua/modules/mixin/service/FabricItemPickupMixin.java",
    )
    if path is None:
        fail(f"Fabric {version} has no inherited FabricItemPickupMixin source")
    FABRIC_PICKUP_MIXINS.add(path)

for (version, loader), path in BRIDGES.items():
    loader_label = {"fabric": "Fabric", "forge": "Forge", "neoforge": "NeoForge"}[loader]
    target = f"{loader_label} {version}"
    source = path.read_text(encoding="utf-8")
    implemented_hooks = set(re.findall(r"\b(?:public\s+)?void\s+(on\w+)\s*\(", source))
    missing_hooks = sorted(required_hooks - implemented_hooks)
    if missing_hooks:
        fail(f"{target} is missing required EventBridge hooks: {', '.join(missing_hooks)}")
    start_tick_hook = re.search(r"\bonClientTickStart\s*\([^)]*\)\s*\{([^}]*)\}", source, re.S)
    if start_tick_hook is None:
        fail(f"{target} has no implementation for the client input refresh hook")
    expected_start = {
        "fabric": "ClientTickEvents.START_CLIENT_TICK",
        "forge": "TickEvent.Phase.START",
        "neoforge": "ClientTickEvent.Pre",
    }[loader]
    if expected_start not in start_tick_hook.group(1):
        fail(f"{target} must refresh simulated input before the client tick begins")

    for method in ("onItemPickup", "onEntityHurt", "onPlayerRespawn", "onWorldLoad", "onWorldUnload",
                   "onClientWorldLoad", "onClientWorldUnload", "onClientEntityUnload"):
        if not re.search(rf"\b{method}\s*\(", source):
            fail(f"{target} is missing EventBridge.{method}")

    for method in ("onClientWorldLoad", "onClientWorldUnload", "onClientEntityUnload"):
        match = re.search(rf"\b{method}\s*\([^)]*\)\s*\{{(.*?)\n\s*\}}", source, re.S)
        if match is None:
            fail(f"{target} has no inspectable body for EventBridge.{method}")
        body = match.group(1)
        if "LoaderApi.platform().isClient()" not in body or "UnsupportedOperationException" not in body:
            fail(f"{target} must reject client lifecycle hooks on a dedicated server ({method})")

    if "clientWorldTracker().onLoad(callback)" not in source or "clientWorldTracker().onUnload(callback)" not in source:
        fail(f"{target} does not route client world transitions through the shared lifecycle tracker")

for path in FABRIC_PICKUP_MIXINS:
    source = path.read_text(encoding="utf-8")
    if "getCount() - (remaining.isEmpty() ? 0 : remaining.getCount())" not in source:
        fail(f"{path.relative_to(ROOT)} must report the stack amount actually transferred")
    if "!player.level().isClientSide() && pickedCount > 0" not in source:
        fail(f"{path.relative_to(ROOT)} must emit only successful server-side pickups")

for (version, loader), path in BRIDGES.items():
    if loader != "forge":
        continue
    source = path.read_text(encoding="utf-8")
    if "callback.handle(event.getEntity(), event.getStack())" not in source:
        fail(f"Forge {version} must forward ItemPickupEvent's clone of the amount picked up")
    if "LivingAttackEvent" in source:
        fail(f"Forge {version} EntityHurt still fires before invulnerability checks; use the post-check LivingHurtEvent phase")
    if source.count("LivingHurtEvent event") < 2:
        fail(f"Forge {version} must route both EntityHurt hooks through the post-invulnerability damage phase")
    if "event.setCanceled(true)" not in source[source.find("onEntityHurtEvent"):source.find("onBlockBreakEvent")]:
        fail(f"Forge {version} EntityHurt cancellation must cancel the LivingHurtEvent")

for (version, loader), path in BRIDGES.items():
    if loader != "fabric":
        continue
    source = path.read_text(encoding="utf-8")
    if "ServerLivingEntityEvents.ALLOW_DAMAGE.register" not in source:
        fail(f"Fabric {version} must use the server-side pre-mitigation damage event")

for (version, loader), path in BRIDGES.items():
    if loader != "neoforge":
        continue
    source = path.read_text(encoding="utf-8")
    if "LivingIncomingDamageEvent" not in source:
        fail(f"NeoForge {version} must observe damage after invulnerability checks")
    if "event.setCanceled(true)" not in source[source.find("onEntityHurtEvent"):source.find("onBlockBreakEvent")]:
        fail(f"NeoForge {version} EntityHurt cancellation must cancel the incoming-damage event")

if "after the entity passes its invulnerability check" not in EVENT_SERVICE.read_text(encoding="utf-8"):
    fail("EntityHurt documentation must describe the shared post-invulnerability phase")

for (version, loader), path in BRIDGES.items():
    if loader != "neoforge":
        continue
    target = f"NeoForge {version}"
    source = path.read_text(encoding="utf-8")
    if "event.getOriginalStack()" not in source or "event.getCurrentStack()" not in source:
        fail(f"{target} must derive the transferred stack from the pickup event's before/after values")
    if "int pickedCount = original.getCount() - (remaining.isEmpty() ? 0 : remaining.getCount())" not in source:
        fail(f"{target} does not calculate the partial pickup amount")

service = EVENT_SERVICE.read_text(encoding="utf-8")
if "copy of the stack amount actually transferred, including partial pickups" not in service:
    fail("Events.ItemPickup documentation does not promise a consistent transferred-stack contract")
tracker = LIFECYCLE_TRACKER.read_text(encoding="utf-8")
if "if (current == observedWorld) return;" not in tracker:
    fail("client world tracking can emit duplicate load/unload events for the same world")
unload = tracker.find('dispatch(unloadListeners, previous, "client world unload")')
load = tracker.find('dispatch(loadListeners, current, "client world load")')
if unload < 0 or load < 0 or unload > load:
    fail("client world replacement must emit unload before load")
if "catch (Throwable failure)" not in tracker:
    fail("one failing client world listener can prevent later listeners from running")
if "verify-event-bridge-contract.py" not in WORKFLOW.read_text(encoding="utf-8"):
    fail("workflow is missing the event bridge contract check wiring")

print(f"Event bridge contract passed ({len(BRIDGES)} supported loader/version targets; pickup and client lifecycle semantics checked).")
