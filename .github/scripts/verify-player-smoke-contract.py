#!/usr/bin/env python3
"""Catch drift between generated Lua player smoke markers and the Windows runner."""

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[2]
prepare = ROOT / ".github/scripts/prepare-packaged-smoke.ps1"
runner = ROOT / ".github/scripts/run-player-smoke.ps1"
workflow = ROOT / ".github/workflows/compile-java.yml"
loader_projects = {
    "fabric": ROOT / "client-smoke/fabric/build.gradle",
    "forge": ROOT / "client-smoke/forge/build.gradle",
    "neoforge": ROOT / "client-smoke/neoforge/build.gradle",
}


def fail(message: str) -> None:
    print(f"Player smoke contract error: {message}", file=sys.stderr)
    raise SystemExit(1)


def versioned_source(source: str, target: str) -> str:
    output = []
    active = True
    for line in source.splitlines():
        marker = line.strip()
        if marker.startswith("//--version "):
            active = target in {value.strip() for value in marker[len("//--version "):].split(",")}
        elif marker == "//--endversion":
            active = True
        elif active:
            output.append(line)
    return "\n".join(output)


module_sources = ROOT / "src/main/java/dev/mlua/modules"


def discover_service_sources() -> dict[str, tuple[Path, Path]]:
    """Resolve service APIs from their owning module's service factory."""
    discovered: dict[str, tuple[Path, Path]] = {}
    factory = re.compile(
        r'ServiceEntry\.builder\(\s*"([^"\n]+)"\s*,\s*([A-Za-z_$][\w$]*)::new'
    )
    for module_path in module_sources.rglob("*Module.java"):
        module_text = module_path.read_text(encoding="utf-8")
        for service_name, implementation_name in factory.findall(module_text):
            matches = list(module_sources.rglob(f"{implementation_name}.java"))
            if len(matches) != 1:
                fail(f"service {service_name} resolves to {len(matches)} files for {implementation_name}")
            mixin_service_outside_transform_package = (
                service_name == "Mixin"
                and matches[0].parent == module_path.parent / "api"
                and "package dev.mlua.modules.mixin.api;" in matches[0].read_text(encoding="utf-8")
            )
            if matches[0].parent.name != "service" and not mixin_service_outside_transform_package:
                fail(f"service {service_name} implementation must live in its module's service package: {matches[0]}")
            if service_name in discovered:
                fail(f"service name {service_name} is registered by multiple modules")
            discovered[service_name] = (matches[0], module_path)
    return discovered


service_api_sources = discover_service_sources()


prepare_text = prepare.read_text(encoding="utf-8")
runner_text = runner.read_text(encoding="utf-8")
workflow_text = workflow.read_text(encoding="utf-8")
gui_service_text = (ROOT / "src/main/java/dev/mlua/modules/coregui/service/MLuaGuiService.java").read_text(encoding="utf-8")
native_widget_activation_text = (ROOT / "src/main/java/dev/mlua/modules/coregui/service/MLuaNativeWidgetActivation.java").read_text(encoding="utf-8")
gui_api_text = "\n".join(
    (ROOT / f"src/main/java/dev/mlua/modules/coregui/service/{name}.java").read_text(encoding="utf-8")
    for name in ("MLuaGuiApi", "MLuaGuiHudApi", "MLuaGuiScreenApi", "MLuaGuiNativeControlApi")
)
gui_screen_manager_text = (ROOT / "src/main/java/dev/mlua/modules/coregui/service/MLuaNativeScreenManager.java").read_text(encoding="utf-8")
gui_module_text = (ROOT / "src/main/java/dev/mlua/modules/coregui/GuiModule.java").read_text(encoding="utf-8")
runtime_marker_source = prepare_text + "\n" + gui_service_text
runtime_marker_source += "\n" + (ROOT / "src/main/java/dev/mlua/modules/coregui/service/MLuaGuiChatLineBridge.java").read_text(encoding="utf-8")
runtime_marker_source += "\n" + (ROOT / "src/main/java/dev/mlua/runtime/MLuaBootstrap.java").read_text(encoding="utf-8")
if "MLuaNativeWidgetActivation.activate(button, mouseX, mouseY)" not in gui_service_text:
    fail("Gui service must preserve its native replacement activation entry point")
