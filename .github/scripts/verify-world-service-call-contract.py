#!/usr/bin/env python3
"""Check that every World service implementation normalizes colon and dot calls."""

from pathlib import Path
import json
import re
import sys


ROOT = Path(__file__).resolve().parents[2]
TARGETS = ROOT / "gradle/mlua-targets.json"
SHARED = ROOT / "src/main/java/dev/mlua/modules/world/service/MLuaWorldService.java"
FABRIC_LEGACY_OVERLAY = ROOT / "mlua-loaders/fabric/1.20.1/impl/dev/mlua/modules/world/service/MLuaWorldService.java"
COMMON_26 = ROOT / "mlua-versions/common/26.1/dev/mlua/modules/world/service/MLuaWorldService.java"
GAME_RULES_26 = ROOT / "mlua-versions/common/26.1/dev/mlua/modules/world/MLuaWorldGameRules26.java"
GAME_RULES_SHARED = ROOT / "src/main/java/dev/mlua/modules/world/MLuaWorldGameRules.java"
GAME_RULE_ENTRY = ROOT / "src/main/java/dev/mlua/modules/world/MLuaWorldGameRuleEntry.java"
RAYCAST_26 = ROOT / "mlua-versions/common/26.1/dev/mlua/modules/world/service/MLuaWorldRaycastOperations26.java"
RAYCAST_API_26 = ROOT / "mlua-versions/common/26.1/dev/mlua/modules/world/service/MLuaWorldRaycastApi26.java"
ENVIRONMENT_API_26 = ROOT / "mlua-versions/common/26.1/dev/mlua/modules/world/service/MLuaWorldEnvironmentApi26.java"
BLOCK_MUTATION_API_26 = ROOT / "mlua-versions/common/26.1/dev/mlua/modules/world/service/MLuaWorldBlockMutationApi26.java"
EFFECTS_API_26 = ROOT / "mlua-versions/common/26.1/dev/mlua/modules/world/service/MLuaWorldEffectsApi26.java"
ENTITY_QUERY_API_26 = ROOT / "mlua-versions/common/26.1/dev/mlua/modules/world/service/MLuaWorldEntityQueryApi26.java"
DATA_API_26 = ROOT / "mlua-versions/common/26.1/dev/mlua/modules/world/service/MLuaWorldDataApi26.java"
WORLD_MODULE = ROOT / "src/main/java/dev/mlua/modules/world/WorldModule.java"
SCRIPTED_BLOCK = ROOT / "src/main/java/dev/mlua/minecraft/scripted/MLuaScriptedBlock.java"
CHUNK_WRAPPER = ROOT / "src/main/java/dev/mlua/modules/world/MLuaChunkWrapper.java"
WORLD_CHUNK_API = ROOT / "src/main/java/dev/mlua/modules/world/MLuaWorldChunkApi.java"
GAME_THREAD_ACCESS = ROOT / "src/main/java/dev/mlua/runtime/MLuaGameThreadAccess.java"


def fail(message: str) -> None:
    print(f"World service call contract error: {message}", file=sys.stderr)
    raise SystemExit(1)


def method_body(source: str, signature: str) -> str:
    match = re.search(signature, source)
    if match is None:
        fail(f"could not find {signature}")
    opening = source.find("{", match.end())
    if opening < 0:
        fail(f"could not find body for {signature}")
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:index]
    fail(f"unterminated body for {signature}")


targets = json.loads(TARGETS.read_text(encoding="utf-8"))["targets"]
pairs = sorted({(str(target["minecraft"]), loader)
                for target in targets for loader in target["loaders"]})
implementations = {}
for version, loader in pairs:
    if version.startswith("26."):
        path = COMMON_26
    else:
        path = SHARED
    if not path.is_file():
        fail(f"{loader} {version} selects a missing implementation: {path}")
    implementations.setdefault(path, []).append((version, loader))

game_thread_access = GAME_THREAD_ACCESS.read_text(encoding="utf-8")
if "MLuaClientThread.callForClientState(action)" not in game_thread_access:
    fail("shared game-thread access does not marshal client world calls")
if "MLuaServerThread.callChecked(serverLevel.getServer(), action::call)" not in game_thread_access:
    fail("shared game-thread access does not marshal server world calls")
