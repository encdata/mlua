param(
    [string]$Target = 'default',
    [string]$Loader = 'all',
    [string[]]$Tasks = @('build')
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$matrixPath = Join-Path $PSScriptRoot 'mlua-targets.json'
$matrix = Get-Content -Raw -LiteralPath $matrixPath | ConvertFrom-Json
$loader = $Loader.ToLowerInvariant()

function Get-MluaJavaMajor([string]$jdkHome) {
    if (-not $jdkHome) { return $null }
    $java = Join-Path $jdkHome 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $java)) { return $null }
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $versionOutput = (& $java -version 2>&1 | Out-String)
    $ErrorActionPreference = $previousErrorAction
    if ($versionOutput -notmatch 'version\s+"(\d+)(?:\.(\d+))?') { return $null }
    $major = [int]$Matches[1]
    if ($major -eq 1) { return [int]$Matches[2] }
    return $major
}

if ($Target -eq 'default') {
    $targetNames = @([string]$matrix.defaultTarget)
} elseif ($Target -in @('all', 'every')) {
    $targetNames = @($matrix.targets | ForEach-Object { [string]$_.minecraft })
} else {
    $targetNames = @($Target -split ',' | ForEach-Object { $_.Trim().Replace('_', '.') } | Where-Object { $_ })
}

$selectedTargets = foreach ($name in $targetNames) {
    $exact = @($matrix.targets | Where-Object { $_.minecraft -eq $name })
    $targetCandidates = if ($exact.Count -gt 0) {
        $exact
    } else {
        @($matrix.targets | Where-Object { $_.minecraft.StartsWith($name + '.') })
    }
    if ($targetCandidates.Count -eq 0) { throw "unknown mlua target '$name'" }
    $targetCandidates
}
$selectedTargets = @($selectedTargets | Sort-Object -Property minecraft -Unique)

$builds = foreach ($targetConfig in $selectedTargets) {
    $loaders = if ($loader -eq 'all') { @($targetConfig.loaders) } else { @($loader) }
    foreach ($selectedLoader in $loaders) {
        if ($targetConfig.loaders -notcontains $selectedLoader) {
            throw "minecraft $($targetConfig.minecraft) does not define the $selectedLoader loader"
        }
        $gradleVersion = $targetConfig.ciGradleVersions.$selectedLoader
        $javaVersion = $targetConfig.ciJavaVersions.$selectedLoader
        if (-not $gradleVersion -or -not $javaVersion) {
            throw "minecraft $($targetConfig.minecraft) is missing its $selectedLoader gradle/java version in the target matrix"
        }
        [PSCustomObject]@{
            target = [string]$targetConfig.minecraft
            loader = [string]$selectedLoader
            gradle = [string]$gradleVersion
            java = [int]$javaVersion
        }
    }
}
$expectedArtifactCount = @($builds).Count