if "ClassValue<Activation>" not in native_widget_activation_text \
        or 'declaredMethod(current, "onPress")' not in native_widget_activation_text \
        or 'declaredMethod(current, "onClick", double.class, double.class)' not in native_widget_activation_text:
    fail("native button actions must resolve each widget class once and preserve both activation signatures")
for project in loader_projects.values():
    for source_path in project.parent.glob("src/**/*.java"):
        runtime_marker_source += "\n" + source_path.read_text(encoding="utf-8")
render_service_text = (ROOT / "src/main/java/dev/mlua/modules/render/service/MLuaRenderService.java").read_text(encoding="utf-8")
render_object_operations_text = (ROOT / "src/main/java/dev/mlua/modules/render/service/MLuaRenderObjectOperations.java").read_text(encoding="utf-8")
render_manager_text = (ROOT / "src/main/java/dev/mlua/runtime/client/MLuaClientRenderManager.java").read_text(encoding="utf-8")
render_manager_text += "\n" + (ROOT / "src/main/java/dev/mlua/runtime/client/MLuaWorldRenderPipeline.java").read_text(encoding="utf-8")
process_helper_text = (ROOT / ".github/scripts/smoke-process.ps1").read_text(encoding="utf-8")
client_player_wrapper_text = (ROOT / "src/main/java/dev/mlua/modules/players/classes/MLuaClientPlayerWrapper.java").read_text(encoding="utf-8")
client_player_wrapper_text += "\n" + (ROOT / "src/main/java/dev/mlua/modules/players/classes/MLuaLocalPlayerBindings.java").read_text(encoding="utf-8")
item_wrapper_text = (ROOT / "src/main/java/dev/mlua/minecraft/binding/MLuaItemWrapper.java").read_text(encoding="utf-8")
item_class_text = (ROOT / "src/main/java/dev/mlua/minecraft/binding/MLuaItemStackClass.java").read_text(encoding="utf-8")
input_module_text = (ROOT / "src/main/java/dev/mlua/modules/input/InputModule.java").read_text(encoding="utf-8")
keyboard_input_mixin = (ROOT / "src/main/java/dev/mlua/modules/mixin/service/KeyboardInputMixin.java").read_text(encoding="utf-8")

for loader, path in loader_projects.items():
    config = path.read_text(encoding="utf-8")
    has_test_window = "'--width', '320', '--height', '240'" in config
    if "System.getenv('MLUA_CI_PLAYER_TEST') == 'true'" not in config \
            or (loader != "neoforge" and not has_test_window):
        fail(f"{loader} player simulation must use a reduced client window for software rendering")
    if loader == "neoforge" and all(token not in config for token in ("'--width'", "'--height'")):
        fail("neoforge player simulation must use a reduced client window for software rendering")
if "'clouds:off'" not in runner_text:
    fail("software-rendered player simulation must disable the expensive cloud pass")
for setting in ("'level-type=minecraft:flat'", "'generate-structures=false'", "'difficulty=peaceful'"):
    if setting not in runner_text:
        fail(f"software-rendered player simulation must apply {setting} to its isolated server")

if "return MLuaItemStackClass.register(registration);" not in item_wrapper_text:
    fail("Item wrapper must delegate class API ownership to MLuaItemStackClass")
for api in ('registration.registerClass("Item"', '"new"', '"GetId"', '"GetCount"',
            '"SetCount"', '"SetLore"', '"SetTag"', '"GetEnchantments"', '"BlockItem"', '"Weapon"'):
    if api not in item_class_text:
        fail(f"Item class registrar is missing {api}")
for helper in ("itemTable", "maxDamage", "remainingDurability", "damagePercent", "toItemStack",
               "damage", "setDamage", "cloneItem"):
    if f"MLuaItemWrapper.{helper}(" not in item_class_text:
        fail(f"Item class registrar must delegate {helper} to the item wrapper")
block_entity_class = (ROOT / "src/main/java/dev/mlua/minecraft/binding/MLuaBlockEntityClass.java").read_text(encoding="utf-8")
if "import dev.mlua.module.MLuaRegistration;" not in block_entity_class:
    fail("BlockEntity class registrar must import its registration API from the module package")

starts = len(re.findall(r"(?m)^(?:.*=\s*)?@'\s*$", prepare_text))
ends = len(re.findall(r"(?m)^'@(?:\s*\|.*)?$", prepare_text))
if starts != ends:
    fail(f"unbalanced PowerShell here-strings in prepare script ({starts} starts, {ends} ends)")