if "return level.isClientSide();" not in game_thread_access or "return level.isClientSide;" not in game_thread_access:
    fail("shared game-thread access must resolve the target-specific client-side API")

for path, expected in (
    (SHARED, "MLuaGameThreadAccess.callUnchecked(context, () -> fn.apply(args))"),
    (COMMON_26, "MLuaGameThreadAccess.callUnchecked(context, () -> action.apply(args))"),
):
    if expected not in path.read_text(encoding="utf-8"):
        fail(f"{path.name} must route World service calls through the shared game-thread policy")
world_api_support = (ROOT / "src/main/java/dev/mlua/modules/world/service/MLuaWorldApiSupport.java").read_text(encoding="utf-8")
if "MLuaGameThreadAccess.callUnchecked(level, () -> function.apply(level, args))" not in world_api_support:
    fail("legacy World instance queries must dispatch on the owning client or server thread")
if "MLuaGameThreadAccess.callUnchecked(serverLevel, () -> function.apply(serverLevel, args))" not in world_api_support:
    fail("legacy server World mutations must use the shared game-thread policy")
world26 = COMMON_26.read_text(encoding="utf-8")
if "MLuaGameThreadAccess.callUnchecked(level, () -> action.apply(level, args))" not in world26:
    fail("26.1 World instance queries must dispatch on the owning client or server thread")
chunk_api = WORLD_CHUNK_API.read_text(encoding="utf-8")
if "MLuaGameThreadAccess.call(level, () -> body.apply(args))" not in chunk_api:
    fail("World chunk operations must dispatch on the owning client or server thread")
chunk_wrapper = CHUNK_WRAPPER.read_text(encoding="utf-8")
if "MLuaGameThreadAccess.call(world, () -> body.apply(args))" not in chunk_wrapper:
    fail("live Chunk instance methods must dispatch on their owning client or server thread")
for source, owner in ((chunk_wrapper, "Chunk instance methods"), (chunk_api, "World chunk methods"),
                      (world_api_support, "World API chunk loading")):
    if "MLuaServerThread.call" in source:
        fail(f"{owner} must rely on the shared API boundary instead of nested server-thread dispatch")

if FABRIC_LEGACY_OVERLAY.exists():
    fail("Fabric must use the shared legacy World service; its stale loader overlay still exists")
fabric_build = (ROOT / "gradle/mlua-fabric-project.gradle").read_text(encoding="utf-8")
world_service_path = "dev/mlua/modules/world/service/MLuaWorldService.java"
if not re.search(r"def\s+fabricImplExcludes\s*=\s*\[\s*'" + re.escape(world_service_path) + r"'\s*\]", fabric_build):
    fail("Fabric source selection must explicitly exclude the stale implementation overlay")
if re.search(r"rootExcludes\s*=\s*\[[^\]]*'" + re.escape(world_service_path) + r"'", fabric_build):
    fail("Fabric must keep the shared World service for legacy targets")
if not re.search(r"if\s*\(is26Family\)\s*\{[^}]*rootExcludes\s*\+=\s*'"
                 + re.escape(world_service_path) + r"'", fabric_build, re.DOTALL):
    fail("Fabric 26.1 must select its common version World service implementation")

for path, covered_targets in implementations.items():
    source = path.read_text(encoding="utf-8")
    body = method_body(source, r"\bpublic\s+LuaTable\s+toLua\s*\(\s*\)")
    if "supportColonAndDotCalls(table)" not in body:
        labels = ", ".join(f"{loader} {version}" for version, loader in covered_targets)
        fail(f"{labels} World service does not normalize colon and dot calls")
    if not re.search(r'table\.set\("Get"\s*,', body):
        labels = ", ".join(f"{loader} {version}" for version, loader in covered_targets)
        fail(f"{labels} World service no longer exposes Get")
    if "MLuaServiceMetadata.attachMethodDescriptions(table, WorldModule.worldServiceMethods())" not in body:
        labels = ", ".join(f"{loader} {version}" for version, loader in covered_targets)
        fail(f"{labels} World service methods are missing their registered descriptions")

