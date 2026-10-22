from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE_ROOT = ROOT / "src/main/java"
COMMON_ROOTS = [path for path in (ROOT / "mlua-versions/common").iterdir() if path.is_dir()]
OVERLAPS = sorted(
    path.relative_to(root).as_posix()
    for root in COMMON_ROOTS
    for path in root.rglob("*.java")
    if (SOURCE_ROOT / path.relative_to(root)).is_file()
)

helper = (ROOT / "gradle/mlua-targets.gradle").read_text(encoding="utf-8")
if "commonVersionRootExcludes" not in helper:
    raise SystemExit("Common version source ownership helper is missing")
if "targetPathChain('mlua-versions/common/%s', minecraftVersion)" not in helper:
    raise SystemExit("Common source exclusions must follow the resolved target inheritance chain")
if "new File(sourceRoot, relative).isFile()" not in helper:
    raise SystemExit("Common source exclusions must be derived from actual root/overlay path collisions")

packet = (ROOT / "src/main/java/dev/mlua/minecraft/network/MLuaAnimationSyncPacket.java").read_text(encoding="utf-8")
packet_overlay = ROOT / "mlua-versions/common/26.1/dev/mlua/minecraft/network/MLuaAnimationSyncPacket.java"
if packet_overlay.exists():
    raise SystemExit("Animation sync packet must have one root owner with version-scoped Minecraft identifier codecs")
for token in ("ResourceLocation", "Identifier", "readResourceLocation()", "readIdentifier()", "writeResourceLocation(dimension)", "writeIdentifier(dimension)"):
    if token not in packet:
        raise SystemExit(f"Animation sync packet is missing version-scoped codec support for {token}")

root_compilers = (
    "gradle/mlua-fabric-project.gradle",
    "gradle/mlua-neoforge-project.gradle",
    "gradle/mlua-runtime-target.gradle",
    "gradle/mlua-runtime-neoforge-target.gradle",
    "mlua-loaders/forge/1.20.1/project/build.gradle",
    "mlua-loaders/forge/1.21.1/project/build.gradle",
)
for relative in root_compilers:
    source = (ROOT / relative).read_text(encoding="utf-8")
    if "commonVersionRootExcludes(targetVersion)" not in source:
        raise SystemExit(f"{relative} does not exclude root files replaced by common version sources")

delegated_projects = {
    "mlua-loaders/fabric/1.20.1/project/build.gradle": "gradle/mlua-fabric-project.gradle",
    "mlua-loaders/neoforge/1.21.1/project/build.gradle": "gradle/mlua-neoforge-project.gradle",
    "mlua-loaders/neoforge/26.1/project/build.gradle": "gradle/mlua-neoforge-project.gradle",
}
for relative, owner_script in delegated_projects.items():
    source = (ROOT / relative).read_text(encoding="utf-8")
    if owner_script not in source:
        raise SystemExit(f"{relative} must delegate root source selection to {owner_script}")

print(f"Common source ownership contract passed ({len(OVERLAPS)} root/overlay collisions derived from source trees).")