for path in ("ci_player.client.lua", "ci_player.server.lua"):
    if f'player-test-scripts/{path}' not in prepare_text:
        fail(f"prepare script does not generate {path}")

markers = sorted(set(re.findall(r"MLUA_CI_(?:PLAYER|SCRIPTED_ENTITY)_[A-Z0-9_]+", runner_text)))
missing = [marker for marker in markers if marker not in runtime_marker_source]
if missing:
    fail("runner waits for markers not emitted by generated scripts: " + ", ".join(missing))
for marker in ("MLUA_PACKAGED_CLIENT_BOOT_OK", "MLUA_PACKAGED_SERVER_BOOT_OK"):
    if marker not in runner_text or marker not in runtime_marker_source:
        fail(f"player simulation must verify packaged {marker.removeprefix('MLUA_PACKAGED_').removesuffix('_BOOT_OK').lower()} boot")

client_runtime_markers = (
    "MLUA_RUNTIME_BOOTSTRAP_OK",
    "MLUA_CI_CLIENT_SERVICE_SCRIPT_OK",
    "MLUA_CI_GENERATED_ASSET_HANDLES_OK",
    "MLUA_CI_SCRIPTED_CONTAINER_REGISTRATION_OK",
    "MLUA_CI_REGISTRY_PROPERTIES_OK",
    "MLUA_CI_DYNAMIC_REGISTRY_ENTRY_OK",
    "MLUA_CI_ORE_VEIN_REGISTRATION_OK",
    "MLUA_CI_TOAST_API_OK",
    "MLUA_CI_MODEL_TRANSFORM_OK",
    "MLUA_CI_TASK_LIMITS_OK",
    "MLUA_CI_NESTED_TASK_SCHEDULE_OK",
    "MLUA_CI_PARTICLE_EMITTER_API_OK",
    "MLUA_CI_PLAYER_HEARTBEAT_FIRED",
    "MLUA_CI_PLAYER_CHAT_LINE_HOOK_REGISTERED",
    "MLUA_CI_CHAT_LINE_BRIDGE_ENTERED",
    "MLUA_CI_PLAYER_CHAT_LINE_HOOK_FIRED",
    "MLUA_CI_INPUT_KEYBINDS_OK",
    "MLUA_CI_BBMODEL_HIERARCHY_OK",
    "MLUA_CI_ANIMATION_API_OK",
    "MLUA_CI_NATIVE_SCREEN_STATE_OK",
    "MLUA_CI_NATIVE_SCREEN_TICK_OK",
    "MLUA_CI_NATIVE_SCREEN_CONTROLS_OK",
    "MLUA_CI_NATIVE_SCREEN_BUTTON_CALLBACK_OK",
    "MLUA_CI_NATIVE_SCREEN_EDITBOX_CHANGE_OK",
    "MLUA_CI_CLOSED_SCREEN_CONTROLS_OK",
    "MLUA_CI_SERVICE_PARITY_OK",
)
native_screen_diagnostics = (
    "MLUA_CI_NATIVE_SCREEN_INIT_CALLBACK_OK",
    "MLUA_CI_NATIVE_SCREEN_TICK_CALLBACK_REGISTERED",
    "MLUA_CI_NATIVE_SCREEN_TICK_CALLBACK_ENTERED",
)
missing_diagnostics = [marker for marker in native_screen_diagnostics if marker not in prepare_text]
if missing_diagnostics:
    fail("native screen smoke does not report lifecycle checkpoints: " + ", ".join(missing_diagnostics))
required_runtime_block = re.search(
    r"(?s)\$requiredClientRuntimeMarkers\s*=\s*@\((.*?)\n\)", runner_text
)
if required_runtime_block is None:
    fail("player runner does not define required runtime checks for its isolated client")
required_runtime_markers = set(re.findall(r"'([^']+)'", required_runtime_block.group(1)))
required_server_runtime_block = re.search(
    r"(?s)\$requiredServerRuntimeMarkers\s*=\s*@\((.*?)\n\)", runner_text
)
if required_server_runtime_block is None:
    fail("player runner does not define required runtime checks for its isolated server")
