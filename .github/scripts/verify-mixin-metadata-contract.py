#!/usr/bin/env python3
"""Keep loader mixin configuration ownership explicit across metadata generations."""

from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]
PACKAGING = (ROOT / "build.gradle").read_text(encoding="utf-8")
FABRIC_PACKAGING = (ROOT / "gradle/mlua-fabric-project.gradle").read_text(encoding="utf-8")


def fail(message: str) -> None:
    print(f"Mixin metadata contract error: {message}", file=sys.stderr)
    raise SystemExit(1)


forge_120 = (ROOT / "mlua-loaders/forge/1.20.1/project/src/main/resources/META-INF/mods.toml").read_text(encoding="utf-8")
forge_120_build = (ROOT / "mlua-loaders/forge/1.20.1/project/build.gradle").read_text(encoding="utf-8")
if 'mixinConfig=' in forge_120 or "'MixinConfigs': 'mlua.firstperson.mixins.json'" not in forge_120_build \
        or "config 'mlua.firstperson.mixins.json'" not in forge_120_build \
        or "exclude 'mlua.forge.mixins.json'" not in forge_120_build:
    fail("Forge 1.20.1 must keep first-person mixins in the loader JAR and service mixins in the runtime JAR")

forge_121_build = (ROOT / "mlua-loaders/forge/1.21.1/project/build.gradle").read_text(encoding="utf-8")
for required in ("MixinConfigs", "'MixinConfigs': 'mlua.firstperson.mixins.json'",
                 "prepareForgeProjectResources/META-INF/mods.toml", "config 'mlua.firstperson.mixins.json'",
                 "exclude 'mlua.forge.mixins.json'"):
    if required not in forge_121_build:
        fail(f"Forge 1.21.1 mixin metadata generation is missing {required}")
if "'MixinConfigs': 'mlua.forge.mixins.json,mlua.firstperson.mixins.json'" in forge_121_build:
    fail("Forge 1.21.1 service mixin config must be registered by its embedded runtime JAR")
runtime_forge_build = (ROOT / "gradle/mlua-runtime-target.gradle").read_text(encoding="utf-8")
if "'MixinConfigs': 'mlua.forge.mixins.json'" not in runtime_forge_build:
    fail("Forge runtime JAR must register its service mixin config in its own manifest")
for metadata_path in (ROOT / "src/main/resources/META-INF/mods.toml",
                      ROOT / "mlua-loaders/forge/1.20.1/project/src/main/resources/META-INF/mods.toml"):
    if "mixinConfig=" in metadata_path.read_text(encoding="utf-8"):
        fail(f"{metadata_path.relative_to(ROOT)} must leave mixin ownership with the JAR manifests")

neo_build = (ROOT / "gradle/mlua-neoforge-project.gradle").read_text(encoding="utf-8")
for required in ("prepareNeoForgeMixinMetadata", "[[mixins]]", "mlua.service.mixins.json",
                 "neoForgeServiceMixinNames", "'ChatLineMixin'", "from(prepareNeoForgeMixinMetadata)",
                 "metadata.indexOf('[[mods]]')", "metadata.substring(0, modsHeader) + mixinMetadata"):
    if required not in neo_build:
        fail(f"NeoForge mixin metadata generation is missing {required}")
if "'MixinConfigs': 'mlua.firstperson.mixins.json,mlua.service.mixins.json'" in neo_build:
    fail("NeoForge must declare service and first-person mixins through mod metadata")
if 'from(file("${buildDir}/generated/prepareNeoForgeProjectResources/META-INF/neoforge.mods.toml"))' in neo_build:
    fail("NeoForge must package the generated mixin metadata instead of the unmodified TOML")
if "expand(minecraft_version: minecraft_version" not in neo_build:
    fail("NeoForge's generated mixin metadata must still expand its version properties")
if "exclude 'mlua.forge.mixins.json'" not in FABRIC_PACKAGING:
    fail("Fabric artifacts must exclude the Forge-only mixin configuration")
for required in ("registeredMixinConfigs", "mixinFiles.findAll", "fabric.mod.json",
                 "META-INF/mods.toml", "META-INF/neoforge.mods.toml", "MixinConfigs"):
    if required not in PACKAGING:
        fail(f"Packaged artifact validation does not verify mixin registration: {required}")

for mixin_path in (ROOT / "src/main/java/dev/mlua/modules/mixin/service/ScreenLayoutMixin.java",
                   ROOT / "mlua-loaders/neoforge/1.21.1/impl/dev/mlua/mixin/ScreenLayoutMixin.java"):
    if '@Inject(method = "init()V"' not in mixin_path.read_text(encoding="utf-8"):
        fail(f"{mixin_path.relative_to(ROOT)} must target Screen.init() after widgets are built")

for version in ("1.20.1", "1.21.1", "26.1"):
    path = ROOT / f"mlua-loaders/fabric/{version}/project/src/main/resources/mlua.fabric.mixins.json"
    if not path.is_file() or '"ChatLineMixin"' not in path.read_text(encoding="utf-8"):
        fail(f"Fabric {version} has no chat line bridge mixin")

print("Mixin metadata contract passed for Forge, NeoForge, and Fabric targets.")
