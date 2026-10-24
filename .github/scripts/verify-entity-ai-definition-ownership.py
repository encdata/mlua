from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
REGISTRY = (ROOT / "src/main/java/dev/mlua/minecraft/scripted/MLuaBehaviorRegistry.java").read_text()
DEFINITION = (ROOT / "src/main/java/dev/mlua/minecraft/scripted/MLuaEntityAIDefinition.java").read_text()


def require(source: str, fragment: str, label: str) -> None:
    if fragment not in source:
        raise SystemExit(f"Entity AI definition ownership contract missing {label}")


require(REGISTRY, "return MLuaEntityAIDefinition.snapshot(source);", "registry facade snapshot delegation")
require(REGISTRY, "MLuaEntityAIDefinition.validate(ai);", "registry facade validation delegation")
require(DEFINITION, "public static LuaTable snapshot(LuaTable source)", "dedicated snapshot implementation")
require(DEFINITION, "public static void validate(LuaValue ai)", "dedicated validator implementation")
require(DEFINITION, "MAX_TABLE_ENTRIES = 16_384", "bounded recursive data traversal")
require(DEFINITION, "new IdentityHashMap<>()", "identity-based cycle and alias tracking")

print("Entity AI definition ownership contract passed")