required_server_runtime_markers = set(re.findall(r"'([^']+)'", required_server_runtime_block.group(1)))
missing_runtime_markers = [marker for marker in client_runtime_markers if marker not in required_runtime_markers]
if missing_runtime_markers:
    fail("isolated player client does not require these service/UI markers: "
         + ", ".join(missing_runtime_markers))
unemitted_runtime_markers = [marker for marker in client_runtime_markers if marker not in runtime_marker_source]
if unemitted_runtime_markers:
    fail("isolated player client waits for markers not emitted by its generated script: "
         + ", ".join(unemitted_runtime_markers))
server_runtime_markers = (
    "MLUA_RUNTIME_BOOTSTRAP_OK",
    "MLUA_SCRIPT_MIXIN_HOOK_OK",
    "MLUA_EMPTY_STACK_ISOLATION_OK",
    "MLUA_CI_SERVER_SERVICE_SCRIPT_OK",
    "MLUA_CI_ITEM_PROPERTIES_APPLIED_OK",
    "MLUA_CI_TIERED_TOOL_CREATED_OK",
    "MLUA_CI_NATIVE_BOW_CREATED_OK",
    "MLUA_CI_SCRIPTED_BOW_CREATED_OK",
    "MLUA_CI_ITEM_COPY_CREATED_OK",
    "MLUA_CI_ITEM_PICKUP_EVENT_OK",
    "MLUA_CI_SCOREBOARD_CLASSES_OK",
    "MLUA_CI_GENERATED_ASSET_HANDLES_OK",
    "MLUA_CI_SCRIPTED_CONTAINER_REGISTRATION_OK",
    "MLUA_CI_REGISTRY_PROPERTIES_OK",
    "MLUA_CI_COOKING_RESULT_COUNT_OK",
    "MLUA_CI_DYNAMIC_REGISTRY_ENTRY_OK",
    "MLUA_CI_ORE_VEIN_REGISTRATION_OK",
)
missing_server_markers = [marker for marker in server_runtime_markers if marker not in required_server_runtime_markers]
if missing_server_markers:
    fail("isolated player server does not require these service/registry markers: "
         + ", ".join(missing_server_markers))
unemitted_server_markers = [marker for marker in server_runtime_markers if marker not in runtime_marker_source]
if unemitted_server_markers:
    fail("isolated player server waits for markers not emitted by its generated scripts: "
         + ", ".join(unemitted_server_markers))
if "$missingClientRuntimeMarkers.Count -eq 0" not in runner_text:
    fail("isolated client service/UI runtime markers do not gate player simulation success")
if not re.search(r"(?s)local containerInteractionFinished = clientContainerClosed\s+or \(containerUseAttempts >= 3 and containerUseState == 0\).*?if playerSeen and containerInteractionFinished and inputPressState == 0 then.*?MLUA_CI_PLAYER_INPUT_PRESS_BEGIN", prepare_text):
    fail("input simulation must start after local-player readiness and completion of container interaction attempts")
if 'print("MLUA_CI_PLAYER_MOVEMENT_DELTA:"' not in prepare_text:
    fail("player movement failures must log the observed X/Z displacement")
if 'Gui:CloseCurrentScreen()' not in prepare_text \
        or "containerInteractionFinished and inputPressState == 0" not in prepare_text \
        or 'MLUA_CI_PLAYER_CONTAINER_CLOSED' not in runner_text:
    fail("player movement simulation must wait for the opened vanilla container screen to close or interaction retries to finish")
if 'table.set("CloseCurrentScreen"' not in gui_api_text \
        or "closeCurrentScreen()" not in gui_screen_manager_text \
        or 'Registration.method("Gui", "Gui:CloseCurrentScreen()"' not in gui_module_text:
    fail("Gui screen closure must be implemented and documented on the public service")
if "expectedScreen instanceof Screen screen) screen.onClose()" not in gui_screen_manager_text:
    fail("native screen closure must invoke the screen lifecycle so container screens send their close packet")
if "MLuaClientThread.runOnClientThread(operation::get)" not in gui_screen_manager_text \
        or "minecraft.screen != expectedScreen" not in gui_screen_manager_text:
    fail("Gui screen closure must inspect and change the active screen on Minecraft's client thread")
if re.search(r"(?s)if playerPickupReceived and inputPressState == 0 then", prepare_text):
    fail("input simulation is still blocked on the independent item-pickup event")
