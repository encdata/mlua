from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
AGENT = (ROOT / "mlua-core/src/main/java/dev/mlua/agent/MLuaMixinAgent.java").read_text()
BOOTSTRAP = (ROOT / "mlua-core/src/main/java/dev/mlua/agent/MLuaAgentBootstrap.java").read_text()


def require(source: str, fragment: str, label: str) -> None:
    if fragment not in source:
        raise SystemExit(f"Mixin agent classloader contract missing {label}")


require(AGENT, "sharesRuntimeMixinEngine(fallback, fallback)", "mod-loader preference")
require(AGENT, "sharesRuntimeMixinEngine(moduleLoader, fallback)", "module-loader compatibility check")
require(AGENT, "runtimeEngine == transformerEngine", "shared MixinRuleEngine identity check")
require(AGENT, "if (transformerLoader == null)", "refusal to install a mismatched transformer")
require(BOOTSTRAP, "findCompatibleTransformerClass(runtimeLoader, runtimeLoader)", "runtime-loader lookup")
require(BOOTSTRAP, "findCompatibleTransformerClass(moduleLoader, runtimeLoader)", "module-loader compatibility lookup")
require(BOOTSTRAP, "runtimeEngine == transformerEngine", "bootstrap MixinRuleEngine identity check")

print("Mixin agent classloader contract passed")
