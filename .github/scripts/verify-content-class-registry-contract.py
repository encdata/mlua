from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
REGISTRY = (ROOT / "src/main/java/dev/mlua/minecraft/scripted/MLuaBehaviorRegistry.java").read_text()
CLASSES = (ROOT / "src/main/java/dev/mlua/minecraft/scripted/MLuaScriptedContentClassRegistry.java").read_text()
TESTS = (ROOT / "src/test/java/dev/mlua/minecraft/scripted/MLuaScriptedContentClassRegistryTest.java").read_text()


def require(source: str, fragment: str, label: str) -> None:
    if fragment not in source:
        raise SystemExit(f"Content class registry contract missing {label}")


require(REGISTRY, "MLuaScriptedContentClassRegistry.register(domain, key, className, context)",
        "compatibility facade registration")
require(REGISTRY, "MLuaScriptedContentClassRegistry.restore(domain, key, expected, previous)",
        "rollback delegation")
require(CLASSES, "private static final ConcurrentMap<String, ConcurrentMap<String, ContentClassDefinition>> DEFINITIONS",
        "content class state ownership")
require(CLASSES, "MLuaScriptedOwnerPolicy.sameOwner(entry.getValue().context(), context, entry.getKey())",
        "addon registration limits")
require(CLASSES, "java.util.Objects.equals(current, expected)", "conditional rollback semantics")
require(TESTS, "restoresClassMappingForFailedRegistrationAndRetainsFacadeBehavior", "compatibility regression coverage")

print("Content class registry contract passed")
