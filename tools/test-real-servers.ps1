[CmdletBinding()]
param(
    [ValidateRange(60, 1800)] [int]$TimeoutSeconds = 300,
    [switch]$KeepServers
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$testRoot = Join-Path $root 'build\real-server-smoke'
$logRoot = Join-Path $testRoot 'logs'
$serverRoot = Join-Path $testRoot 'servers'
$cacheRoot = Join-Path $testRoot 'cache'
$worldName = 'Smoke'
New-Item -ItemType Directory -Force -Path $logRoot, $serverRoot, $cacheRoot | Out-Null

$targets = @(
    @{ loader='fabric'; minecraft='1.20.1'; loaderVersion='0.16.10'; api='0.92.9+1.20.1'; java=17; port=25565 },
    @{ loader='forge'; minecraft='1.20.1'; loaderVersion='47.2.0'; java=17; port=25566 },
    @{ loader='fabric'; minecraft='1.21.1'; loaderVersion='0.16.14'; api='0.116.13+1.21.1'; java=21; port=25567 },
    @{ loader='forge'; minecraft='1.21.1'; loaderVersion='52.1.14'; java=21; port=25568 },
    @{ loader='neoforge'; minecraft='1.21.1'; loaderVersion='21.1.228'; java=21; port=25569 },
    @{ loader='fabric'; minecraft='26.1'; loaderVersion='0.19.3'; api='0.154.2+26.1.2'; java=25; port=25570 },
    @{ loader='neoforge'; minecraft='26.1'; loaderVersion='26.1.2.6-beta'; java=25; port=25571 },
    @{ loader='fabric'; minecraft='26.1.1'; loaderVersion='0.19.3'; api='0.154.2+26.1.2'; java=25; port=25572 },
    @{ loader='fabric'; minecraft='26.1.2'; loaderVersion='0.19.3'; api='0.154.2+26.1.2'; java=25; port=25573 },
    @{ loader='neoforge'; minecraft='26.1.2'; loaderVersion='26.1.2.6-beta'; java=25; port=25574 }
)

function Download([string]$Url, [string]$Path) {
    if (-not (Test-Path $Path)) {
        Write-Host "Downloading $Url" -ForegroundColor DarkCyan
        Invoke-WebRequest -Uri $Url -OutFile $Path
    }
}

function Get-MinecraftServerUrl([string]$Version) {
    $manifest = Invoke-RestMethod 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
    $entry = $manifest.versions | Where-Object id -eq $Version | Select-Object -First 1
    if (-not $entry) { throw "Minecraft version $Version is not in the official version manifest." }
    $detail = Invoke-RestMethod $entry.url
    return $detail.downloads.server.url
}

function Get-Java([int]$Version) {
    $roots = @(
        "$env:JAVA_HOME",
        "$env:ProgramFiles\Eclipse Adoptium",
        "$env:ProgramFiles\Java"
    ) | Where-Object { $_ -and (Test-Path $_) }
    $java = Get-ChildItem $roots -Recurse -Filter java.exe -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match "jdk-$Version|jdk$Version|$Version" } |
        Select-Object -First 1 -ExpandProperty FullName
    if (-not $java) { throw "Java $Version was not found for the real server test." }
    return $java
}

