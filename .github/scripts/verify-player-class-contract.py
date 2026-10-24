#!/usr/bin/env python3
"""Keep player inventory and container binding ownership separate from the Player facade."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
PLAYER = ROOT / "src/main/java/dev/mlua/modules/players/classes/MLuaPlayerWrapper.java"
CLIENT_PLAYER = ROOT / "src/main/java/dev/mlua/modules/players/classes/MLuaClientPlayerWrapper.java"
LOCAL_PLAYER_BINDINGS = ROOT / "src/main/java/dev/mlua/modules/players/classes/MLuaLocalPlayerBindings.java"
INVENTORY = ROOT / "src/main/java/dev/mlua/modules/players/classes/MLuaPlayerInventoryBindings.java"
FOOD_BINDINGS = ROOT / "src/main/java/dev/mlua/modules/players/classes/MLuaPlayerFoodBindings.java"
PLAYER_RELATIONSHIPS = ROOT / "src/main/java/dev/mlua/modules/players/classes/MLuaPlayerRelationshipBindings.java"
ENTITY = ROOT / "src/main/java/dev/mlua/modules/players/classes/MLuaEntityWrapper.java"
ENTITY_FACTORY = ROOT / "src/main/java/dev/mlua/modules/players/classes/MLuaEntityInstanceFactory.java"
LIVING_ENTITY_BINDINGS = ROOT / "src/main/java/dev/mlua/modules/players/classes/MLuaLivingEntityBindings.java"
ENTITY_TRANSFORM_BINDINGS = ROOT / "src/main/java/dev/mlua/modules/players/classes/MLuaEntityTransformBindings.java"
ENTITY_STATE_BINDINGS = ROOT / "src/main/java/dev/mlua/modules/players/classes/MLuaEntityStateBindings.java"
ENTITY_PROPERTIES_BINDINGS = ROOT / "src/main/java/dev/mlua/modules/players/classes/MLuaEntityPropertiesBindings.java"
ENTITY_INSTANCE_CACHE = ROOT / "src/main/java/dev/mlua/modules/players/classes/MLuaEntityInstanceCache.java"
ENTITY_ACTION_BINDINGS = ROOT / "src/main/java/dev/mlua/modules/players/classes/MLuaEntityActionBindings.java"
PLAYERS_SERVICE = ROOT / "src/main/java/dev/mlua/modules/players/service/MLuaPlayersService.java"
INVENTORY_SERVICE = ROOT / "src/main/java/dev/mlua/modules/inventory/service/MLuaInventoryService.java"


def fail(message: str) -> None:
    print(f"Player class contract error: {message}", file=sys.stderr)
    raise SystemExit(1)


player_source = PLAYER.read_text(encoding="utf-8")
client_player_source = CLIENT_PLAYER.read_text(encoding="utf-8")
local_player_source = LOCAL_PLAYER_BINDINGS.read_text(encoding="utf-8")
client_bindings_source = client_player_source + local_player_source
players_service_source = PLAYERS_SERVICE.read_text(encoding="utf-8")
if "extends MLuaGameThreadReflectiveService" not in players_service_source:
    fail("client and server Players lookups must run on their owning game thread")
inventory_service_source = INVENTORY_SERVICE.read_text(encoding="utf-8")
if "extends MLuaGameThreadReflectiveService" not in inventory_service_source:
    fail("live inventory and container lookups must run on their owning game thread")
if "import net.minecraft.client." in player_source or "import net.minecraft.client.player.LocalPlayer" in player_source or "import net.minecraft.client.player.AbstractClientPlayer" in player_source:
    fail("shared Player wrapper must not resolve client-only Minecraft classes on dedicated servers")
if '"dev.mlua.modules.players.classes.MLuaClientPlayerWrapper"' not in player_source:
    fail("client Player bindings must be loaded only through the lazy client delegate")
if "AbstractClientPlayer" not in client_player_source or "LocalPlayer" not in local_player_source:
    fail("remote and local client Player bindings must have separate owners")
client_access_source = (ROOT / "src/main/java/dev/mlua/modules/players/MLuaClientPlayerAccess.java").read_text(encoding="utf-8")
if ("ClassValue<Member> PLAYER_ACCESSORS" not in client_access_source
        or "ClassValue<Member> LEVEL_ACCESSORS" not in client_access_source
        or "field.trySetAccessible()" not in client_access_source):
    fail("client player and level access must cache their version-tolerant field resolution independently")
if "method.trySetAccessible()" not in client_access_source or "getDeclaredField(fieldName)" not in client_access_source:
    fail("client player access must support non-public Minecraft mappings")
if "MLuaPlayerInventoryBindings.attach(table, player, true, true, registration)" not in player_source:
    fail("server Player instances must delegate inventory binding to its owner")
if player_source.count("MLuaPlayerInventoryBindings.attach(") != 1 or client_bindings_source.count("MLuaPlayerInventoryBindings.attach(") != 2:
    fail("server, remote-client, and local-client Player tables must share the inventory binding owner")
if "attachInventoryMethods(" in player_source:
    fail("Player wrapper still owns inventory and open-container binding")
food_source = FOOD_BINDINGS.read_text(encoding="utf-8")
if "MLuaPlayerFoodBindings.bind(table, player)" not in player_source:
    fail("Player wrapper must delegate food methods to their binding owner")
if "MLuaPlayerFoodBindings.bind(table, player)" not in client_bindings_source or "bindFoodMethods(table, player)" in client_bindings_source:
    fail("client Player wrapper must delegate food methods to the shared binding owner")
for method in ("GetFoodLevel", "SetFoodLevel", "GetSaturation", "SetSaturation", "CauseFoodExhaustion"):
    if f'"{method}"' not in food_source:
        fail(f"Player food binding owner is missing {method}")
relationship_source = PLAYER_RELATIONSHIPS.read_text(encoding="utf-8")
if "MLuaPlayerRelationshipBindings.bindNearbyPlayers(table, player, registration)" not in player_source:
    fail("Player wrapper must delegate nearby-player queries to their binding owner")
if '"GetNearbyPlayers"' not in relationship_source or "MLuaClassMethods.varArg(" not in relationship_source:
    fail("Player relationship binding owner must register GetNearbyPlayers through the shared callback adapter")
if (player_source + client_bindings_source).count("MLuaEntityWrapper.livingEntityTableBase(") != 3:
    fail("server, remote-client, and local-client Player wrappers must attach Player before live properties")
entity_source = ENTITY.read_text(encoding="utf-8")
entity_factory_source = ENTITY_FACTORY.read_text(encoding="utf-8")
if "if (entity instanceof Player player)" not in entity_factory_source:
    fail("living entity entry point must route Player instances through the Player wrapper")
living_entity_source = LIVING_ENTITY_BINDINGS.read_text(encoding="utf-8")
transform_source = ENTITY_TRANSFORM_BINDINGS.read_text(encoding="utf-8")
state_source = ENTITY_STATE_BINDINGS.read_text(encoding="utf-8")
properties_source = ENTITY_PROPERTIES_BINDINGS.read_text(encoding="utf-8")
cache_source = ENTITY_INSTANCE_CACHE.read_text(encoding="utf-8")
action_source = ENTITY_ACTION_BINDINGS.read_text(encoding="utf-8")
class_methods_source = (ROOT / "src/main/java/dev/mlua/core/classes/MLuaClassMethods.java").read_text(encoding="utf-8")
inventory_service_source = (ROOT / "src/main/java/dev/mlua/modules/inventory/service/MLuaInventoryService.java").read_text(encoding="utf-8")
inventory_bindings_source = INVENTORY.read_text(encoding="utf-8")
player_wrapper_source = (ROOT / "src/main/java/dev/mlua/modules/players/classes/MLuaPlayerWrapper.java").read_text(encoding="utf-8")
players_module_source = (ROOT / "src/main/java/dev/mlua/modules/players/PlayersModule.java").read_text(encoding="utf-8")
player_join_dispatcher_source = (ROOT / "src/main/java/dev/mlua/modules/players/MLuaPlayerJoinDispatcher.java").read_text(encoding="utf-8")
player_directory_source = (ROOT / "src/main/java/dev/mlua/modules/players/service/MLuaPlayerDirectory.java").read_text(encoding="utf-8")
if ('Class.forName("dev.mlua.modules.players.classes.MLuaClientPlayerWrapper"' not in player_directory_source
        or "wrapperType.getMethods()" not in player_directory_source):
    fail("player directory must resolve client instances through the client-only Player wrapper")
if "playerJoinDispatcher::enqueue" not in players_module_source or "playerJoinDispatcher::dispatchPending" not in players_module_source:
    fail("PlayersModule must delegate player join lifecycle work to its dedicated dispatcher")
if "pending.poll()" not in player_join_dispatcher_source or "this.dispatch(player)" not in player_join_dispatcher_source:
    fail("PlayerAdded listeners must be deferred until the post-login server tick")
if "MLuaPlayerJoinScope.run(player" not in player_join_dispatcher_source or \
        "PLAYER_ADDED.fireInlineOnSession(session, Side.SERVER, playerInstance)" not in player_join_dispatcher_source:
    fail("PlayerAdded must preserve joining-player scope and invoke listeners inline on the server owner thread")
if "CompletableFuture.runAsync" in player_join_dispatcher_source:
    fail("PlayerAdded must not hold the Lua execution lock from a worker during server events")
if "ownerRuntime.deliverPendingServerDiagnostics(player)" not in player_join_dispatcher_source or \
        "MLuaDataManager.syncInitialState(player)" not in player_join_dispatcher_source:
    fail("PlayerAdded must deliver diagnostics and initial synchronized state after listeners finish")
if "playerJoinDispatcher.clear()" not in players_module_source:
    fail("Queued PlayerAdded callbacks must be discarded when their module runtime reloads or unloads")
if "MLuaClientThread.isClientExecutionContext()" not in local_player_source:
    fail("local Player mutations must accept MLua's synchronous client coroutine context")
if "MLuaClientThread.callOnClientThread(" not in local_player_source:
    fail("local Player actions must execute on Minecraft's actual client thread")
if "withResumeWaitHook" not in (ROOT / "src/main/java/dev/mlua/core/MLuaCallbackBudget.java").read_text(encoding="utf-8"):
    fail("synchronous Lua callbacks must keep the Minecraft owner thread responsive while LuaJ runs")
join_scope_source = (ROOT / "src/main/java/dev/mlua/modules/players/MLuaPlayerJoinScope.java").read_text(encoding="utf-8")
if "InheritableThreadLocal<ServerPlayer>" not in join_scope_source:
    fail("Joining-player lookup scope must reach LuaJ's child coroutine thread")
if "MLuaPlayerJoinScope.current()" not in player_directory_source or "joining.getName().getString().equals(name)" not in player_directory_source:
    fail("player directory must resolve the joining player before the loader adds it to the live list")
if "public static LuaValue invokeInstanceMethod(" not in class_methods_source:
    fail("shared class method owner is missing live instance invocation")
for source in (inventory_service_source, inventory_bindings_source, player_wrapper_source):
    if "MLuaClassMethods.invokeInstanceMethod(" not in source:
        fail("inventory services and player bindings must use the shared class invocation convention")
    if "private static LuaValue invokeInstanceMethod(" in source or "static LuaValue invokeInstance(" in source:
        fail("inventory API duplicates live instance invocation instead of using MLuaClassMethods")
if "MLuaLivingEntityBindings.bind(table, entity, wrapped, registration)" not in entity_factory_source:
    fail("entity wrapper must delegate LivingEntity and Mob methods to their binding owner")
for method in ("GetHealth", "GiveEffect", "NavigateTo", "Damage", "GetAttributeInstance", "GetEquipment"):
    if f'"{method}"' not in living_entity_source:
        fail(f"LivingEntity binding owner is missing {method}")
    if f'"{method}"' in entity_source:
        fail(f"Entity wrapper still owns LivingEntity method {method}")
if entity_factory_source.count("MLuaEntityTransformBindings.bind(table, wrapped)") != 2:
    fail("base and living entity instances must share one transform binding owner")
for method in ("GetPosition", "SetPosition", "GetVelocity", "SetVelocity", "GetRotation", "SetCFrame", "LookAt"):
    if f'"{method}"' not in transform_source:
        fail(f"entity transform binding owner is missing {method}")
    if f'"{method}"' in entity_source:
        fail(f"Entity wrapper still owns transform method {method}")
for implementation in ("static LuaValue setVelocity(", "static MCVec3 lookAngle(", "static void applyCFrame(", "static void applyRotation("):
    if implementation not in transform_source:
        fail(f"entity transform component is missing operation {implementation}")
for facade in ("MLuaEntityTransformBindings.setVelocity(entity, value)",
               "MLuaEntityTransformBindings.applyCFrame(entity, value)",
               "MLuaEntityTransformBindings.applyRotation(entity, value)"):
    if facade not in entity_source:
        fail(f"entity transform compatibility facade is missing {facade}")
for method in ("GetIsAlive", "GetIsMoving", "GetIsJumping", "GetNbt", "GetSyncedData"):
    if f'"{method}"' not in state_source:
        fail(f"entity state binding owner is missing {method}")
    if f'"{method}"' in entity_source:
        fail(f"Entity wrapper still owns state method {method}")
if "MLuaEntityPropertiesBindings.attach(table)" not in entity_source:
    fail("entity property projection must be owned by MLuaEntityPropertiesBindings")
for method in ("GetNearbyEntities", "GetPassengers", "StartRiding", "GetBoundingBox", "AddTag"):
    if f'"{method}"' not in properties_source:
        fail(f"entity property binding owner is missing {method}")
    if f'"{method}"' in entity_source:
        fail(f"Entity wrapper still owns entity property method {method}")
for method in ("clearCache", "removeFromCache", "cleanCache", "cachedPlayerTable"):
    if f"MLuaEntityInstanceCache.{method}(" not in entity_source:
        fail(f"entity wrapper facade must delegate {method} to the cache owner")
if "WeakHashMap<>()" not in cache_source or "MLuaEntityComponentManager.clearEntity(scope)" not in cache_source:
    fail("entity identity cache owner must retain weak runtime ownership and entity teardown")
if entity_factory_source.count("MLuaEntityActionBindings.bind(table, wrapped)") != 2:
    fail("base and living entity instances must share one action binding owner")
for method in ("Attack", "Kill", "Remove", "DistanceTo"):
    if f'"{method}"' not in action_source:
        fail(f"entity action binding owner is missing {method}")
    if f'"{method}"' in entity_source:
        fail(f"Entity wrapper still owns action method {method}")
if "MLuaClassMethods.zeroArg(" not in action_source or "MLuaClassMethods.varArg(" not in action_source:
    fail("entity method binders must use the shared Lua callback adapters")
if "public static LuaValue zeroArg(" not in class_methods_source or "public static LuaValue varArg(" not in class_methods_source:
    fail("shared class callback adapters must be owned by MLuaClassMethods")
for binding_source in (living_entity_source, transform_source, state_source, action_source):
    if "MLuaClassMethods.zeroArg(" not in binding_source or "MLuaClassMethods.varArg(" not in binding_source:
        fail("entity binding owners must use the shared Lua callback adapters")
for method in ("GetInventory", "GetSlot", "GetHotbar", "GetArmorContents", "GetOffhandSlot",
               "GetEnderChest", "GetOpenContainer", "GetOpenContainerTitle", "GetOpenContainerSize"):
    if f'"{method}"' not in INVENTORY.read_text(encoding="utf-8"):
        fail(f"player inventory binding owner is missing {method}")
players_module = (ROOT / "src/main/java/dev/mlua/modules/players/PlayersModule.java").read_text(encoding="utf-8")
entity_registration = (ROOT / "src/main/java/dev/mlua/modules/players/MLuaEntityClassRegistration.java").read_text(encoding="utf-8")
player_registration = (ROOT / "src/main/java/dev/mlua/modules/players/MLuaPlayerClassRegistration.java").read_text(encoding="utf-8")
entity_bounds_registration = (ROOT / "src/main/java/dev/mlua/modules/players/MLuaEntityBoundsClassRegistration.java").read_text(encoding="utf-8")
if "MLuaEntityClassRegistration.register(registration)" not in players_module or "MLuaPlayerClassRegistration.register(registration)" not in players_module:
    fail("PlayersModule must delegate class definitions to their registration owners")
if "registration.registerClass(\"Entity\"" not in entity_registration or "registration.registerClass(\"Mob\"" not in entity_registration:
    fail("entity class hierarchy must be owned by MLuaEntityClassRegistration")
if "registration.registerClass(\"Player\"" not in player_registration:
    fail("Player class contract must be owned by MLuaPlayerClassRegistration")
if "MLuaEntityBoundsClassRegistration.register(registration)" not in entity_registration or "registration.registerClass(\"EntityBounds\"" not in entity_bounds_registration:
    fail("EntityBounds value construction and spatial methods must have a dedicated class owner")
client_player_controls = ("Jump", "SetSprinting", "Turn", "SwingArm", "AttackEntity", "UseItem")
for method in client_player_controls:
    if f'registerInstanceMethod(registration, "Player", "{method}"' not in player_registration:
        fail(f"client Player control {method} must be registered before runtime instances bind it")
if "new WeakHashMap<>()" not in INVENTORY.read_text(encoding="utf-8"):
    fail("open-container instance ownership must remain weakly keyed by native menu identity")
print("Player and entity class contract passed (inventory, behavior, transform, state, and property bindings have dedicated owners).")
