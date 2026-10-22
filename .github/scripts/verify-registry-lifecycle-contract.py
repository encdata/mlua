#!/usr/bin/env python3
"""Guard rollback ownership for scripted world generation registrations."""

from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "src/main/java/dev/mlua/modules/registry/service/MLuaRegistryService.java"
API_ROOT = ROOT / "src/main/java/dev/mlua/modules/registry/service"
BRIDGE_API = ROOT / "mlua-loader-api/src/main/java/dev/mlua/loaderapi/WorldGenerationBridge.java"
DATA_BRIDGE = ROOT / "mlua-loader-api/src/main/java/dev/mlua/loaderapi/LoaderWorldGenerationBridges.java"
FABRIC_BRIDGE = ROOT / "mlua-loaders/fabric/1.20.1/project/src/main/java/dev/mlua/loader/impl/fabric/FabricWorldGenerationBridge.java"
REGRESSION_TEST = ROOT / "src/test/java/dev/mlua/loaderapi/WorldGenerationBridgeTest.java"


def fail(message: str) -> None:
    print(f"Registry lifecycle contract error: {message}", file=sys.stderr)
    raise SystemExit(1)


service = SERVICE.read_text(encoding="utf-8")
api_sources = "\n".join(
    path.read_text(encoding="utf-8")
    for path in sorted(API_ROOT.glob("MLuaRegistry*Api.java"))
)
add_feature = api_sources.split('worldGeneration.set("addFeature"', 1)[-1].split(
    'worldGeneration.set("AddFeature"', 1
)[0]
if "bridge.addPlacedFeature(owner, name, featureId, biomeIds, registeredStep)" not in add_feature:
    fail("Registry.WorldGeneration:AddFeature does not delegate its accepted definition")
if "registration().trackRollback" not in add_feature or "bridge.removePlacedFeature" not in add_feature:
    fail("world generation contributions are not removed when their script registration rolls back")

bridge_api = BRIDGE_API.read_text(encoding="utf-8")
if "default boolean removePlacedFeature" not in bridge_api:
    fail("loader bridge does not expose a backward-compatible removal operation")
for required in (
    "MAX_PLACED_FEATURES_TOTAL",
    "MAX_PLACED_FEATURES_PER_ADDON",
    "FeatureCounts featureCounts(String owner)",
):
    if required not in bridge_api:
        fail(f"world generation bridge is missing shared resource limits or counts: {required}")

data_bridge = DATA_BRIDGE.read_text(encoding="utf-8")
if "GeneratedDataResources.unregister(owner, resourceType, name," not in data_bridge:
    fail("Forge and NeoForge cleanup does not compare the expected generated definition")

fabric_bridge = FABRIC_BRIDGE.read_text(encoding="utf-8")
for required in (
    "ensureModifierInstalled(owner)",
    'owner + ":_mlua/scripted_world_features"',
    "REGISTRATIONS.remove(expected.id())",
    "registrationsSnapshot(owner)",
    "registrationsSnapshot(owner)",
    "ownerRegistrations.sort(java.util.Comparator.comparing(FeatureRegistration::id))",
    "MAX_PLACED_FEATURES_TOTAL",
    "MAX_PLACED_FEATURES_PER_ADDON",
    "FeatureCounts featureCounts(String owner)",
):
    if required not in fabric_bridge:
        fail(f"Fabric's dynamic biome modifier is missing required behavior: {required}")

test = REGRESSION_TEST.read_text(encoding="utf-8")
if "rollback must preserve a definition that does not match" not in test:
    fail("the world generation bridge regression test does not cover mismatched rollback")
if "assertFalse(GeneratedDataResources.contains(resourceType, fullId))" not in test:
    fail("the world generation bridge regression test does not verify successful cleanup")
if "bridge.featureCounts(owner).forAddon()" not in test:
    fail("the world generation bridge regression test does not verify addon-owned usage counts")

runtime = (ROOT / "src/main/java/dev/mlua/core/MLuaRuntime.java").read_text(encoding="utf-8")
generated_pack_path = ROOT / "src/main/java/dev/mlua/runtime/assets/MLuaGeneratedAssetPack.java"
generated_pack = generated_pack_path.read_text(encoding="utf-8")
if runtime.count("this.generateAssetPack();") < 2:
    fail("registry startup and resource reload must both refresh generated pack data")
