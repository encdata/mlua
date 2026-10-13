[CmdletBinding()]
param(
    [ValidateRange(1, 16)]
    [int]$ThrottleLimit = 5,
    [ValidateRange(1, 32)]
    [int]$GradleWorkers = 4,
    [ValidateSet('build', 'assemble')]
    [string]$Task = 'build',
    [switch]$ParallelTargets,
    [switch]$ContinueOnError
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
# Pin Gradle's cache explicitly so PowerShell-launched builds use the same
# ForgeGradle/Loom artifacts as an interactive Gradle invocation.
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
$gradleCache = Join-Path $env:USERPROFILE '.gradle\wrapper\dists'
$legacyGradle = Get-ChildItem (Join-Path $gradleCache 'gradle-8.10.2-bin') -Recurse -Filter gradle.bat -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
$modernGradle = Get-ChildItem (Join-Path $gradleCache 'gradle-9.2.1-bin') -Recurse -Filter gradle.bat -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName

if (-not $legacyGradle) { throw 'Gradle 8.10.2 is required for legacy targets; install it or populate the Gradle wrapper cache.' }
if (-not $modernGradle) { throw 'Gradle 9.2.1 is required for the 26.1 family targets; install it or populate the Gradle wrapper cache.' }

$targets = Get-Content (Join-Path $root 'gradle\mlua-targets.json') -Raw | ConvertFrom-Json
$loaderJarsDir = Join-Path $root 'build\loader-jars'
$aggregateDir = Join-Path $root 'build\loader-jars-all'
New-Item -ItemType Directory -Force -Path $aggregateDir | Out-Null
$logDir = Join-Path $root 'build\parallel-logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

foreach ($outputDir in @($loaderJarsDir, $aggregateDir)) {
    $resolvedRoot = [IO.Path]::GetFullPath($root).TrimEnd('\') + '\'
    $resolvedOutput = [IO.Path]::GetFullPath($outputDir)
    if (-not $resolvedOutput.StartsWith($resolvedRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clear output outside the workspace: $resolvedOutput"
    }
    if (Test-Path -LiteralPath $resolvedOutput) {
        Remove-Item -LiteralPath $resolvedOutput -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $resolvedOutput | Out-Null
}

$failed = @()
$targetList = @($targets.targets)
$batchSize = if ($ParallelTargets) { $ThrottleLimit } else { 1 }
if ($ParallelTargets) {
    Write-Warning 'Parallel target builds are experimental: target versions share Gradle project output directories. Use this only with isolated workspaces.'
} else {
    Write-Host "Building targets sequentially; Gradle projects use up to $GradleWorkers workers." -ForegroundColor Yellow
}

foreach ($target in $targetList) {
    $version = [string]$target.minecraft
    $gradle = if ($version -like '26.1*') { $modernGradle } else { $legacyGradle }
    $safeVersion = $version.Replace('.', '_')
    $log = Join-Path $logDir "build-$safeVersion.log"
    Write-Host "[$version] building; log: $log" -ForegroundColor Cyan
    "[$version] START $(Get-Date -Format o)" | Set-Content $log -Encoding UTF8
    Push-Location $root
    try {
        # Gradle writes progress and failures to stderr; do not let PowerShell's
        # Stop preference abort the wrapper before we can record the exit code.
        $savedPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        $output = @(& $gradle ("-PmluaTarget={0}" -f $version) $Task "--max-workers=$GradleWorkers" '--no-daemon' '--console=plain' 2>&1)
        $gradleExitCode = $LASTEXITCODE
        $ErrorActionPreference = $savedPreference
    } finally { Pop-Location }
    $output | ForEach-Object { $line = "[$version] $_"; Add-Content -Path $log -Value $line -Encoding UTF8; Write-Host $line }
    $reportedFailure = @($output | ForEach-Object { $_.ToString() } | Where-Object { $_ -match 'BUILD FAILED|FAILURE: Build failed' }).Count -gt 0
    if ($gradleExitCode -ne 0 -or $reportedFailure) {
        $failed += $version
        Write-Host "[$version] FAILED; see $log" -ForegroundColor Red
        if (-not $ContinueOnError) { throw "Gradle failed for target $version (exit code $gradleExitCode; see $log)" }
        continue
    }
    "[$version] SUCCESS $(Get-Date -Format o)" | Add-Content $log -Encoding UTF8
    Write-Host "[$version] completed" -ForegroundColor Green

    # Gradle's per-target collectLoaderJars task owns the shared staging
    # directory, so preserve this completed batch before the next target runs.
    if (Test-Path -LiteralPath $loaderJarsDir) {
        Get-ChildItem $loaderJarsDir -Filter '*.jar' -File | Copy-Item -Destination $aggregateDir -Force
    }
}

if ($failed.Count -gt 0 -and -not $ContinueOnError) {
    throw "Parallel build failures: $($failed -join ', ')"
}

if (Test-Path $loaderJarsDir) {
    Get-ChildItem $loaderJarsDir -Filter '*.jar' -File | Copy-Item -Destination $aggregateDir -Force
}

$finalDir = $loaderJarsDir
Get-ChildItem $aggregateDir -Filter '*.jar' -File | Copy-Item -Destination $finalDir -Force

$manifest = Get-ChildItem $finalDir -Filter 'mlua-*.jar' -File | ForEach-Object {
    if ($_.Name -match '^mlua-(?<loader>[^-]+)-(?<minecraft>.+)-(?<version>[^-]+)\.jar$') {
        [ordered]@{
            loader = $Matches.loader
            minecraft = $Matches.minecraft
            file = $_.Name
            bytes = $_.Length
            sha256 = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    }
} | Sort-Object minecraft, loader
$manifest | ConvertTo-Json -Depth 3 | Set-Content (Join-Path $finalDir 'manifest.json')

if ($failed.Count -eq 0) {
    Write-Host 'All MLua targets built successfully.' -ForegroundColor Green
} else {
    Write-Host "Completed with failures: $($failed -join ', ')" -ForegroundColor Yellow
    exit 1
}