if '@Inject(method = "tick", at = @At("HEAD"))' not in keyboard_input_mixin \
        or "MLuaKeybindController.refreshSimulatedPresses();" not in keyboard_input_mixin:
    fail("simulated mappings must be refreshed immediately before Minecraft samples keyboard movement input")
if "//--version 1.20.1,1.21.1" not in keyboard_input_mixin \
        or "boolean slowDown, float slowDownFactor" not in keyboard_input_mixin \
        or "//--version 26.1" not in keyboard_input_mixin:
    fail("keyboard input injection signatures must follow the mapped tick signature for each supported version family")
for version, signature in (
    ("1.20.1", "boolean slowDown, float slowDownFactor"),
    ("1.21.1", "boolean slowDown, float slowDownFactor"),
    ("26.1", "CallbackInfo callback"),
):
    selected = versioned_source(keyboard_input_mixin, version)
    if signature not in selected or selected.count("MLuaKeybindController.refreshSimulatedPresses();") != 1:
        fail(f"{version} keyboard input hook does not match its selected signature")
if "onClientTickStart(dev.mlua.minecraft.client.MLuaKeybindController::refreshSimulatedPresses)" in input_module_text:
    fail("simulated key refresh must not rely on a loader tick event that runs before key-state polling")
for relative in (
    "src/main/resources/mlua.mixins.json",
    "src/main/resources/mlua.forge.mixins.json",
    "mlua-loaders/fabric/1.20.1/project/src/main/resources/mlua.fabric.mixins.json",
    "mlua-loaders/fabric/1.21.1/project/src/main/resources/mlua.fabric.mixins.json",
    "mlua-loaders/fabric/26.1/project/src/main/resources/mlua.fabric.mixins.json",
):
    if '"KeyboardInputMixin"' not in (ROOT / relative).read_text(encoding="utf-8"):
        fail(f"{relative} does not install the simulated movement input hook")
if "'HumanoidModelMixin', 'KeyboardInputMixin', 'MouseHandlerMixin'" not in (
    ROOT / "gradle/mlua-neoforge-project.gradle"
).read_text(encoding="utf-8"):
    fail("NeoForge service mixin generation does not install the simulated movement input hook")

if 'print("MLUA_CI_COOKING_RESULT_COUNT_OK")' not in prepare_text:
    fail("generated registry smoke does not exercise cooking recipe result counts")
if 'for _, contract in ipairs(serviceContracts) do' not in prepare_text \
        or 'type(service[methodName]) == "function"' not in prepare_text:
    fail("client service parity marker is not backed by a required service-method contract")
service_contract_block = re.search(
    r"(?s)local serviceContracts\s*=\s*\{(.*?)\n\s*\}\s*\nfor _, contract",
    prepare_text,
)
if service_contract_block is None:
    fail("client smoke service-method contract cannot be parsed")
declared_contracts = re.findall(
    r'\{\s*"([^"\n]+)"\s*,\s*\{([^}]*)\}\s*\}', service_contract_block.group(1)
)
if not declared_contracts:
    fail("client smoke service-method contract is empty")
for service_name, method_list in declared_contracts:
    source_paths = service_api_sources.get(service_name)
    if source_paths is None:
        fail(f"client smoke contract names an unmapped service: {service_name}")
    missing_sources = [str(path.relative_to(ROOT)) for path in source_paths if not path.is_file()]
    if missing_sources:
        fail(f"source files for smoke service {service_name} are missing: "
             + ", ".join(missing_sources))
    source_text = "\n".join(path.read_text(encoding="utf-8") for path in source_paths)
    method_names = re.findall(r'"([A-Za-z][A-Za-z0-9_]*)"', method_list)
    if not method_names:
        fail(f"client smoke contract for {service_name} has no methods")
    for method_name in method_names:
        if not re.search(rf"\b{re.escape(method_name)}\s*\(", source_text) \
                and f'"{method_name}"' not in source_text:
            fail(f"client smoke contract requires missing {service_name}.{method_name}")
if 'colonLimits.Maximum == dotLimits.Maximum' not in prepare_text \
        or prepare_text.find('print("MLUA_CI_SERVICE_PARITY_OK")') < prepare_text.find('colonLimits.Maximum == dotLimits.Maximum'):
    fail("client service parity marker is not gated on matching colon/dot service results")
if 'MLUA_CI_COOKING_RESULT_COUNT_OK' not in required_server_runtime_markers:
    fail("dedicated-server smoke does not require the cooking recipe result-count check")