bounds_helper = ROOT / "src/main/java/dev/mlua/modules/entity/MLuaEntityBounds.java"
if not bounds_helper.is_file():
    fail("shared EntityBounds implementation is missing")
for path in (SHARED, COMMON_26):
    source = path.read_text(encoding="utf-8")
    if path == SHARED:
        raycast_api = ROOT / "src/main/java/dev/mlua/modules/world/service/MLuaWorldRaycastApi.java"
        entity_query_api = ROOT / "src/main/java/dev/mlua/modules/world/service/MLuaWorldEntityQueryApi.java"
        environment_api = ROOT / "src/main/java/dev/mlua/modules/world/service/MLuaWorldEnvironmentApi.java"
        if not raycast_api.is_file() or not entity_query_api.is_file():
            fail("shared world raycast API binding is missing")
        if not environment_api.is_file():
            fail("shared world environment API binding is missing")
        source += (raycast_api.read_text(encoding="utf-8")
                   + entity_query_api.read_text(encoding="utf-8")
                   + environment_api.read_text(encoding="utf-8"))
    elif path == COMMON_26:
        source += "\n".join(owner.read_text(encoding="utf-8") for owner in (
            RAYCAST_26, RAYCAST_API_26, ENVIRONMENT_API_26, BLOCK_MUTATION_API_26,
            EFFECTS_API_26, ENTITY_QUERY_API_26, DATA_API_26))
    if "MLuaEntityBounds.fromLua(" not in source or "MLuaEntityBounds.rayHitNormal(" not in source:
        fail(f"{path} does not delegate bounds conversion and hit normals to the entity module")
    if re.search(r"\bprivate\s+static\s+(?:AABB|Vec3)\s+(?:entityBounds|rayHitNormal)\s*\(", source):
        fail(f"{path} duplicates EntityBounds geometry implementation")

# World binders may install instance methods either as pending raw methods or
# directly into the class instance method store. Keep both paths valid.
world_module_text = WORLD_MODULE.read_text(encoding="utf-8")
world_binder = method_body(world_module_text,
                           r"\bpublic\s+static\s+void\s+bindWorldInstanceMethods\s*\(")
if '"__mlua_instance_methods"' not in world_binder:
    fail("World method finalization no longer recognizes methods installed by shared binders")

scripted_block_text = SCRIPTED_BLOCK.read_text(encoding="utf-8")
block_entity_factory = method_body(scripted_block_text,
                                   r"\bpublic\s+BlockEntity\s+newBlockEntity\s*\(")
if "configureContainer(container.rows() * 9)" not in block_entity_factory:
    fail("scripted containers are not initialized when their block entity is created")

query_helper = ROOT / "src/main/java/dev/mlua/modules/world/service/MLuaWorldEntityQueries.java"
if not query_helper.is_file():
    fail("shared world entity query result implementation is missing")
query_api = ROOT / "src/main/java/dev/mlua/modules/world/service/MLuaWorldEntityQueryApi.java"
if not query_api.is_file():
    fail("shared world entity query API binding is missing")
for path in (SHARED, COMMON_26):
    source = path.read_text(encoding="utf-8")
    if path == SHARED:
        source += query_api.read_text(encoding="utf-8")
    elif path == COMMON_26:
        source += ENTITY_QUERY_API_26.read_text(encoding="utf-8")
    if "MLuaWorldEntityQueries." not in source:
        fail(f"{path} does not use shared world entity query results")

shared_world = SHARED.read_text(encoding="utf-8")
query_api_source = query_api.read_text(encoding="utf-8")
world_factory = method_body(shared_world, r"\bprivate\s+synchronized\s+LuaTable\s+worldTable\s*\(")
environment_api = ROOT / "src/main/java/dev/mlua/modules/world/service/MLuaWorldEnvironmentApi.java"
block_query_api = ROOT / "src/main/java/dev/mlua/modules/world/service/MLuaWorldBlockQueryApi.java"
data_api = ROOT / "src/main/java/dev/mlua/modules/world/service/MLuaWorldDataApi.java"
if "MLuaWorldEnvironmentApi.bind(table, supplier, context)" not in world_factory:
    fail("shared World service does not delegate environment and dimension methods")