$groups = @($builds | Group-Object -Property loader, gradle, java)
$plannedGroups = foreach ($group in $groups) {
    $first = $group.Group[0]
    $javaHome = [Environment]::GetEnvironmentVariable("MLUA_JAVA_HOME_$($first.java)")
    if ((Get-MluaJavaMajor $javaHome) -ne $first.java) {
        $javaHome = $null
    }
    if (-not $javaHome -and (Get-MluaJavaMajor $env:JAVA_HOME) -eq $first.java) {
        $javaHome = $env:JAVA_HOME
    }
    if (-not $javaHome) {
        $roots = @(
            (Join-Path $env:ProgramFiles 'Java'),
            (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
            (Join-Path $env:USERPROFILE '.gradle\jdks')
        ) | Where-Object { Test-Path -LiteralPath $_ }
        $javaExecutables = foreach ($root in $roots) {
            Get-ChildItem -LiteralPath $root -Filter java.exe -File -Recurse -ErrorAction SilentlyContinue
        }
        foreach ($executable in $javaExecutables) {
            $candidateHome = Split-Path (Split-Path $executable.FullName -Parent) -Parent
            if ((Get-MluaJavaMajor $candidateHome) -eq $first.java) {
                $javaHome = $candidateHome
                break
            }
        }
    }
    if (-not $javaHome) {
        throw "java $($first.java) is required for $($first.loader) $($group.Group.target -join ', '); set MLUA_JAVA_HOME_$($first.java)"
    }
    [PSCustomObject]@{
        loader = $first.loader
        gradle = $first.gradle
        java = $first.java
        javaHome = (Resolve-Path -LiteralPath $javaHome).Path
        targets = @($group.Group | ForEach-Object { $_.target })
    }
}

$wrapperJar = Join-Path $repoRoot 'gradle\wrapper\gradle-wrapper.jar'
if (-not (Test-Path -LiteralPath $wrapperJar)) { throw "missing gradle wrapper jar: $wrapperJar" }

$aggregateArtifacts = $Tasks -contains 'build'
if ($aggregateArtifacts) {
    $buildOutputDir = Join-Path $repoRoot 'build\loader-jars'
    $aggregationDir = Join-Path $repoRoot "build\loader-jars-aggregate-$PID"
    if (Test-Path -LiteralPath $aggregationDir) {
        Remove-Item -LiteralPath $aggregationDir -Recurse -Force
    }
    $groupOutputRoot = Join-Path $aggregationDir 'groups'
    $resultOutputDir = Join-Path $aggregationDir 'result'
    New-Item -ItemType Directory -Path $groupOutputRoot, $resultOutputDir | Out-Null
}

$failures = @()
$manifestEntries = @()
foreach ($plan in $plannedGroups) {
    $wrapperHome = Join-Path $env:TEMP "mlua-gradle-wrapper-$($plan.gradle)"
    New-Item -ItemType Directory -Force -Path $wrapperHome | Out-Null
    $localWrapperJar = Join-Path $wrapperHome 'gradle-wrapper.jar'
    Copy-Item -LiteralPath $wrapperJar -Destination $localWrapperJar -Force
    $properties = @(
        'distributionBase=GRADLE_USER_HOME',
        'distributionPath=wrapper/dists',
        "distributionUrl=https\://services.gradle.org/distributions/gradle-$($plan.gradle)-bin.zip",
        'networkTimeout=10000',
        'validateDistributionUrl=true',
        'zipStoreBase=GRADLE_USER_HOME',
        'zipStorePath=wrapper/dists'
    ) -join "`n"
    Set-Content -LiteralPath (Join-Path $wrapperHome 'gradle-wrapper.properties') -Value $properties -Encoding ASCII

    $java = Join-Path $plan.javaHome 'bin\java.exe'
    $groupOutputDir = $null
    $arguments = @(
        '-classpath', $localWrapperJar, 'org.gradle.wrapper.GradleWrapperMain',
        '-p', $repoRoot,
        "-PmluaTarget=$($plan.targets -join ',')",
        "-PmluaLoader=$($plan.loader)"
    ) + $Tasks
    if ($aggregateArtifacts) {
        $groupName = "$($plan.loader)-$($plan.targets -join '-')"
        $groupOutputDir = Join-Path $groupOutputRoot $groupName
        New-Item -ItemType Directory -Path $groupOutputDir | Out-Null
        $arguments = @($arguments[0..($arguments.Count - $Tasks.Count - 1)]) +
            "-PmluaArtifactOutputDir=$groupOutputDir" + $Tasks
    }
    Write-Host "running $($plan.loader) for $($plan.targets -join ', ') with gradle $($plan.gradle) and java $($plan.java)"
    $previousErrorAction = $ErrorActionPreference
    $previousNativeErrorAction = $PSNativeCommandUseErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $PSNativeCommandUseErrorActionPreference = $false
    & $java @arguments
    $gradleExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorAction
    $PSNativeCommandUseErrorActionPreference = $previousNativeErrorAction
    if ($gradleExitCode -ne 0) {
        $failures += "$($plan.loader) $($plan.targets -join ', '): gradle exited with code $gradleExitCode"
        continue
    }
    if (-not $aggregateArtifacts) { continue }

    $groupManifestPath = Join-Path $groupOutputDir 'manifest.json'
    if (-not (Test-Path -LiteralPath $groupManifestPath)) {
        $failures += "$($plan.loader) $($plan.targets -join ', '): build did not produce its artifact manifest"
        continue
    }
    $groupManifest = Get-Content -Raw -LiteralPath $groupManifestPath | ConvertFrom-Json
    foreach ($entry in $groupManifest) {
        $artifactPath = Join-Path $groupOutputDir ([string]$entry.file)
        if (-not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) {
            $failures += "$($plan.loader) $($plan.targets -join ', '): missing validated artifact '$($entry.file)'"
            continue
        }
        $destinationPath = Join-Path $resultOutputDir ([string]$entry.file)
        if (Test-Path -LiteralPath $destinationPath) {
            $failures += "duplicate loader artifact '$($entry.file)' across build groups"
            continue
        }
        Copy-Item -LiteralPath $artifactPath -Destination $destinationPath
        $manifestEntries += $entry
    }
}

if ($failures.Count -gt 0) {
    if ($aggregateArtifacts -and $aggregationDir -and (Test-Path -LiteralPath $aggregationDir)) {
        Remove-Item -LiteralPath $aggregationDir -Recurse -Force
    }
    throw "mlua build groups failed:`n$($failures -join "`n")"
}
if (-not $aggregateArtifacts) { return }

if ($manifestEntries.Count -ne $expectedArtifactCount) {
    Remove-Item -LiteralPath $aggregationDir -Recurse -Force
    throw "expected $expectedArtifactCount loader artifacts, collected $($manifestEntries.Count)"
}

$orderedManifest = @($manifestEntries | Sort-Object loader, minecraft)
$manifestJson = ConvertTo-Json -InputObject $orderedManifest -Depth 5
[System.IO.File]::WriteAllText(
    (Join-Path $resultOutputDir 'manifest.json'),
    $manifestJson + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false))

$previousOutputDir = Join-Path $repoRoot "build\loader-jars-previous-$PID"
$hadPreviousOutput = Test-Path -LiteralPath $buildOutputDir
if ($hadPreviousOutput) {
    Move-Item -LiteralPath $buildOutputDir -Destination $previousOutputDir
}
try {
    Move-Item -LiteralPath $resultOutputDir -Destination $buildOutputDir
} catch {
    if ($hadPreviousOutput -and (Test-Path -LiteralPath $previousOutputDir)) {
        Move-Item -LiteralPath $previousOutputDir -Destination $buildOutputDir
    }
    throw
}
if ($hadPreviousOutput) {
    Remove-Item -LiteralPath $previousOutputDir -Recurse -Force
}
Remove-Item -LiteralPath $aggregationDir -Recurse -Force