if 'MLUA_CI_ORE_VEIN_REGISTRATION_OK' not in prepare_text \
        or 'MLUA_CI_ORE_VEIN_REGISTRATION_OK' not in required_runtime_markers \
        or 'MLUA_CI_ORE_VEIN_REGISTRATION_OK' not in required_server_runtime_markers:
    fail("packaged smoke does not require ore-vein registration on both client and server targets")
if '__MLUA_TARGET__' not in prepare_text or '$env:MLUA_TARGET' not in prepare_text:
    fail("cooking recipe smoke does not select the format expected by the target version")
if 'print("MLUA_CI_MODEL_TRANSFORM_OK")' not in prepare_text \
        or 'MLUA_CI_MODEL_TRANSFORM_OK' not in required_runtime_markers:
    fail("packaged client smoke does not require the model-transform CFrame contract")
if 'MLUA_CI_PLAYER_MODEL_TRANSFORM_DRAW_OK' not in runner_text:
    fail("player simulation does not wait for a transformed Mesh draw submission")
if 'GetModelTransform' not in render_object_operations_text or 'SetModelTransform' not in render_object_operations_text:
    fail("RenderObject does not expose the script-facing ModelTransform property methods")
if render_manager_text.count("applyTransform(pose, object.modelTransform)") < 3:
    fail("model transforms are not applied by world, entity, and held-item render targets")
if render_manager_text.count("applyTransform(matrix, modelTransform)") < 4:
    fail("deferred geometry does not apply snapshotted model transforms across its render paths")

# Every assertion observed by the polling loop must gate success. This catches
# cases where a marker remains useful in logs but no longer blocks the job.
complete_match = re.search(r"(?s)\$checksComplete\s*=\s*\((.*?)\n\s*\)", runner_text)
if complete_match is None:
    fail("player runner has no parseable aggregate completion condition")
completion = complete_match.group(1)
marker_flags = re.findall(
    r"(?m)^\s*\$(\w+)\s*=\s*\$(?:client|server)Log\.Contains\('MLUA_CI_(?:PLAYER|SCRIPTED_ENTITY)_[A-Z0-9_]+'\)",
    runner_text,
)
omitted_checks = [name for name in marker_flags if not re.search(rf"\${re.escape(name)}\b", completion)]
if omitted_checks:
    fail("player runner does not wait for these gameplay checks: " + ", ".join(omitted_checks))

for marker in (
    "MLUA_CI_PLAYER_SERVER_LISTENER_READY",
    "MLUA_CI_PLAYER_SERVER_JOINED:",
    "MLUA_CI_PLAYER_CLIENT_JOINED:",
    "MLUA_CI_PLAYER_WORLD_DOT_CALL_OK",
    "MLUA_CI_PLAYER_ATTACK_CONFIRMED",
    "MLUA_CI_PLAYER_PICKUP_COUNT_OK",
    "MLUA_CI_PLAYER_BLOCK_ENTITY_ON_LOAD_INSTANCE_OK",
    "MLUA_CI_PLAYER_CONTAINER_OPENED",
    "MLUA_CI_PLAYER_CLIENT_CONTAINER_OPENED",
    "MLUA_CI_PLAYER_MOVEMENT_OK",
):
    if marker not in runner_text:
        fail(f"runner does not require the expected gameplay check {marker}")

if "set MLUA_CI_PLAYER_TEST=true" not in runner_text:
    fail("isolated player-test client and server are not marked to stay alive for gameplay")
if "'runServer'" not in runner_text or "'runClient'" not in runner_text:
    fail("runner must start a dedicated server and a Minecraft client")
smoke_clients = (
    ROOT / "client-smoke/fabric/src/main/java/dev/mlua/ci/SmokeClient.java",
    ROOT / "client-smoke/forge/src/smoke/java/dev/mlua/ci/SmokeClient.java",
    ROOT / "client-smoke/neoforge/src/main/java/dev/mlua/ci/SmokeClient.java",
)
for path in smoke_clients:
    source = path.read_text(encoding="utf-8")
    if "MLUA_CI_PLAYER_TEST" not in source:
        fail(f"{path.relative_to(ROOT)} stops the client during player simulation")
