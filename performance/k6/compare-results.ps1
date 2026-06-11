param(
    [Parameter(Mandatory = $true)]
    [string]$NoCache,

    [Parameter(Mandatory = $true)]
    [string]$Cache
)

function Read-Result {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Result file not found: $Path"
    }

    Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
}

function Metric {
    param($Result, [string]$Name)

    if ($null -eq $Result.metrics -or -not ($Result.metrics.PSObject.Properties.Name -contains $Name)) {
        return $null
    }

    return [double]$Result.metrics.$Name
}

function Format-Number {
    param($Value)

    if ($null -eq $Value) {
        return "n/a"
    }

    return "{0:N2}" -f [double]$Value
}

function Percent-ImproveLowerIsBetter {
    param($Before, $After)

    if ($null -eq $Before -or $null -eq $After -or [double]$Before -eq 0) {
        return "n/a"
    }

    return "{0:N2}%" -f ((([double]$Before - [double]$After) / [double]$Before) * 100)
}

function Percent-ImproveHigherIsBetter {
    param($Before, $After)

    if ($null -eq $Before -or $null -eq $After -or [double]$Before -eq 0) {
        return "n/a"
    }

    return "{0:N2}%" -f ((([double]$After - [double]$Before) / [double]$Before) * 100)
}

$noCacheResult = Read-Result -Path $NoCache
$cacheResult = Read-Result -Path $Cache

$rows = @(
    [pscustomobject]@{
        Label = "DB 조회"
        Avg = Metric $noCacheResult "avgMs"
        P95 = Metric $noCacheResult "p95Ms"
        P99 = Metric $noCacheResult "p99Ms"
        Max = Metric $noCacheResult "maxMs"
        Rps = Metric $noCacheResult "rps"
        ErrorRate = Metric $noCacheResult "errorRate"
        Dropped = Metric $noCacheResult "droppedIterations"
    },
    [pscustomobject]@{
        Label = "Redis Hit"
        Avg = Metric $cacheResult "avgMs"
        P95 = Metric $cacheResult "p95Ms"
        P99 = Metric $cacheResult "p99Ms"
        Max = Metric $cacheResult "maxMs"
        Rps = Metric $cacheResult "rps"
        ErrorRate = Metric $cacheResult "errorRate"
        Dropped = Metric $cacheResult "droppedIterations"
    }
)

Write-Output "| 구분 | 평균(ms) | p95(ms) | p99(ms) | 최대(ms) | RPS | 오류율 | Dropped |"
Write-Output "|---|---:|---:|---:|---:|---:|---:|---:|"
foreach ($row in $rows) {
    Write-Output ("| {0} | {1} | {2} | {3} | {4} | {5} | {6} | {7} |" -f `
            $row.Label, `
            (Format-Number $row.Avg), `
            (Format-Number $row.P95), `
            (Format-Number $row.P99), `
            (Format-Number $row.Max), `
            (Format-Number $row.Rps), `
            (Format-Number $row.ErrorRate), `
            (Format-Number $row.Dropped))
}

Write-Output ""
Write-Output ("평균 응답 시간 개선율: {0}" -f (Percent-ImproveLowerIsBetter (Metric $noCacheResult "avgMs") (Metric $cacheResult "avgMs")))
Write-Output ("p95 개선율: {0}" -f (Percent-ImproveLowerIsBetter (Metric $noCacheResult "p95Ms") (Metric $cacheResult "p95Ms")))
Write-Output ("처리량 증가율: {0}" -f (Percent-ImproveHigherIsBetter (Metric $noCacheResult "rps") (Metric $cacheResult "rps")))
