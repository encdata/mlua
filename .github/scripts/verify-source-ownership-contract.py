from pathlib import Path
import re


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
core_source_list = helper.split("ext.mluaCoreRuntimeSources = [", 1)[1].split("]", 1)[0]
selected_core_sources = set(re.findall(r"'([^']+\.java)'", core_source_list))
available_core_sources = {
    path.relative_to(ROOT / "mlua-core/src/main/java").as_posix()
    for path in (ROOT / "mlua-core/src/main/java").rglob("*.java")
}
if selected_core_sources != available_core_sources:
    missing = sorted(available_core_sources - selected_core_sources)
    stale = sorted(selected_core_sources - available_core_sources)
    raise SystemExit(
        "Runtime target core source selection must include every mlua-core Java source "
        f"and no stale paths; missing={missing}, stale={stale}"
    )

config_schema = (ROOT / "src/main/java/dev/mlua/runtime/config/MLuaConfigSchema.java").read_text(encoding="utf-8")
if "MLuaConfigRegistry.isWithinRange(" not in config_schema or "MLuaConfigRegistry.rangeDescription(" not in config_schema:
    raise SystemExit("Config schema must delegate shared range validation to the registry contract")
config_registries = [
    ROOT / "src/main/java/dev/mlua/runtime/config/MLuaConfigRegistry.java",
    *ROOT.glob("mlua-loaders/**/src/main/java/dev/mlua/runtime/config/MLuaConfigRegistry.java"),
]
for source_path in config_registries:
    source = source_path.read_text(encoding="utf-8")
    for signature in (
        "static boolean isWithinRange(Object value, Double minimum, Double maximum)",
        "static String rangeDescription(Double minimum, Double maximum)",
    ):
        declaration = re.search(rf"^\s*(.*?)\b{re.escape(signature)}", source, re.MULTILINE)
        if declaration is None or "private" in declaration.group(1).split():
            raise SystemExit(f"{source_path.relative_to(ROOT)} must expose package-level {signature}")
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

block_dispatcher = ROOT / "src/main/java/dev/mlua/minecraft/scripted/MLuaBlockCallbackDispatcher.java"
block_dispatcher_overlay = ROOT / "mlua-loaders/neoforge/26.1/project/src/main/java/dev/mlua/minecraft/scripted/MLuaBlockCallbackDispatcher.java"
if block_dispatcher_overlay.exists():
    raise SystemExit("Block callback dispatch must have one owner shared by legacy and 26.1 loaders")
block_dispatcher_source = block_dispatcher.read_text(encoding="utf-8")
for token in ("//--version 26.1", "level.dimension().identifier()", "level.isClientSide()",
              "//--version 1.20.1,1.21.1", "level.dimension().location()", "level.isClientSide;"):
    if token not in block_dispatcher_source:
        raise SystemExit(f"Shared block callback dispatcher is missing version-scoped API support for {token}")

generated_asset_pack = ROOT / "src/main/java/dev/mlua/runtime/assets/MLuaGeneratedAssetPack.java"
generated_asset_pack_overlay = ROOT / "mlua-loaders/common/26.1/project/src/main/java/dev/mlua/runtime/assets/MLuaGeneratedAssetPack.java"
if not generated_asset_pack.is_file() or generated_asset_pack_overlay.exists():
    raise SystemExit("Generated asset pack must have one shared owner with version-specific code in that source")

modern_item = (ROOT / "mlua-versions/common/26.1/dev/mlua/minecraft/scripted/MLuaScriptedItem.java").read_text(encoding="utf-8")
modern_block_item = (ROOT / "mlua-versions/common/26.1/dev/mlua/minecraft/scripted/MLuaScriptedBlockItem.java").read_text(encoding="utf-8")
modern_item_dispatcher = (ROOT / "mlua-versions/common/26.1/dev/mlua/runtime/registry/MLuaItemBehaviorDispatcher.java").read_text(encoding="utf-8")
for method in ("onUse", "onUseOnBlock", "onConsume", "onInventoryTick", "onAttackEntity", "onCrafted"):
    if f"MLuaItemBehaviorDispatcher.{method}(" not in modern_item:
        raise SystemExit(f"26.1 scripted items must delegate {method} behavior to the item dispatcher")
    if f" {method}(" not in modern_item_dispatcher:
        raise SystemExit(f"26.1 item behavior dispatcher is missing {method}")
if "MLuaBehaviorRegistry" in modern_item or "MLuaBehaviorCallbackFailureTracker" in modern_item:
    raise SystemExit("26.1 scripted item hooks must not own Lua callback lookup or failure handling")
for method in ("onUse", "onUseOnBlock", "onConsume", "onInventoryTick", "onAttackEntity", "onCrafted"):
    if f"MLuaItemBehaviorDispatcher.{method}(" not in modern_block_item:
        raise SystemExit(f"26.1 scripted block items must delegate {method} behavior to the item dispatcher")
if "MLuaBehaviorRegistry" in modern_block_item or "MLuaBehaviorCallbackFailureTracker" in modern_block_item:
    raise SystemExit("26.1 scripted block item hooks must not own Lua callback lookup or failure handling")
for field in ("ItemStack", "Entity", "EquipmentSlot"):
    if f'context.set("{field}"' not in modern_item_dispatcher:
        raise SystemExit(f"26.1 item callback contexts are missing {field}")
registered_bow = (ROOT / "src/main/java/dev/mlua/runtime/registry/MLuaRegisteredBow.java").read_text(encoding="utf-8")
if "MLuaItemFactory.invokeOn" in registered_bow:
    raise SystemExit("registered bows must use the shared item behavior dispatcher")
for method in ("onUse", "onUseOnBlock", "onAttackEntity", "onInventoryTick", "onCrafted"):
    if f"MLuaItemBehaviorDispatcher.{method}(" not in registered_bow:
        raise SystemExit(f"registered bows must delegate {method} behavior to the item dispatcher")

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