process_helper_source = ". (Join-Path $PSScriptRoot 'smoke-process.ps1')"
if process_helper_source not in runner_text or process_helper_source not in (
    ROOT / ".github/scripts/run-minecraft-smoke.ps1"
).read_text(encoding="utf-8"):
    fail("Minecraft and player runners must share the bounded process-tree cleanup helper")
if "taskkill.exe" not in process_helper_text or "WaitForExit(15000)" not in process_helper_text:
    fail("smoke process-tree cleanup must terminate Gradle children without an unbounded wait")
sound_manager_sources = [
    path for path in ROOT.rglob("MLuaClientSoundManager.java")
    if "build" not in path.parts and ".git" not in path.parts
]
if not sound_manager_sources:
    fail("no checked-in client sound manager source was found")
for path in sound_manager_sources:
    if "activeIds(MLuaSoundOwnershipRegistry.Owner owner)" not in path.read_text(encoding="utf-8"):
        fail(f"client sound manager override is missing owner-scoped active-id enumeration: {path.relative_to(ROOT)}")
if "Copy-Item (Join-Path $projectDir 'build.gradle') $sideDir -Force" not in runner_text:
    fail("client and server do not get isolated Gradle project copies")
for copied_input in (
    "Copy-Item (Join-Path $projectDir 'settings.gradle') $sideDir -Force",
    "Copy-Item (Join-Path $projectDir 'compile-libs/*.jar')",
    "Copy-Item (Join-Path $projectDir '../common/*')",
    "$configSource = Join-Path $runRoot 'config'",
    "Copy-Item (Join-Path $configSource '*') $configTarget -Recurse -Force",
):
    if copied_input not in runner_text:
        fail(f"isolated player project is missing required input copy: {copied_input}")
if "@('-p', \"`\"$SideDir`\"\")" not in runner_text:
    fail("player test Gradle processes do not run against their separate project directories")
if "run/player-*/run/logs/latest.log" not in workflow_text:
    fail("player smoke artifacts do not include logs from the isolated game directories")
if "run/player-*/run/crash-reports/*.txt" not in workflow_text:
    fail("player smoke artifacts do not include crash reports from the isolated game directories")
if "Upload simulated player logs" not in workflow_text:
    fail("workflow does not preserve player simulation logs for debugging")
if "Boot packaged client and server, then simulate player gameplay" not in workflow_text:
    fail("player simulation must be the packaged client/server boot check")
for operation in (
    "WorldService.Get(dimension)",
    "player:SendChatMessage(",
    "entity:Attack()",
    'Input:SetKeyDown("key.forward", true)',
    'Input:SetMouseButtonDown(1, true)',
    "Input:SetMouseButtonDown(2, true)",
    "player:SetRotation(pitch, yaw)",
    "Render:CreateMesh({",
    "context:DrawObject(worldTransformMesh, CFrame.new(0, 1, 0))",
    "player:GetOpenContainerSize() > 46",
    "if containerTarget and not clientContainerOpened then",
    "Events.EntityHurt:Connect",
    "Events.ItemPickup:Connect",
    "Events.ContainerOpen:Connect",
    'event.Player:GetUUID() == ciTestPlayer:GetUUID()',
):
    if operation not in prepare_text:
        fail(f"player simulation is missing real gameplay operation {operation}")
if "inputPressState >= 5 and renderSimulationReady" in prepare_text:
    fail("native container interaction must not wait for unrelated input or render callbacks")
if "CHAT_SUBMISSION_METHOD.get(connection.getClass())" not in client_player_wrapper_text:
    fail("local chat submission must resolve the live loader-specific connection type")
if 'Minecraft.class.getMethod("getConnection")' in client_player_wrapper_text:
    fail("local chat submission must not reflect using an unmapped Minecraft getter name")
for loader, project in loader_projects.items():
    project_text = project.read_text(encoding="utf-8")
    client_source = {
        "fabric": ROOT / "client-smoke/fabric/src/main/java/dev/mlua/ci/SmokeClient.java",
        "forge": ROOT / "client-smoke/forge/src/smoke/java/dev/mlua/ci/SmokeClient.java",
        "neoforge": ROOT / "client-smoke/neoforge/src/main/java/dev/mlua/ci/SmokeClient.java",
    }[loader].read_text(encoding="utf-8")
    if ("../common/src/main/java" not in project_text
            or "PlayerTestServerConnector.connectIfRequested" not in client_source
            or "if (booted) return;" not in client_source
            or "booted = true;" not in client_source):
        fail(f"{loader} smoke client does not connect through the native multiplayer screen")