if "else MLuaGeneratedAssetPack.generate();" not in runtime:
    fail("dedicated servers must generate MLua data-pack resources without loading client hooks")
if "net.minecraft.client" in generated_pack:
    fail("generated data-pack generation must remain safe to call on a dedicated server")
if "//--version 26.1" not in generated_pack or "pack.addProperty(\"min_format\", 84)" not in generated_pack:
    fail("shared generated-pack source must retain the 26.1 resource pack metadata branch")
if "dev.mlua.runtime.assets.MLuaGeneratedAssetPack" not in runtime:
    fail("runtime startup must depend on the shared asset-pack owner, not client runtime internals")

registry_api = (API_ROOT / "MLuaRegistryApi.java").read_text(encoding="utf-8")
limits_api_path = API_ROOT / "MLuaRegistryLimitsApi.java"
if not limits_api_path.is_file():
    fail("Registry script limit snapshots must have a dedicated API owner")
limits_api = limits_api_path.read_text(encoding="utf-8")
if "MLuaRegistryLimitsApi.install(service, table)" not in registry_api:
    fail("Registry API assembly does not delegate script limits to their owner")
if 'registry.set("GetScriptLimits"' not in limits_api or 'table.set("GetScriptLimits"' in registry_api:
    fail("GetScriptLimits registration remains mixed into the registry API assembler")
for field in ("ActiveBehaviors", "ActiveRegistryDefinitions", "ActivePlacedFeatures", "MaxPlacedFeatures"):
    if f'limits.set("{field}"' not in limits_api:
        fail(f"Registry script limit snapshot is missing {field}")
root_queries = (API_ROOT / "MLuaRegistryRootQueries.java").read_text(encoding="utf-8")
if "MLuaRegistryRootQueries.install(service, table)" not in registry_api:
    fail("Registry root lookup methods are not installed through their query owner")
for method in (
    "GetTypes", "ResolveType", "GetRegistered", "GetEntries", "GetKinds", "GetCategories",
    "GetCategory", "Get", "GetAll", "GetIds", "GetEntry", "Exists",
):
    if f'table.set("{method}"' not in root_queries:
        fail(f"Registry root query owner is missing {method}")
    if f'table.set("{method}"' in registry_api:
        fail(f"Registry API builder still owns root query method {method}")
root_content_types = (API_ROOT / "MLuaRegistryContentTypes.java").read_text(encoding="utf-8")
if "installRootContentTypeApi(contentTypes)" not in registry_api or "void installRootApi(LuaTable target)" not in root_content_types:
    fail("Registry content type API is not assembled by its content schema owner")
for method in ("Register", "GetTypes", "GetLimits", "Resolve"):
    if f'target.set("{method}"' not in root_content_types:
        fail(f"Registry content schema owner is missing root method {method}")
    if f'contentTypes.set("{method}"' in registry_api:
        fail(f"Registry API builder still owns content schema method {method}")
registry_category_owners = {
    "MLuaRegistryCreativeTabApi.java": ("Registry.Tabs:Register", "MLuaRegistryCreativeTabApi.install(service, tabs)"),
    "MLuaRegistryVisualContentApi.java": ("Registry.Particles:Register", "MLuaRegistryVisualContentApi.install(service, particles, potions, effects)"),
    "MLuaRegistryConfigApi.java": ("RegisterCommon", "MLuaRegistryConfigApi.install(service, config)"),
    "MLuaRegistryDamageSourceApi.java": ("MLuaDamageSourceRegistry.register(", "MLuaRegistryDamageSourceApi.install(service, damageSource)"),
}
for filename, (operation, delegation) in registry_category_owners.items():
    owner_path = API_ROOT / filename
    if not owner_path.is_file() or operation not in owner_path.read_text(encoding="utf-8"):
        fail(f"Registry category {filename} does not own {operation}")
    if delegation not in registry_api:
        fail(f"Registry API does not delegate {filename} through its dedicated owner")
if any(f'{name}.set("Register"' in registry_api for name in ("tabs", "particles", "potions", "effects", "config", "damageSource")):
    fail("Registry API builder still owns built-in category registration callbacks")

