param(
    [Parameter(Mandatory = $true)][string[]]$GradleArgs,
    [int]$TimeoutSeconds = 360
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'smoke-process.ps1')
$projectDir = (Get-Location).Path
$runRoot = Join-Path $projectDir 'run'
$serverDir = Join-Path $runRoot 'player-server'
$clientDir = Join-Path $runRoot 'player-client'

foreach ($sideDir in @($serverDir, $clientDir)) {
    Remove-Item $sideDir -Recurse -Force -ErrorAction SilentlyContinue
}
if (-not (Test-Path (Join-Path $projectDir 'player-test-scripts/ci_player.server.lua')) -or
    -not (Test-Path (Join-Path $projectDir 'player-test-scripts/ci_player.client.lua'))) {
    throw 'Player-test Lua scripts were not generated; rerun prepare-packaged-smoke.ps1 first.'
}

Remove-Item (Join-Path $runRoot 'common') -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force (Join-Path $runRoot 'common') | Out-Null
Copy-Item (Join-Path $projectDir '../common/*') (Join-Path $runRoot 'common') -Recurse -Force

foreach ($sideDir in @($serverDir, $clientDir)) {
    $sideRunDir = Join-Path $sideDir 'run'
    New-Item -ItemType Directory -Force (Join-Path $sideRunDir 'mods') | Out-Null
    New-Item -ItemType Directory -Force (Join-Path $sideRunDir 'mlua/scripts') | Out-Null
    $configSource = Join-Path $runRoot 'config'
    if (Test-Path $configSource) {
        $configTarget = Join-Path $sideRunDir 'config'
        New-Item -ItemType Directory -Force $configTarget | Out-Null
        Copy-Item (Join-Path $configSource '*') $configTarget -Recurse -Force
    }
    New-Item -ItemType Directory -Force (Join-Path $sideDir 'compile-libs') | Out-Null
    Copy-Item (Join-Path $projectDir 'build.gradle') $sideDir -Force
    Copy-Item (Join-Path $projectDir 'settings.gradle') $sideDir -Force
    $sourceDir = Join-Path $projectDir 'src'
    if (Test-Path $sourceDir) {
        Copy-Item $sourceDir $sideDir -Recurse -Force
    }
    Copy-Item (Join-Path $projectDir 'compile-libs/*.jar') (Join-Path $sideDir 'compile-libs') -Force
    Copy-Item (Join-Path $runRoot 'mods/*.jar') (Join-Path $sideRunDir 'mods') -Force
    Copy-Item (Join-Path $runRoot 'mlua/scripts/*') (Join-Path $sideRunDir 'mlua/scripts') -Recurse -Force
    Copy-Item (Join-Path $projectDir 'player-test-scripts/*.lua') `
        (Join-Path $sideRunDir 'mlua/scripts') -Force
}
Copy-Item (Join-Path $runRoot 'eula.txt') (Join-Path $serverDir 'run') -Force
Copy-Item (Join-Path $runRoot 'server.properties') (Join-Path $serverDir 'run') -Force

$serverOut = Join-Path $projectDir 'player-server.gradle.log'
$serverErr = "$serverOut.stderr"
$clientOut = Join-Path $projectDir 'player-client.gradle.log'
$clientErr = "$clientOut.stderr"
$serverLatest = Join-Path $serverDir 'run/logs/latest.log'
$clientLatest = Join-Path $clientDir 'run/logs/latest.log'
Remove-Item @($serverOut, $serverErr, $clientOut, $clientErr, $serverLatest, $clientLatest) `
    -Force -ErrorAction SilentlyContinue

function Start-SmokeGame([string]$Name, [string]$SideDir, [string[]]$Arguments, [string]$Stdout, [string]$Stderr) {
    $launcher = Join-Path $projectDir "$Name-launch.cmd"
    # prepare-packaged-smoke.ps1 writes a target-specific wrapper at the test
    # template root. Reuse it here; the repository wrapper may use a newer
    # Gradle release that the selected loader plugin cannot run on (Forge 1.x).
    $gradleWrapper = Join-Path $projectDir 'gradlew.bat'
    $argumentText = (@($GradleArgs) + @('-p', "`"$SideDir`"") + $Arguments) -join ' '
    $launcherLines = @('@echo off')
    $launcherLines += 'set MLUA_CI_PLAYER_TEST=true'
    $launcherLines += "call `"$gradleWrapper`" $argumentText"
    $launcherLines += 'exit /b %ERRORLEVEL%'
    $launcherLines | Set-Content -Path $launcher -Encoding Ascii
    Start-Process -FilePath $env:ComSpec -ArgumentList @('/d', '/c', "`"$launcher`"") `
        -WorkingDirectory $SideDir -RedirectStandardOutput $Stdout `
        -RedirectStandardError $Stderr -PassThru
}

$script:playerLogOffsets = @{}
$script:playerLogContents = @{}
$playerFailurePatterns = @(
    "Client (?:entrypoint )?script .+ failed",
    "Server (?:entrypoint )?script .+ failed",
    'Scheduled MLua task failed',
    'Disabled (?:render callback|custom model adapter|model adapter resolver|entity component)',
    'Lua screen (?:button|edit box) callback failed',
    'Lua native screen (?:close|tick|render) callback failed',
    'Disabled animation event callback',
    'Animation event callback limit reached',
    'Animation event callback limit reached for addon',
    'hand pose callback failed',
    'MLua .+ callback failed',
    'RenderObject FollowOffset callback failed'
)
$requiredClientRuntimeMarkers = @(
    'MLUA_CI_CLIENT_SERVICE_SCRIPT_OK',
    'MLUA_CI_PLAYER_HEARTBEAT_FIRED',
    'MLUA_CI_INPUT_KEYBINDS_OK',
    'MLUA_CI_BBMODEL_HIERARCHY_OK',
    'MLUA_CI_ANIMATION_API_OK',
    'MLUA_CI_NATIVE_SCREEN_STATE_OK',
    'MLUA_CI_NATIVE_SCREEN_TICK_OK',
    'MLUA_CI_NATIVE_SCREEN_CONTROLS_OK',
    'MLUA_CI_NATIVE_SCREEN_BUTTON_CALLBACK_OK',
    'MLUA_CI_NATIVE_SCREEN_EDITBOX_CHANGE_OK',
    'MLUA_CI_CLOSED_SCREEN_CONTROLS_OK',
    'MLUA_CI_SERVICE_PARITY_OK'
)
function Get-CombinedLog([string[]]$Paths) {
    foreach ($path in $Paths) {
        if (-not (Test-Path $path)) { continue }

        $stream = [System.IO.File]::Open(
            $path,
            [System.IO.FileMode]::Open,
            [System.IO.FileAccess]::Read,
            [System.IO.FileShare]::ReadWrite)
        try {
            $offset = [long]0
            if ($script:playerLogOffsets.ContainsKey($path)) {
                $offset = [long]$script:playerLogOffsets[$path]
                if ($stream.Length -lt $offset) {
                    $offset = 0
                    $script:playerLogContents[$path] = ''
                }
            } else {
                $script:playerLogContents[$path] = ''
            }

            if ($stream.Length -gt $offset) {
                $stream.Seek($offset, [System.IO.SeekOrigin]::Begin) | Out-Null
                $reader = [System.IO.StreamReader]::new(
                    $stream, [System.Text.Encoding]::UTF8, $true, 4096, $true)
                try {
                    $script:playerLogContents[$path] += $reader.ReadToEnd()
                    $script:playerLogOffsets[$path] = $stream.Position
                } finally {
                    $reader.Dispose()
                }
            }
        } finally {
            $stream.Dispose()
        }
    }

    $content = [System.Text.StringBuilder]::new()
    foreach ($path in $Paths) {
        if ($script:playerLogContents.ContainsKey($path)) {
            [void]$content.Append($script:playerLogContents[$path])
        }
    }
    return $content.ToString()
}

function Test-PlayerServerListening {
    return $null -ne (Get-NetTCPConnection -State Listen -LocalPort 25565 -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalAddress -eq '0.0.0.0' -or $_.LocalAddress -eq '127.0.0.1' -or $_.LocalAddress -eq '::' } |
        Select-Object -First 1)
}

$serverProcess = $null
$clientProcess = $null
$serverPaths = @($serverOut, $serverErr, $serverLatest)
$clientPaths = @($clientOut, $clientErr, $clientLatest)
try {
    $serverProcess = Start-SmokeGame 'player-server' $serverDir @('-PmluaServerRunDir=run', 'runServer') `
        $serverOut $serverErr
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $serverScriptReady = $false
    $serverPortReady = $false
    while (-not ($serverScriptReady -and $serverPortReady)) {
        $serverProcess.Refresh()
        if ($serverProcess.HasExited) { throw 'The packaged player-test server exited before loading its Lua join listener.' }
        $serverScriptReady = (Get-CombinedLog $serverPaths).Contains('MLUA_CI_PLAYER_SERVER_LISTENER_READY')
        $serverPortReady = Test-PlayerServerListening
        if ([DateTime]::UtcNow -ge $deadline) {
            throw "Timed out waiting for the player-test server (Lua listener ready=$serverScriptReady, TCP port listening=$serverPortReady)."
        }
        Start-Sleep -Seconds 2
    }
    Write-Host 'Player-test server has loaded its Lua listener and opened TCP port 25565; launching the packaged Minecraft client.'

    $clientProcess = Start-SmokeGame 'player-client' $clientDir `
        @('-PmluaClientRunDir=run', 'runClient') `
        $clientOut $clientErr
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $clientJoined = $false
    $serverJoined = $false
    $samePlayer = $false
    $clientChatSent = $false
    $serverChatReceived = $false
    $serverGreetingReceived = $false
    $clientAttackSubmitted = $false
    $serverAttackConfirmed = $false
    $clientAttackConfirmed = $false
    $serverPickupCountPassed = $false
    $playerPickupReceived = $false
    $serverContainerDataPassed = $false
    $serverContainerOpened = $false
    $clientContainerOpened = $false
    $scriptedEntitySpawnPassed = $false
    $scriptedEntityTickPassed = $false
    $scriptedEntityAIPassed = $false
    $mobSpawnPassed = $false
    $worldGeometryContextPassed = $false
    $renderDeltaTimePassed = $false
    $worldTransformMeshDrawn = $false
    $firstPersonItemRendered = $false
    $thirdPersonItemRendered = $false
    $playerModelRendered = $false
    $playerAdapterApplied = $false
    $playerAnimationPoseReady = $false
    $mobModelRendered = $false
    $modelAdapterConnectionPassed = $false
    $mobAdapterApplied = $false
    $mobAnimationPoseReady = $false
    $inputPressReleasePassed = $false
    $playerMovementPassed = $false
    $mousePressReleasePassed = $false
    $checksComplete = $false
    while (-not $checksComplete) {
        $clientProcess.Refresh()
        $serverProcess.Refresh()
        $clientLog = Get-CombinedLog $clientPaths
        $serverLog = Get-CombinedLog $serverPaths
        $failurePattern = '(?im)(?:' + ($playerFailurePatterns -join '|') + ')'
        $mluaFailure = [regex]::Match(($clientLog + $serverLog), $failurePattern)
        if ($mluaFailure.Success) {
            throw "MLua reported a runtime failure during player simulation: $($mluaFailure.Value)"
        }
        $missingClientRuntimeMarkers = @($requiredClientRuntimeMarkers | Where-Object {
            -not $clientLog.Contains($_)
        })
        $clientJoin = [regex]::Match($clientLog, 'MLUA_CI_PLAYER_CLIENT_JOINED:([A-Za-z0-9_]+)')
        $serverJoin = [regex]::Match($serverLog, 'MLUA_CI_PLAYER_SERVER_JOINED:([A-Za-z0-9_]+)')
        $clientJoined = $clientJoin.Success
        $serverJoined = $serverJoin.Success
        $samePlayer = $clientJoined -and $serverJoined -and $clientJoin.Groups[1].Value -eq $serverJoin.Groups[1].Value
        $serverWorldDotCallPassed = $serverLog.Contains('MLUA_CI_PLAYER_WORLD_DOT_CALL_OK')
        $clientChatSent = $clientLog.Contains('MLUA_CI_PLAYER_CHAT_SUBMITTED')
        $serverChatReceived = $serverLog.Contains('MLUA_CI_PLAYER_CHAT_SENT')
        $serverGreetingReceived = $clientLog.Contains('MLUA_CI_PLAYER_GREETING_RECEIVED')
        $clientAttackSubmitted = $clientLog.Contains('MLUA_CI_PLAYER_ATTACK_SUBMITTED')
        $serverAttackConfirmed = $serverLog.Contains('MLUA_CI_PLAYER_ATTACK_CONFIRMED')
        $clientAttackConfirmed = $clientLog.Contains('MLUA_CI_PLAYER_ATTACK_CONFIRMED')
        $serverPickupCountPassed = $serverLog.Contains('MLUA_CI_PLAYER_PICKUP_COUNT_OK')
        $playerPickupReceived = $clientLog.Contains('MLUA_CI_PLAYER_PICKUP_RECEIVED')
        $serverBlockEntityOnLoadPassed = $serverLog.Contains('MLUA_CI_PLAYER_BLOCK_ENTITY_ON_LOAD_INSTANCE_OK')
        $serverContainerDataPassed = $serverLog.Contains('MLUA_CI_PLAYER_CONTAINER_DATA_OK')
        $serverContainerOpened = $serverLog.Contains('MLUA_CI_PLAYER_CONTAINER_OPENED')
        $clientContainerOpened = $clientLog.Contains('MLUA_CI_PLAYER_CLIENT_CONTAINER_OPENED')
        $scriptedEntitySpawnPassed = $serverLog.Contains('MLUA_CI_SCRIPTED_ENTITY_SPAWN_OK')
        $scriptedEntityTickPassed = $serverLog.Contains('MLUA_CI_SCRIPTED_ENTITY_TICK_OK')
        $scriptedEntityAIPassed = $serverLog.Contains('MLUA_CI_SCRIPTED_ENTITY_AI_TRANSITION_OK')
        $mobSpawnPassed = $serverLog.Contains('MLUA_CI_PLAYER_MOB_SPAWN_OK')
        $worldGeometryContextPassed = $clientLog.Contains('MLUA_CI_PLAYER_WORLD_GEOMETRY_CONTEXT_OK')
        $renderDeltaTimePassed = $clientLog.Contains('MLUA_CI_PLAYER_RENDER_DELTA_TIME_OK')
        $worldTransformMeshDrawn = $clientLog.Contains('MLUA_CI_PLAYER_MODEL_TRANSFORM_DRAW_OK')
        $firstPersonItemRendered = $clientLog.Contains('MLUA_CI_PLAYER_FIRST_PERSON_ITEM_RENDERED')
        $thirdPersonItemRendered = $clientLog.Contains('MLUA_CI_PLAYER_THIRD_PERSON_ITEM_RENDERED')
        $playerModelRendered = $clientLog.Contains('MLUA_CI_PLAYER_MODEL_RENDERED')
        $playerAdapterApplied = $clientLog.Contains('MLUA_CI_PLAYER_MODEL_ADAPTER_APPLIED')
        $playerAnimationPoseReady = $clientLog.Contains('MLUA_CI_PLAYER_ANIMATION_POSE_READY')
        $mobModelRendered = $clientLog.Contains('MLUA_CI_PLAYER_MOB_MODEL_RENDERED')
        $modelAdapterConnectionPassed = $clientLog.Contains('MLUA_CI_PLAYER_MODEL_ADAPTER_CONNECTION_OK')
        $mobAdapterApplied = $clientLog.Contains('MLUA_CI_PLAYER_MOB_ADAPTER_APPLIED')
        $mobAnimationPoseReady = $clientLog.Contains('MLUA_CI_PLAYER_MOB_ANIMATION_POSE_READY')
        $inputPressReleasePassed = $clientLog.Contains('MLUA_CI_PLAYER_INPUT_PRESS_RELEASE_OK')
        $playerMovementPassed = $clientLog.Contains('MLUA_CI_PLAYER_MOVEMENT_OK')
        $mousePressReleasePassed = $clientLog.Contains('MLUA_CI_PLAYER_MOUSE_PRESS_RELEASE_OK')
        $checksComplete = (
            $missingClientRuntimeMarkers.Count -eq 0 -and
            $clientJoined -and $serverJoined -and $samePlayer -and
            $serverWorldDotCallPassed -and
            $clientChatSent -and $serverChatReceived -and $serverGreetingReceived -and
            $clientAttackSubmitted -and $serverAttackConfirmed -and $clientAttackConfirmed -and
            $serverPickupCountPassed -and $playerPickupReceived -and $serverBlockEntityOnLoadPassed -and
            $serverContainerDataPassed -and
            $serverContainerOpened -and $clientContainerOpened -and
            $scriptedEntitySpawnPassed -and $scriptedEntityTickPassed -and $scriptedEntityAIPassed -and
            $mobSpawnPassed -and
            $worldGeometryContextPassed -and
            $renderDeltaTimePassed -and
            $worldTransformMeshDrawn -and
            $firstPersonItemRendered -and $thirdPersonItemRendered -and $playerModelRendered -and
            $playerAdapterApplied -and $playerAnimationPoseReady -and $mobModelRendered -and
            $modelAdapterConnectionPassed -and $mobAdapterApplied -and $mobAnimationPoseReady -and
            $inputPressReleasePassed -and $playerMovementPassed -and $mousePressReleasePassed
        )

        if ($serverProcess.HasExited) {
            throw "The packaged player-test server exited during simulation (clientJoined=$clientJoined, serverJoined=$serverJoined, serverChatReceived=$serverChatReceived)."
        }
        if ($clientProcess.HasExited -and -not $checksComplete) {
            throw 'The packaged Minecraft client exited before completing the player join, chat, and render checks.'
        }
        if ([DateTime]::UtcNow -ge $deadline) {
            $missingRuntime = if ($missingClientRuntimeMarkers.Count -eq 0) { 'none' } else { $missingClientRuntimeMarkers -join ', ' }
            $simulationDiagnostics = @(
                "missingClientRuntimeMarkers=$missingRuntime",
                "clientJoined=$clientJoined",
                "serverJoined=$serverJoined",
                "samePlayer=$samePlayer",
                "serverWorldDotCallPassed=$serverWorldDotCallPassed",
                "clientChatSent=$clientChatSent",
                "serverChatReceived=$serverChatReceived",
                "serverGreetingReceived=$serverGreetingReceived",
                "clientAttackSubmitted=$clientAttackSubmitted",
                "serverAttackConfirmed=$serverAttackConfirmed",
                "clientAttackConfirmed=$clientAttackConfirmed",
                "serverPickupCountPassed=$serverPickupCountPassed",
                "playerPickupReceived=$playerPickupReceived",
                "serverBlockEntityOnLoadPassed=$serverBlockEntityOnLoadPassed",
                "serverContainerDataPassed=$serverContainerDataPassed",
                "serverContainerOpened=$serverContainerOpened",
                "clientContainerOpened=$clientContainerOpened",
                "scriptedEntitySpawnPassed=$scriptedEntitySpawnPassed",
                "scriptedEntityTickPassed=$scriptedEntityTickPassed",
                "scriptedEntityAIPassed=$scriptedEntityAIPassed",
                "mobSpawnPassed=$mobSpawnPassed",
                "worldGeometryContextPassed=$worldGeometryContextPassed",
                "renderDeltaTimePassed=$renderDeltaTimePassed",
                "worldTransformMeshDrawn=$worldTransformMeshDrawn",
                "firstPersonItemRendered=$firstPersonItemRendered",
                "thirdPersonItemRendered=$thirdPersonItemRendered",
                "playerModelRendered=$playerModelRendered",
                "playerAdapterApplied=$playerAdapterApplied",
                "playerAnimationPoseReady=$playerAnimationPoseReady",
                "mobModelRendered=$mobModelRendered",
                "modelAdapterConnectionPassed=$modelAdapterConnectionPassed",
                "mobAdapterApplied=$mobAdapterApplied",
                "mobAnimationPoseReady=$mobAnimationPoseReady",
                "inputPressReleasePassed=$inputPressReleasePassed",
                "playerMovementPassed=$playerMovementPassed",
                "mousePressReleasePassed=$mousePressReleasePassed"
            ) -join ", "
            throw "Timed out during player simulation ($simulationDiagnostics)."
        }
        Start-Sleep -Seconds 2
    }
    Write-Host "Player simulation passed for '$($clientJoin.Groups[1].Value)': the isolated client completed its Java service and native-screen callbacks, join/chat round-tripped, Entity:Attack reached the server and produced an EntityHurt event for the intended sheep, the player picked up a server-dropped stack with the expected count, the scripted block entity accessed its live container and NBT during onLoad, a simulated right-click opened the scripted container on both server and client, a scripted entity ran spawn/tick/AI-transition callbacks, simulated inputs moved the player and released cleanly, world geometry and held-item render passes fired, and Lua adapters applied animation poses to the player and a sheep model."
}
finally {
    foreach ($process in @($clientProcess, $serverProcess)) {
        if ($null -ne $process) {
            Stop-SmokeProcessTree $process
        }
    }
    Remove-Item (Join-Path $projectDir 'player-server-launch.cmd'), `
        (Join-Path $projectDir 'player-client-launch.cmd') -Force -ErrorAction SilentlyContinue
}