shared_smoke_target = ROOT / "client-smoke/common/src/main/java/dev/mlua/ci/SmokeTarget.java"
if not shared_smoke_target.is_file():
    fail("shared smoke target source is missing")
for duplicate in (
    ROOT / "client-smoke/fabric/src/main/java/dev/mlua/ci/SmokeTarget.java",
    ROOT / "client-smoke/forge/src/smoke/java/dev/mlua/ci/SmokeTarget.java",
    ROOT / "client-smoke/neoforge/src/main/java/dev/mlua/ci/SmokeTarget.java",
):
    if duplicate.exists():
        fail(f"smoke target must have only one shared source owner: {duplicate.relative_to(ROOT)}")

connector = ROOT / "client-smoke/common/src/main/java/dev/mlua/ci/PlayerTestServerConnector.java"
if not connector.is_file():
    fail("shared native multiplayer-screen connector is missing")
connector_source = connector.read_text(encoding="utf-8")
for contract in ("127.0.0.1:25565", "ServerAddress", "ServerData", "ConnectScreen",
                 'getDeclaredMethods()', 'equals("startConnecting")',
                 'getSimpleName().equals("TransferState")', 'Candidates: " + candidates',
                 'parameters[2] == boolean.class'):
    if contract not in connector_source:
        fail(f"shared player smoke connector is missing {contract}")
if "net.minecraft.client.multiplayer.resolver.ServerAddress" not in connector_source:
    fail("player smoke connector must resolve ServerAddress from Minecraft's resolver package")
if "run-player-smoke.ps1" not in workflow_text or "Boot packaged client and server, then simulate player gameplay" not in workflow_text:
    fail("compile-java workflow does not run the player simulation")
parse_step = workflow_text.find("Parse Windows smoke scripts before downloading Minecraft")
run_client_job = workflow_text.find("  run-client:")
artifact_download = workflow_text.find("uses: actions/download-artifact@v4", run_client_job)
if min(parse_step, run_client_job, artifact_download) < 0:
    fail("Windows player-test job does not validate its PowerShell scripts before setup")
if not run_client_job < parse_step < artifact_download:
    fail("PowerShell parser check must run in the Windows player-test job before artifact setup")
for script in (
    ".github/scripts/prepare-packaged-smoke.ps1",
    ".github/scripts/run-minecraft-smoke.ps1",
    ".github/scripts/run-player-smoke.ps1",
):
    if script not in workflow_text[parse_step:artifact_download]:
        fail(f"early Windows PowerShell parser check does not include {script}")

# Ensure the live player runner treats callback failures from the runtime paths
# exercised by the smoke as failures, rather than accepting a marker emitted
# before a callback disables itself.
patterns_match = re.search(
    r"(?s)\$playerFailurePatterns\s*=\s*@\((.*?)\n\)", runner_text
)
if patterns_match is None:
    fail("player runner has no parseable runtime failure pattern list")
failure_patterns = re.findall(r"'([^']+)'", patterns_match.group(1))
failure_expression = re.compile("(?:" + "|".join(failure_patterns) + ")", re.IGNORECASE | re.MULTILINE)
runtime_failure_examples = (
    "Lua screen button callback failed",
    "Lua screen edit box callback failed",
    "Lua native screen close callback failed",
    "Lua native screen tick callback failed",
    "Lua native screen render callback failed",
    "[MLua] Disabled animation event callback for track 12 at t=0.0 after it failed",
    "[MLua] Animation event callback limit reached for addon 'test.addon' on track 12",
    "MLua entity onTick callback failed for 'test:addon_entity'",
    "[MLua] Disabled custom model adapter for addon 'test.addon' after an error",
    "[MLua] Disabled model adapter resolver for addon 'test.addon' after an error",
    "[MLua] Disabled render callback 'test:pass' for addon 'test.addon' after an error",
    "RenderObject FollowOffset callback failed",
)
unmatched_failures = [
    example for example in runtime_failure_examples if not failure_expression.search(example)
]
if unmatched_failures:
    fail("player runner does not reject these runtime callback failures: " + "; ".join(unmatched_failures))

print(f"Player smoke contract passed ({len(markers)} generated marker names checked).")
