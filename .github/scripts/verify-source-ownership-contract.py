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
