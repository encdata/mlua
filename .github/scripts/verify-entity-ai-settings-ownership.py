from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
REGISTRY = (ROOT / "src/main/java/dev/mlua/minecraft/scripted/MLuaBehaviorRegistry.java").read_text()
SETTINGS = (ROOT / "src/main/java/dev/mlua/minecraft/scripted/MLuaScriptedEntityAISettings.java").read_text()
TESTS = (ROOT / "src/test/java/dev/mlua/minecraft/scripted/MLuaScriptedEntityAISettingsTest.java").read_text()


def require(source: str, fragment: str, label: str) -> None:
    if fragment not in source:
        raise SystemExit(f"Scripted entity AI settings contract missing {label}")


require(REGISTRY, "MLuaScriptedEntityAISettings.registerInterval(key, interval)", "compatibility facade")
require(REGISTRY, "MLuaScriptedEntityAISettings.restore(key, expected, previous)", "rollback delegation")
require(SETTINGS, "private static final ConcurrentMap<String, Integer> INTERVALS", "interval state ownership")
require(SETTINGS, "Math.max(1, Math.min(120, interval))", "interval range compatibility")
require(SETTINGS, "current != expected", "conditional rollback behavior")
require(TESTS, "intervalClampsAndRollbackOnlyRestoresTheExpectedRegistration", "regression coverage")

print("Scripted entity AI settings contract passed")
