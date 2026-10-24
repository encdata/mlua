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
            if service_name in discovered:
                fail(f"service name {service_name} is registered by multiple modules")
            discovered[service_name] = (matches[0], module_path)
    return discovered


service_api_sources = discover_service_sources()


prepare_text = prepare.read_text(encoding="utf-8")
runner_text = runner.read_text(encoding="utf-8")
workflow_text = workflow.read_text(encoding="utf-8")
render_service_text = (ROOT / "src/main/java/dev/mlua/modules/render/service/MLuaRenderService.java").read_text(encoding="utf-8")
render_object_operations_text = (ROOT / "src/main/java/dev/mlua/modules/render/service/MLuaRenderObjectOperations.java").read_text(encoding="utf-8")
render_manager_text = (ROOT / "src/main/java/dev/mlua/runtime/client/MLuaClientRenderManager.java").read_text(encoding="utf-8")
process_helper_text = (ROOT / ".github/scripts/smoke-process.ps1").read_text(encoding="utf-8")

starts = len(re.findall(r"(?m)^(?:.*=\s*)?@'\s*$", prepare_text))
ends = len(re.findall(r"(?m)^'@(?:\s*\|.*)?$", prepare_text))
if starts != ends:
    fail(f"unbalanced PowerShell here-strings in prepare script ({starts} starts, {ends} ends)")

for path in ("ci_player.client.lua", "ci_player.server.lua"):
    if f'player-test-scripts/{path}' not in prepare_text:
        fail(f"prepare script does not generate {path}")

markers = sorted(set(re.findall(r"MLUA_CI_(?:PLAYER|SCRIPTED_ENTITY)_[A-Z0-9_]+", runner_text)))
missing = [marker for marker in markers if marker not in prepare_text]
if missing:
    fail("runner waits for markers not emitted by generated scripts: " + ", ".join(missing))

client_runtime_markers = (
    "MLUA_CI_CLIENT_SERVICE_SCRIPT_OK",
    "MLUA_CI_PLAYER_HEARTBEAT_FIRED",
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
missing_runtime_markers = [marker for marker in client_runtime_markers if marker not in required_runtime_markers]
if missing_runtime_markers:
    fail("isolated player client does not require these service/UI markers: "
         + ", ".join(missing_runtime_markers))
unemitted_runtime_markers = [marker for marker in client_runtime_markers if marker not in prepare_text]
if unemitted_runtime_markers:
    fail("isolated player client waits for markers not emitted by its generated script: "
         + ", ".join(unemitted_runtime_markers))
if "$missingClientRuntimeMarkers.Count -eq 0" not in runner_text:
    fail("isolated client service/UI runtime markers do not gate player simulation success")
if not re.search(r"(?s)if playerSeen and inputPressState == 0 then.*?MLUA_CI_PLAYER_INPUT_PRESS_BEGIN", prepare_text):
    fail("input simulation must start after local-player readiness, independently of item pickup")
if re.search(r"(?s)if playerPickupReceived and inputPressState == 0 then", prepare_text):
    fail("input simulation is still blocked on the independent item-pickup event")

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
if 'MLUA_CI_COOKING_RESULT_COUNT_OK' not in workflow_text:
    fail("dedicated-server smoke does not require the cooking recipe result-count check")
if 'MLUA_CI_ORE_VEIN_REGISTRATION_OK' not in prepare_text \
        or workflow_text.count('MLUA_CI_ORE_VEIN_REGISTRATION_OK') != 2:
    fail("packaged smoke does not require ore-vein registration on both client and server targets")
if '__MLUA_TARGET__' not in prepare_text or '$env:MLUA_TARGET' not in prepare_text:
    fail("cooking recipe smoke does not select the format expected by the target version")
if 'print("MLUA_CI_MODEL_TRANSFORM_OK")' not in prepare_text or 'MLUA_CI_MODEL_TRANSFORM_OK' not in workflow_text:
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
if workflow_text.index("Boot dedicated server with the packaged MLua JAR") > workflow_text.index("Simulate player gameplay against the dedicated server"):
    fail("player simulation is scheduled before the dedicated server boot check")
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
if "run-player-smoke.ps1" not in workflow_text or "Simulate player gameplay" not in workflow_text:
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
