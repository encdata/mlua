from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
REGISTRY = (ROOT / "src/main/java/dev/mlua/minecraft/scripted/MLuaBehaviorRegistry.java").read_text()
DEFINITIONS = (ROOT / "src/main/java/dev/mlua/minecraft/scripted/MLuaScriptedBehaviorDefinitions.java").read_text()
OWNER_POLICY = (ROOT / "src/main/java/dev/mlua/minecraft/scripted/MLuaScriptedOwnerPolicy.java").read_text()
TESTS = (ROOT / "src/test/java/dev/mlua/minecraft/scripted/MLuaScriptedBehaviorDefinitionsTest.java").read_text()


def require(source: str, fragment: str, label: str) -> None:
    if fragment not in source:
        raise SystemExit(f"Scripted behavior definitions contract missing {label}")


require(REGISTRY, "MLuaScriptedBehaviorDefinitions.registerBehavior(domain, key, event, behavior)",
        "single callback facade")
require(REGISTRY, "MLuaScriptedBehaviorDefinitions.registerBehaviors(domain, key, behaviors, context)",
        "atomic callback-set facade")
require(DEFINITIONS, "private static final ConcurrentMap<String, ConcurrentMap<String, MLuaBehaviorRegistry.BehaviorSet>> DEFINITIONS",
        "definition state ownership")
require(DEFINITIONS, "previous.callbacks().isEmpty()", "snapshot rollback support")
require(DEFINITIONS, "right.get(entry.getKey()) != entry.getValue()", "callback identity comparison")
require(DEFINITIONS, "MLuaScriptedOwnerPolicy.sameOwner(entry.getValue().context(), entry.getKey(), context, key)",
        "shared addon capacity ownership policy")
require(OWNER_POLICY, "static String ownerOf(", "shared addon fallback ownership")
require(TESTS, "rollbackRestoresPreviousCallbackSetOnlyWhileFailedDefinitionIsCurrent",
        "stale registration rollback regression coverage")
require(TESTS, "individualCallbackUpdatesRetainOtherEventsAndNilRemovesOneEvent",
        "per-event update compatibility coverage")

print("Scripted behavior definitions contract passed")