$built = Join-Path $root 'build\loader-jars'
$prepared = @()
foreach ($target in $targets) {
    $id = "$($target.loader)-$($target.minecraft.Replace('.', '_'))"
    $dir = Join-Path $serverRoot $id
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    'eula=true' | Set-Content (Join-Path $dir 'eula.txt') -Encoding ASCII
    $mod = Join-Path $built "mlua-$($target.loader)-$($target.minecraft)-0.3.1.jar"
    if (-not (Test-Path $mod)) { throw "Missing built artifact: $mod" }
    New-Item -ItemType Directory -Force -Path (Join-Path $dir 'mods') | Out-Null
    Get-ChildItem (Join-Path $dir 'mods') -Filter 'mlua-*.jar' -File -ErrorAction SilentlyContinue |
        Remove-Item -Force
    if (Test-Path (Join-Path $dir '.mlua')) {
        Remove-Item (Join-Path $dir '.mlua') -Recurse -Force
    }
    Copy-Item $mod (Join-Path $dir 'mods')
    @(
        'server-port=' + $target.port
        'server-ip='
        'online-mode=false'
    ) | Set-Content (Join-Path $dir 'server.properties') -Encoding ASCII

    $java = Get-Java $target.java
    $serverUrl = $null
    $serverJar = Join-Path $dir "minecraft-server-$($target.minecraft).jar"
    $cachedServerJar = Join-Path $cacheRoot "minecraft-server-$($target.minecraft).jar"
    $preparedMarker = Join-Path $dir '.mlua-server-prepared'
    $expectedLauncher = if ($target.loader -eq 'fabric') {
        Join-Path $dir 'fabric-server-launch.jar'
    } else {
        Join-Path $dir 'run.bat'
    }
    $isPrepared = (Test-Path $preparedMarker) -and (Test-Path $expectedLauncher)

    if ($isPrepared) {
        Write-Host "[$id] reusing cached server installation" -ForegroundColor DarkGreen
    } elseif ($target.loader -eq 'fabric') {
        $serverUrl = Get-MinecraftServerUrl $target.minecraft
        Download $serverUrl $cachedServerJar
        if (-not (Test-Path $serverJar)) { Copy-Item $cachedServerJar $serverJar }
        $installer = Join-Path $testRoot 'fabric-installer.jar'
        Download 'https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.3/fabric-installer-1.0.3.jar' $installer
        & $java '-jar' $installer 'server' '-mcversion' $target.minecraft '-loader' $target.loaderVersion '-downloadMinecraft' '-dir' $dir
        $apiUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$($target.api)/fabric-api-$($target.api).jar"
        Download $apiUrl (Join-Path $dir 'mods/fabric-api.jar')
        $launcher = Join-Path $dir 'fabric-server-launch.jar'
        $args = @('nogui')
    } elseif ($target.loader -eq 'forge') {
        $serverUrl = Get-MinecraftServerUrl $target.minecraft
        Download $serverUrl $cachedServerJar
        if (-not (Test-Path $serverJar)) { Copy-Item $cachedServerJar $serverJar }
        $installer = Join-Path $testRoot "forge-$($target.minecraft)-installer.jar"
        Download "https://maven.minecraftforge.net/net/minecraftforge/forge/$($target.minecraft)-$($target.loaderVersion)/forge-$($target.minecraft)-$($target.loaderVersion)-installer.jar" $installer
        & $java '-jar' $installer '--installServer' $dir
        $launcher = Join-Path $dir 'run.bat'
        $args = @('nogui')
    } else {
        $serverUrl = Get-MinecraftServerUrl $target.minecraft
        Download $serverUrl $cachedServerJar
        if (-not (Test-Path $serverJar)) { Copy-Item $cachedServerJar $serverJar }
        $installer = Join-Path $testRoot "neoforge-$($target.loaderVersion)-installer.jar"
        Download "https://maven.neoforged.net/releases/net/neoforged/neoforge/$($target.loaderVersion)/neoforge-$($target.loaderVersion)-installer.jar" $installer
        & $java '-jar' $installer '--installServer' $dir
        $launcher = Join-Path $dir 'run.bat'
        $args = @('nogui')
    }
    if ($isPrepared) {
        $launcher = $expectedLauncher
        $args = @('nogui')
    }
    if (-not (Test-Path $launcher)) { throw "[$id] real server launcher was not created: $launcher" }
    'prepared' | Set-Content $preparedMarker -Encoding ASCII
    $javaHome = Split-Path (Split-Path $java -Parent) -Parent
    $log = if ($target.loader -eq 'fabric') { Join-Path $dir 'logs\latest.log' } else { Join-Path $logRoot "$id.log" }
    $prepared += [pscustomobject]@{ Id=$id; Dir=$dir; Launcher=$launcher; Args=$args; Java=$java; JavaHome=$javaHome; Log=$log; OutputLog=(Join-Path $logRoot "$id.log") }
}

$running = foreach ($server in $prepared) {
    Write-Host "[$($server.Id)] launching real server" -ForegroundColor Cyan
    if ($server.Launcher.EndsWith('.bat')) {
        $launcher = $server.Launcher.Replace('"', '""')
        $log = $server.Log.Replace('"', '""')
        $javaHome = $server.JavaHome.Replace('"', '""')
        $batCommand = "set `"JAVA_HOME=$javaHome`" && set `"PATH=$javaHome\bin;%PATH%`" && call `"$launcher`" $($server.Args -join ' ') > `"$log`" 2>&1"
        $p = Start-Process cmd.exe -WindowStyle Hidden -WorkingDirectory $server.Dir `
            -ArgumentList @('/d', '/s', '/c', $batCommand) -PassThru
    } else {
        $javaArgs = @('-jar', $server.Launcher) + @($server.Args)
        $p = Start-Process $server.Java -WindowStyle Hidden -WorkingDirectory $server.Dir `
            -ArgumentList $javaArgs `
            -RedirectStandardOutput $server.OutputLog `
            -RedirectStandardError (Join-Path $logRoot "$($server.Id).err.log") -PassThru
    }
    [pscustomobject]@{ Server=$server; Process=$p; Started=Get-Date }
}

$results = foreach ($item in $running) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $loaded = $false
    while (-not $item.Process.HasExited -and (Get-Date) -lt $deadline) {
        $text = if (Test-Path $item.Server.Log) { Get-Content $item.Server.Log -Raw } else { '' }
        if ($text -match 'Done \(') { $loaded = $true; break }
        Start-Sleep 2
    }
    $world = Join-Path $item.Server.Dir 'world'
    $status = if ($loaded -and (Test-Path (Join-Path $world 'level.dat'))) { 'world-created' } else { 'failed' }
    if (-not $KeepServers) { Stop-Process -Id $item.Process.Id -Force -ErrorAction SilentlyContinue }
    [pscustomobject]@{ Id=$item.Server.Id; Status=$status; Log=$item.Server.Log; World=$world }
}
$results | Format-Table -AutoSize
$results | ConvertTo-Json -Depth 3 | Set-Content (Join-Path $logRoot 'summary.json')
if (@($results | Where-Object Status -ne 'world-created').Count) { exit 1 }
Write-Host 'All real server smoke tests created worlds successfully.' -ForegroundColor Green
