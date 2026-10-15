param(
    [Parameter(Mandatory = $true)][string]$Task,
    [Parameter(Mandatory = $true)][string[]]$GradleArgs,
    [Parameter(Mandatory = $true)][string]$BootMarker,
    [string[]]$RequiredMarker = @(),
    [Parameter(Mandatory = $true)][string]$LogFile,
    [int]$TimeoutSeconds = 480,
    [int]$ShutdownGraceSeconds = 30
)

$ErrorActionPreference = 'Stop'
$stdoutPath = Join-Path (Get-Location) $LogFile
$stderrPath = "$stdoutPath.stderr"
$minecraftLogPath = Join-Path (Get-Location) 'run/logs/latest.log'
Remove-Item $stdoutPath, $stderrPath -Force -ErrorAction SilentlyContinue
Remove-Item $minecraftLogPath -Force -ErrorAction SilentlyContinue

$arguments = @($GradleArgs) + @($Task)
$argumentText = $arguments -join ' '
$command = "gradle $argumentText 1> `"$stdoutPath`" 2> `"$stderrPath`""
$process = Start-Process -FilePath $env:ComSpec -ArgumentList @('/d', '/c', $command) `
    -WorkingDirectory (Get-Location).Path -PassThru

function Get-SmokeOutput {
    $text = ''
    if (Test-Path $stdoutPath) { $text += Get-Content $stdoutPath -Raw -ErrorAction SilentlyContinue }
    if (Test-Path $stderrPath) { $text += Get-Content $stderrPath -Raw -ErrorAction SilentlyContinue }
    if (Test-Path $minecraftLogPath) { $text += Get-Content $minecraftLogPath -Raw -ErrorAction SilentlyContinue }
    return $text
}

$deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
$markerSeenAt = $null
$booted = $false
$clientTask = $Task -eq 'runClient'
$graphicsFailure = '(?i)(Failed to create a GLFW window|Failed to find any valid GLFW profile|Failed to find a valid GLFW profile|WGL: The driver does not appear to support OpenGL|Timed out trying to setup the Game Window|ERROR DISPLAY)'

try {
    while ($true) {
        $process.Refresh()
        $text = Get-SmokeOutput

        if (-not $booted -and $text.Contains($BootMarker)) {
            $booted = $true
            $markerSeenAt = [DateTime]::UtcNow
            Write-Host "Observed packaged Minecraft boot marker: $BootMarker"
        }

        if ($clientTask -and $text -match $graphicsFailure) {
            throw 'Minecraft could not initialize OpenGL with the CI software renderer; see the captured launcher log.'
        }

        if ($process.HasExited) { break }

        if ($booted -and [DateTime]::UtcNow -ge $markerSeenAt.AddSeconds($ShutdownGraceSeconds)) {
            Write-Host "Boot marker was observed; stopping the smoke launcher after its shutdown grace period."
            break
        }

        if ([DateTime]::UtcNow -ge $deadline) {
            throw "Timed out waiting for '$BootMarker' while running Gradle task '$Task'."
        }
        Start-Sleep -Seconds 2
    }

    $process.Refresh()
    if (-not $process.HasExited) {
        & taskkill.exe /PID $process.Id /T /F | Out-Null
        $process.WaitForExit()
    }

    $text = Get-SmokeOutput

    if ($text -match "(?im)MLua module '.+' (failed during classes|failed while registering|was disabled after its registration failed|was disabled after its class registration failed)") {
        throw 'MLua reached Minecraft startup but one or more API modules failed registration; see the captured launcher log.'
    }
    if (-not $booted) {
        throw "Gradle task '$Task' exited before MLua emitted '$BootMarker'."
    }
    foreach ($marker in $RequiredMarker) {
        if (-not $text.Contains($marker)) {
            throw "Minecraft emitted '$BootMarker' but did not emit the required smoke marker '$marker'."
        }
    }
    if (-not $clientTask -and $process.ExitCode -ne 0) {
        throw "Dedicated server smoke task exited with code $($process.ExitCode)."
    }
    Write-Host "Packaged Minecraft smoke task '$Task' completed successfully."
}
finally {
    $process.Refresh()
    if (-not $process.HasExited) {
        & taskkill.exe /PID $process.Id /T /F | Out-Null
        $process.WaitForExit()
    }
}