# Visual block entity types are required for scripted blocks with containers
# and callbacks. Keep registration on the loader's native lifecycle.
forge_registry = (ROOT / "src/main/java/dev/mlua/runtime/registry/MLuaForgeRegistry.java").read_text(encoding="utf-8")
neoforge_1211_registry = (ROOT / "mlua-loaders/neoforge/1.21.1/project/src/main/java/dev/mlua/runtime/neoforge/MLuaNeoForgeRegistry.java").read_text(encoding="utf-8")
neoforge_261_registry = (ROOT / "mlua-loaders/neoforge/26.1/project/src/main/java/dev/mlua/runtime/neoforge/MLuaNeoForgeRegistry.java").read_text(encoding="utf-8")
for loader, source in (("Forge", forge_registry), ("NeoForge 1.21.1", neoforge_1211_registry)):
    if "private DeferredRegister<BlockEntityType<?>> blockEntityRegister(String modId)" not in source \
            or "this.attachIfNeeded(id, DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, id))" not in source:
        fail(f"{loader} visual block entity registry is not attached to its mod event bus")
if "new BlockEntityType<>(MLuaVisualBlockEntity::new, validBlocks)" not in neoforge_261_registry:
    fail("NeoForge 26.1 visual block entity registration has no native BlockEntityType constructor")
fabric_registry = (ROOT / "mlua-loaders/fabric/1.21.1/project/src/main/java/dev/mlua/loader/impl/fabric/FabricRegistryBridge.java").read_text(encoding="utf-8")
if "FabricBlockEntityTypeBuilder" not in fabric_registry or ".build();" not in fabric_registry:
    fail("Fabric visual block entity registration does not use Fabric's cross-version builder")
fabric_build = (ROOT / "gradle/mlua-fabric-project.gradle").read_text(encoding="utf-8")
if "registerPendingVisualBlockEntityTypes() {}" in fabric_build:
    fail("Fabric target generation still removes visual block entity registration")
runtime_provider = (ROOT / "mlua-versions/common/1.20.1/dev/mlua/mc/impl/MCApiVersionRuntimeProvider.java").read_text(encoding="utf-8")
if "getParameterTypes()[0].isInstance(value)" not in runtime_provider:
    fail("registry key lookup does not resolve the registry's actual value parameter type")

entity_api = (API_ROOT / "MLuaRegistryEntityApi.java").read_text(encoding="utf-8")
entity_definitions_path = API_ROOT / "MLuaRegistryEntityDefinitions.java"
if not entity_definitions_path.is_file():
    fail("scripted entity property validation must have a dedicated owner")
entity_definitions = entity_definitions_path.read_text(encoding="utf-8")
for operation in ("attributeValue(", "additionalAttributes(", "addAttribute(",
                  "requirePositiveSize(", "mobCategory(", "colorProperty("):
    if operation not in entity_definitions or f"MLuaRegistryEntityDefinitions.{operation}" not in entity_api:
        fail(f"scripted entity registration does not delegate {operation} to its definition owner")
if "ENTITY_ATTRIBUTE_ALIASES" in (API_ROOT / "MLuaRegistryDefinitions.java").read_text(encoding="utf-8"):
    fail("generic registry definitions still own entity-specific attribute rules")

container_definitions_path = API_ROOT / "MLuaRegistryContainerDefinitions.java"
if not container_definitions_path.is_file():
    fail("container slot validation must have a dedicated owner")
container_definitions = container_definitions_path.read_text(encoding="utf-8")
container_api = (API_ROOT / "MLuaRegistryContainerApi.java").read_text(encoding="utf-8")
item_block_api = (API_ROOT / "MLuaRegistryItemBlockApi.java").read_text(encoding="utf-8")
for operation in ("allowedItems(", "slotAllowedItems(", "slotsLength(", "property("):
    if operation not in container_definitions:
        fail(f"container definition owner is missing {operation}")
if "MLuaRegistryContainerDefinitions.slotAllowedItems(" not in container_api or \
        "MLuaRegistryContainerDefinitions.slotAllowedItems(" not in item_block_api:
    fail("both container registration paths must use the shared container definition owner")
if "containerSlotAllowedItems" in (API_ROOT / "MLuaRegistryDefinitions.java").read_text(encoding="utf-8"):
    fail("generic registry definitions still own container-specific slot parsing")

print("Registry lifecycle contract passed for script rollback and loader ownership.")
