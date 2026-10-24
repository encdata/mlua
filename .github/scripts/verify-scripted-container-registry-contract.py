from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
REGISTRY = (ROOT / "src/main/java/dev/mlua/minecraft/scripted/MLuaBehaviorRegistry.java").read_text()
CONTAINERS = (ROOT / "src/main/java/dev/mlua/minecraft/scripted/MLuaScriptedContainerRegistry.java").read_text()
TESTS = (ROOT / "src/test/java/dev/mlua/minecraft/scripted/MLuaScriptedContainerRegistryTest.java").read_text()


def require(source: str, fragment: str, label: str) -> None:
    if fragment not in source:
        raise SystemExit(f"Scripted container registry contract missing {label}")


require(REGISTRY, "MLuaScriptedContainerRegistry.registerBlock(key, definition)", "compatibility facade")
require(REGISTRY, "MLuaScriptedContainerRegistry.restoreBlock(key, expected, previous)", "rollback delegation")
require(CONTAINERS, "private static final ConcurrentMap<String, ContainerDefinition> BLOCK_CONTAINERS",
        "block container state ownership")
require(CONTAINERS, "private static final ConcurrentMap<String, ContainerDefinition> CONTAINER_TYPES",
        "container type state ownership")
require(CONTAINERS, "current != expected", "identity-based rollback safety")
require(TESTS, "rollbackUsesRegistrationIdentityWhenEqualDefinitionsAreReplaced", "replacement regression coverage")
require(TESTS, "containerTypeRemovalAlsoRequiresTheRegisteredDefinitionInstance", "type removal regression coverage")

print("Scripted container registry contract passed")
