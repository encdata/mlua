#!/usr/bin/env python3
"""Keep loader mixin configuration ownership explicit across metadata generations."""

from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"Mixin metadata contract error: {message}", file=sys.stderr)
    raise SystemExit(1)


forge_120 = (ROOT / "mlua-loaders/forge/1.20.1/project/src/main/resources/META-INF/mods.toml").read_text(encoding="utf-8")
forge_120_build = (ROOT / "mlua-loaders/forge/1.20.1/project/build.gradle").read_text(encoding="utf-8")
if 'mixinConfig="mlua.forge.mixins.json"' not in forge_120 \
        or "'MixinConfigs': 'mlua.forge.mixins.json,mlua.firstperson.mixins.json'" not in forge_120_build:
    fail("Forge 1.20.1 must retain its loader-supported mixin declarations")

forge_121_build = (ROOT / "mlua-loaders/forge/1.21.1/project/build.gradle").read_text(encoding="utf-8")
for required in ("prepareForgeMixinMetadata", "[[mixins]]", "mlua.firstperson.mixins.json",
                 "from(prepareForgeMixinMetadata)", "mlua.forge.mixins.json"):
    if required not in forge_121_build:
        fail(f"Forge 1.21.1 mixin metadata generation is missing {required}")
if "'MixinConfigs': 'mlua.forge.mixins.json,mlua.firstperson.mixins.json'" in forge_121_build:
    fail("Forge 1.21.1 must not rely on the ignored legacy manifest mixin list")

neo_build = (ROOT / "gradle/mlua-neoforge-project.gradle").read_text(encoding="utf-8")
for required in ("prepareNeoForgeMixinMetadata", "[[mixins]]", "mlua.service.mixins.json",
                 "neoForgeServiceMixinNames", "'ChatLineMixin'", "from(prepareNeoForgeMixinMetadata)"):
    if required not in neo_build:
        fail(f"NeoForge mixin metadata generation is missing {required}")
if "'MixinConfigs': 'mlua.firstperson.mixins.json,mlua.service.mixins.json'" in neo_build:
    fail("NeoForge must declare service and first-person mixins through mod metadata")

for version in ("1.20.1", "1.21.1", "26.1"):
    path = ROOT / f"mlua-loaders/fabric/{version}/project/src/main/resources/mlua.fabric.mixins.json"
    if not path.is_file() or '"ChatLineMixin"' not in path.read_text(encoding="utf-8"):
        fail(f"Fabric {version} has no chat line bridge mixin")

print("Mixin metadata contract passed for Forge, NeoForge, and Fabric targets.")
