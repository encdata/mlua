function Stop-SmokeProcessTree([System.Diagnostics.Process]$Target) {
    if ($null -eq $Target) { return }

    try {
        $Target.Refresh()
        if ($Target.HasExited) { return }

        $killer = Start-Process -FilePath (Join-Path $env:SystemRoot 'System32/taskkill.exe') `
            -ArgumentList @('/PID', [string]$Target.Id, '/T', '/F') -PassThru -WindowStyle Hidden
        if (-not $killer.WaitForExit(15000)) {
            Stop-Process -Id $killer.Id -Force -ErrorAction SilentlyContinue
            Write-Warning "Timed out waiting for taskkill to stop smoke process $($Target.Id)."
        }

        $Target.Refresh()
        if (-not $Target.HasExited) {
            Stop-Process -Id $Target.Id -Force -ErrorAction SilentlyContinue
            if (-not $Target.WaitForExit(5000)) {
                Write-Warning "Smoke launcher process $($Target.Id) did not exit after forced termination."
            }
        }
    } catch {
        Stop-Process -Id $Target.Id -Force -ErrorAction SilentlyContinue
        if (-not $Target.WaitForExit(5000)) {
            Write-Warning "Could not fully stop smoke process tree $($Target.Id): $($_.Exception.Message)"
        }
    }
}