for binding, owner in (
    ("MLuaWorldBlockQueryApi.bind(table, supplier, context)", block_query_api),
    ("MLuaWorldDataApi.bind(table, supplier, context)", data_api),
):
    if binding not in world_factory:
        fail(f"shared World service does not delegate to {owner.name}")
environment_source = environment_api.read_text(encoding="utf-8")
if 'table.rawset("GetId"' not in environment_source:
    fail("live World instances must install methods without triggering registered-class __newindex checks")
for owner in (environment_api, block_query_api, data_api):
    owner_source = owner.read_text(encoding="utf-8")
    if not owner_source.startswith("//--version 1.20.1,1.21.1\n") or "//--endversion" not in owner_source:
        fail(f"{owner.name} must be excluded from 26.x, which has its own World API implementation")
for owner, methods in (
    (environment_api, ("GetId", "GetDimension", "GetTime", "SetTime", "AddTime", "GetWeather",
                       "IsRaining", "IsThundering", "IsRainingAt", "SetWeather", "GetDifficulty",
                       "SetDifficulty", "ListGameRules", "GetGameRule", "GetGameRuleEntry", "SetGameRule")),
    (block_query_api, ("GetBlock", "GetBlockEntity", "GetBlockState", "GetFluid", "GetFluidState",
                       "GetBiome", "GetLightLevel", "GetSkyLight", "GetBlockLight", "CanSeeSky",
                       "GetSurfaceY", "GetBlockInventory")),
    (data_api, ("GetPersistentData", "GetSyncedData", "GetData", "SetData", "RemoveData")),
):
    owner_source = owner.read_text(encoding="utf-8")
    for method in methods:
        binding = f'table.rawset("{method}"'
        if binding not in owner_source:
            fail(f"{owner.name} does not own the World:{method} binding")
        if binding in shared_world:
            fail(f"World:{method} is still bound inline in MLuaWorldService")
if re.search(r"\btable\.set\(\"(?:Get|Set|Is|Can|Load|Unload|Force|Add|Remove|List|Raycast|Spawn|Drop|Break|Explode)", world_factory):
    fail("live World method implementations must use raw instance setup before class dispatch binding")
if "for (Player player : level.players())" not in query_api_source:
    fail("shared World:FindNearestPlayer must query the world's player list directly")
if "this.players(level)" in query_api_source or "allEntities(Level level)" in query_api_source:
    fail("shared World service must not rescan every loaded entity to collect players")

mutation_api = ROOT / "src/main/java/dev/mlua/modules/world/service/MLuaWorldMutationApi.java"
if not mutation_api.is_file() or "MLuaWorldMutationApi.bind(" not in shared_world:
    fail("shared World service does not delegate server-side changes to the mutation API owner")
mutation_api_source = mutation_api.read_text(encoding="utf-8")
for method in ("SetBlock", "SetBlockState", "BreakBlock", "PlaySound", "SpawnParticle",
               "SpawnEntity", "DropItem", "Explode", "SpawnLightning"):
    if f'table.rawset("{method}"' not in mutation_api_source:
        fail(f"world mutation API owner is missing {method}")
if 'if (entity == null) return LuaValue.NIL;' not in mutation_api_source:
    fail("World:SpawnEntity must return nil when Minecraft rejects the spawn")

raycast_api = ROOT / "src/main/java/dev/mlua/modules/world/service/MLuaWorldRaycastApi.java"
if not raycast_api.is_file() or "MLuaWorldRaycastApi.bind(" not in shared_world:
    fail("shared World service does not delegate raycasts to their API owner")
for method in ("Raycast", "RaycastEntity", "RaycastAABB"):
    if f'table.rawset("{method}"' not in raycast_api.read_text(encoding="utf-8"):
        fail(f"world raycast API owner is missing {method}")

query_api = ROOT / "src/main/java/dev/mlua/modules/world/service/MLuaWorldEntityQueryApi.java"
query_api_source = query_api.read_text(encoding="utf-8")
for method in ("GetPlayers", "GetEntities", "FindEntitiesInRadius", "GetEntitiesInRadius",
               "GetEntitiesNearby", "FindEntitiesInBox", "FindEntitiesInBounds",
               "FindPlayersInBounds", "FindPlayersInRadius", "GetPlayersNearby",
               "FindNearestPlayer", "GetNearestPlayer", "FindNearestEntity"):
    if f'table.rawset("{method}"' not in query_api_source:
        fail(f"world entity query API owner is missing internal binding for {method}")

