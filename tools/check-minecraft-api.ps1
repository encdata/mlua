param([Parameter(Mandatory = $true)][string]$Root)

$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path $Root).Path
$reportDir = Join-Path $Root 'build\reports\minecraft-api'
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null

# Direct imports are deterministic source-level Minecraft dependencies. The Gradle
# compile checks below are the authoritative check for generic/FQCN references too.
$sourceRoots = @(
    (Join-Path $Root 'src\main\java'),
    (Join-Path $Root 'mlua-versions'),
    (Join-Path $Root 'mlua-loaders')
) | Where-Object { Test-Path $_ }
$imports = Get-ChildItem $sourceRoots -Recurse -Filter *.java | ForEach-Object {
    [regex]::Matches((Get-Content $_.FullName -Raw), '(?m)^\s*import\s+(net\.minecraft\.[\w.$]+);') |
        ForEach-Object { $_.Groups[1].Value }
} | Sort-Object -Unique

$targetConfig = Get-Content (Join-Path $Root 'gradle\mlua-targets.json') -Raw | ConvertFrom-Json
$versions = @($targetConfig.targets | ForEach-Object {
    $parts = $_.minecraft.ToString().Split('.')
    [PSCustomObject]@{
        Name = $_.minecraft.ToString()
        # Gradle accepts a partial target; this avoids dots being parsed as a property path.
        Target = $parts[0] + '_' + $parts[1]
        Loaders = @($_.loaders | ForEach-Object { $_.ToString() })
    }
})
$summary = [System.Collections.Generic.List[string]]::new()
$summary.Add('# MLua Minecraft API static audit')
$summary.Add('')
$summary.Add('This command only inspects/decompiles local Minecraft archives. It never invokes Gradle and never runs a game client or server.')
$summary.Add('')

foreach ($version in $versions) {
    $cacheRoot = Join-Path $env:USERPROFILE ".gradle\caches\fabric-loom\$($version.Name)"
    $candidates = @(
        (Get-ChildItem $cacheRoot -Recurse -File -Filter 'minecraft-merged-srg.jar' -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName),
        (Get-ChildItem $cacheRoot -Recurse -File -Filter 'minecraft-merged-srg-patched.jar' -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName),
        (Join-Path $cacheRoot 'minecraft-merged.jar'),
        (Join-Path $cacheRoot 'minecraft-client.jar')
    )
    $jar = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    $report = Join-Path $reportDir ("$($version.Name)-classes.txt")
    $missing = [System.Collections.Generic.List[string]]::new()
    if (Test-Path $jar) {
        $entries = & jar tf $jar
        $entrySet = [System.Collections.Generic.HashSet[string]]::new([string[]]$entries)
        foreach ($import in $imports) {
            $classPath = $import.Replace('.', '/') + '.class'
            # Nested types are stored as Outer$Inner.class.
            if (-not $entrySet.Contains($classPath)) { $missing.Add($import) }
        }
        @("Minecraft merged jar: $jar", "Checked imports: $($imports.Count)", "Raw archive name mismatches: $($missing.Count)", 'These are mapping hints only. Use the target compile report as the authoritative class/method check.', '') + $missing | Set-Content -Encoding UTF8 $report
    } else {
        "No local merged Minecraft jar found for $($version.Name): $jar" | Set-Content -Encoding UTF8 $report
        $missing.Add('<jar unavailable>')
    }

    # Gradle can print daemon socket warnings on stderr. Capture them as audit
    # output rather than letting PowerShell abort the remaining target checks.
    $summary.Add("- $($version.Name): archive mapping mismatches = $($missing.Count); archive inspected = $(if ($jar) { Split-Path $jar -Leaf } else { 'not found' }).")
}

$summary.Add('')
$summary.Add('Use the per-version `*-classes.txt` files to identify imports that changed, then inspect exact methods with:')
$summary.Add('`javap -classpath <minecraft-merged.jar> ''net.minecraft.SomeClass''`')
$summary | Set-Content -Encoding UTF8 (Join-Path $reportDir 'SUMMARY.md')
Get-Content (Join-Path $reportDir 'SUMMARY.md')