instance_cache = ROOT / "src/main/java/dev/mlua/modules/world/service/MLuaWorldInstanceCache.java"
if not instance_cache.is_file():
    fail("shared world instance cache is missing")
for path in (SHARED, COMMON_26):
    source = path.read_text(encoding="utf-8")
    if "new MLuaWorldInstanceCache()" not in source:
        fail(f"{path} does not use shared world wrapper identity management")
    if "WeakHashMap<Level" in source or "WeakReference<LuaTable>" in source:
        fail(f"{path} duplicates world wrapper cache ownership")

common26_source = COMMON_26.read_text(encoding="utf-8")
raycast_26_source = RAYCAST_26.read_text(encoding="utf-8")
raycast_api_26_source = RAYCAST_API_26.read_text(encoding="utf-8")
data_api_26_source = DATA_API_26.read_text(encoding="utf-8")
game_rules_26_source = GAME_RULES_26.read_text(encoding="utf-8")
for required_import in (
    "import dev.mlua.modules.entity.MLuaEntityBounds;",
    "import dev.mlua.modules.entity.MLuaEntityQuery;",
    "import dev.mlua.modules.world.MLuaPositionArguments;",
    "import net.minecraft.world.level.ClipContext;",
):
    if required_import not in raycast_26_source:
        fail(f"26.1 raycast owner is missing version-correct import {required_import}")
if "import dev.mlua.modules.world.MLuaWorldGameRules26;" not in data_api_26_source:
    fail("26.1 World data API is missing the extracted gamerule helper import")
for operation in (".list(world,", ".get(world,", ".entry(world,", ".set(serverLevel,"):
    if f"MLuaWorldGameRules26{operation}" not in data_api_26_source:
        fail(f"26.1 World data API is missing gamerule delegation {operation}")
if "MLuaWorldGameRules26.set(serverLevel, name, value, serverSupplier.get())" not in game_rules_26_source:
    fail("26.1 gamerule entry setter does not resolve to the typed helper")
if "camelCaseName(rule.getIdentifier().getPath()).equals(name)" not in game_rules_26_source:
    fail("26.1 gamerule lookup must accept Minecraft camelCase names as well as registry ids")
if "table.set(camelCaseName(name), entry.get(\"Value\"))" not in game_rules_26_source:
    fail("26.1 gamerule listing must expose Minecraft camelCase names")
for binding, delegated in (
    ('"RaycastAABB"', "MLuaWorldRaycastOperations26.raycastBounds"),
    ('"RaycastEntity"', "MLuaWorldRaycastOperations26.raycastEntity"),
):
    if binding not in raycast_api_26_source or delegated not in raycast_api_26_source:
        fail(f"26.1 World raycast API is missing the dedicated raycast owner for {binding}")
if "private LuaValue raycastBounds(" in common26_source or "private LuaValue raycastEntity(" in common26_source:
    fail("26.1 World service still owns ray traversal implementations inline")
for method in ("raycastBounds", "raycastEntity", "requireFinite"):
    if f"{method}(" not in raycast_26_source:
        fail(f"26.1 raycast owner is missing {method}")

world_factory_26 = method_body(common26_source, r"\bprivate\s+synchronized\s+LuaTable\s+world\s*\(")
for binding in (
    "MLuaWorldEnvironmentApi26.bind(", "MLuaWorldBlockQueryApi26.bind(",
    "MLuaWorldRaycastApi26.bind(", "MLuaWorldBlockMutationApi26.bind(",
    "MLuaWorldEffectsApi26.bind(", "MLuaWorldDataApi26.bind(",
    "MLuaWorldEntityQueryApi26.bind(",
):
    if binding not in world_factory_26:
        fail(f"26.1 World instance factory is missing its API owner: {binding}")
for owner, methods in (
    (ENVIRONMENT_API_26, ("GetId", "GetDimension", "GetTime", "SetTime", "GetWeather", "SetWeather")),
    (BLOCK_MUTATION_API_26, ("SetBlock", "SetBlockState", "BreakBlock", "DestroyBlock")),
    (EFFECTS_API_26, ("PlaySound", "SpawnParticle", "SpawnEntity", "DropItem", "Explode", "SpawnLightning")),
    (DATA_API_26, ("GetPersistentData", "GetSyncedData", "GetData", "SetData", "RemoveData",
                   "ListGameRules", "GetGameRule", "GetGameRuleEntry", "SetGameRule")),
    (ENTITY_QUERY_API_26, ("GetPlayers", "GetEntities", "FindEntitiesInRadius", "FindPlayersInRadius",
                           "FindNearestPlayer", "FindNearestEntity", "FindEntitiesInBounds")),
    (RAYCAST_API_26, ("Raycast", "RaycastEntity", "RaycastAABB")),
):
    if not owner.is_file():
        fail(f"26.1 World API owner is missing: {owner}")
    owner_source = owner.read_text(encoding="utf-8")
    for method in methods:
        if f'"{method}"' not in owner_source:
            fail(f"{owner.name} does not own the World:{method} binding")

for path in (GAME_RULES_SHARED, GAME_RULES_26):
    source = path.read_text(encoding="utf-8")
    if "MLuaWorldGameRuleEntry.create(" not in source:
        fail(f"{path} does not delegate GameRuleEntry shape to the shared builder")
    if "__mlua_get_game_rule_value" in source or "__mlua_set_game_rule_value" in source:
        fail(f"{path} uses version-specific GameRuleEntry backing fields")

if not GAME_RULE_ENTRY.is_file():
    fail("shared GameRuleEntry builder is missing")
game_rule_entry_source = GAME_RULE_ENTRY.read_text(encoding="utf-8")
for backing_field in (
    'fields.set("__game_rule_name"',
    'fields.set("__game_rule_get_value"',
    'fields.set("__game_rule_set_value"',
):
    if backing_field not in game_rule_entry_source:
        fail(f"shared GameRuleEntry builder is missing its backing contract: {backing_field}")

# Chunk instances and World chunk operations have separate binding owners.
chunk_wrapper_source = CHUNK_WRAPPER.read_text(encoding="utf-8")
world_chunk_api_source = WORLD_CHUNK_API.read_text(encoding="utf-8")
legacy_chunk_binder = method_body(chunk_wrapper_source,
                                  r"\bpublic\s+static\s+void\s+bindWorldMethods\s*\(")
if "MLuaWorldChunkApi.bind(world, registration)" not in legacy_chunk_binder:
    fail("legacy chunk binder must delegate World chunk methods to MLuaWorldChunkApi")
for path in (SHARED, COMMON_26):
    source = path.read_text(encoding="utf-8")
    if "MLuaWorldChunkApi.bind(" not in source:
        fail(f"{path} does not delegate World chunk operations to MLuaWorldChunkApi")
for method in ("IsChunkLoaded", "IsBlockLoaded", "GetChunk", "GetChunkAt", "LoadChunk",
               "UnloadChunk", "SetChunkForced", "ForceLoadChunk", "IsChunkForced",
               "GetEntitiesInChunk"):
    binding = f'bindWorldMethod(world, registration, "{method}"'
    if binding not in world_chunk_api_source:
        fail(f"MLuaWorldChunkApi does not own World:{method}")
    if binding in chunk_wrapper_source:
        fail(f"World:{method} is still bound inline in MLuaChunkWrapper")
if 'bind(chunk, "GetBlockAt"' not in chunk_wrapper_source:
    fail("MLuaChunkWrapper no longer owns Chunk instance block access")
if "ReferenceQueue<LuaTable>" not in chunk_wrapper_source \
        or "byPosition.remove(collected.positionKey, collected)" not in chunk_wrapper_source:
    fail("chunk wrapper cache must reap collected instances through its reference queue")
if "chunks.entrySet().removeIf" in chunk_wrapper_source:
    fail("chunk wrapper cache must not scan every cached chunk during each lookup")

print(f"World service call contract passed ({len(pairs)} supported loader/version targets).")
