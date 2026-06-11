param(
    [string]$ResultsDirectory = ".\performance\k6\results"
)
[Console]::OutputEncoding =
    [System.Text.UTF8Encoding]::new()

$OutputEncoding =
    [Console]::OutputEncoding

$ErrorActionPreference = "Stop"

function U {
    param([string]$Escaped)

    return [regex]::Replace($Escaped, "\\u([0-9a-fA-F]{4})", {
            param($Match)
            return [string][char][Convert]::ToInt32($Match.Groups[1].Value, 16)
        })
}

function Read-JsonResult {
    param([string]$Path)

    Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
}

function Metric {
    param($Result, [string]$Name)

    if ($null -eq $Result.metrics -or -not ($Result.metrics.PSObject.Properties.Name -contains $Name)) {
        return $null
    }

    return $Result.metrics.$Name
}

function Median {
    param([object[]]$Values)

    $numbers = @($Values | Where-Object { $null -ne $_ } | ForEach-Object { [double]$_ } | Sort-Object)

    if ($numbers.Count -eq 0) {
        return $null
    }

    $middle = [int][Math]::Floor($numbers.Count / 2)

    if ($numbers.Count % 2 -eq 1) {
        return $numbers[$middle]
    }

    return ($numbers[$middle - 1] + $numbers[$middle]) / 2
}

function Format-Number {
    param($Value, [int]$Digits = 2)

    if ($null -eq $Value) {
        return "n/a"
    }

    return ("{0:N$Digits}" -f [double]$Value)
}

function Format-Percent {
    param($Value)

    if ($null -eq $Value) {
        return "n/a"
    }

    return ("{0:N2}%" -f ([double]$Value * 100))
}

function Is-Stable {
    param($Row)

    return (
        $null -ne $Row.ErrorRate -and $Row.ErrorRate -lt 0.01 -and
        $null -ne $Row.CheckRate -and $Row.CheckRate -gt 0.99 -and
        $null -ne $Row.DroppedIterations -and $Row.DroppedIterations -eq 0 -and
        $null -ne $Row.P95Ms -and $Row.P95Ms -lt 200 -and
        $null -ne $Row.P99Ms -and $Row.P99Ms -lt 500 -and
        $Row.ThresholdsPassed
    )
}

if (-not (Test-Path -LiteralPath $ResultsDirectory)) {
    throw "Results directory not found: $ResultsDirectory"
}

$files = @(
    Get-ChildItem -LiteralPath $ResultsDirectory -Filter "perf-nocache-stress-*.json" -File
    Get-ChildItem -LiteralPath $ResultsDirectory -Filter "perf-cache-stress-*.json" -File
)

if ($files.Count -eq 0) {
    throw "No stress result files found in $ResultsDirectory"
}

$results = foreach ($file in $files) {
    $result = Read-JsonResult -Path $file.FullName
    $profile = [string]$result.profile

    if ([string]::IsNullOrWhiteSpace($profile)) {
        if ($file.Name -like "perf-nocache-stress-*") {
            $profile = "perf-nocache"
        } elseif ($file.Name -like "perf-cache-stress-*") {
            $profile = "perf-cache"
        }
    }

    [pscustomobject]@{
        File = $file.Name
        Profile = $profile
        Rate = [int]$result.settings.rate
        AvgMs = [double](Metric $result "avgMs")
        P95Ms = [double](Metric $result "p95Ms")
        P99Ms = [double](Metric $result "p99Ms")
        MaxMs = [double](Metric $result "maxMs")
        Rps = [double](Metric $result "rps")
        ErrorRate = [double](Metric $result "errorRate")
        CheckRate = [double](Metric $result "checkRate")
        DroppedIterations = [double](Metric $result "droppedIterations")
        ThresholdsPassed = [bool](Metric $result "thresholdsPassed")
    }
}

$rows = foreach ($group in ($results | Group-Object Profile, Rate)) {
    $items = @($group.Group)
    $thresholdsPassed = @($items | Where-Object { -not $_.ThresholdsPassed }).Count -eq 0

    $row = [pscustomobject]@{
        Profile = $items[0].Profile
        Rate = $items[0].Rate
        Rps = Median ($items | ForEach-Object { $_.Rps })
        AvgMs = Median ($items | ForEach-Object { $_.AvgMs })
        P95Ms = Median ($items | ForEach-Object { $_.P95Ms })
        P99Ms = Median ($items | ForEach-Object { $_.P99Ms })
        MaxMs = Median ($items | ForEach-Object { $_.MaxMs })
        ErrorRate = Median ($items | ForEach-Object { $_.ErrorRate })
        CheckRate = Median ($items | ForEach-Object { $_.CheckRate })
        DroppedIterations = Median ($items | ForEach-Object { $_.DroppedIterations })
        ThresholdsPassed = $thresholdsPassed
        Runs = $items.Count
    }

    $row | Add-Member -NotePropertyName Stable -NotePropertyValue (Is-Stable $row)
    $row
}

$rows = @($rows | Sort-Object Profile, Rate)

$rateHeader = U "\uC694\uCCAD\uB960"
$actualRpsHeader = U "\uC2E4\uC81C RPS"
$avgHeader = U "\uD3C9\uADE0"
$maxHeader = U "\uCD5C\uB300"
$errorRateHeader = U "\uC624\uB958\uC728"
$stableHeader = U "\uC548\uC815 \uC5EC\uBD80"

Write-Output "| Profile | $rateHeader | $actualRpsHeader | $avgHeader | p95 | p99 | $maxHeader | $errorRateHeader | Dropped | Threshold | $stableHeader |"
Write-Output "|---|---:|---:|---:|---:|---:|---:|---:|---:|---|---|"

foreach ($row in $rows) {
    $thresholdLabel = if ($row.ThresholdsPassed) { "PASS" } else { "FAIL" }
    $stableLabel = if ($row.Stable) { "PASS" } else { "FAIL" }

    Write-Output ("| {0} | {1} | {2} | {3} | {4} | {5} | {6} | {7} | {8} | {9} | {10} |" -f `
            $row.Profile, `
            $row.Rate, `
            (Format-Number $row.Rps), `
            (Format-Number $row.AvgMs), `
            (Format-Number $row.P95Ms), `
            (Format-Number $row.P99Ms), `
            (Format-Number $row.MaxMs), `
            (Format-Percent $row.ErrorRate), `
            (Format-Number $row.DroppedIterations 0), `
            $thresholdLabel, `
            $stableLabel)
}

Write-Output ""

$maxStableText = U "\uCD5C\uB300 \uC548\uC815 \uC694\uCCAD\uB960"
$minFailText = U "\uD604\uC7AC \uCD5C\uC18C \uC694\uCCAD\uB960\uC5D0\uC11C\uB3C4 \uC548\uC815 \uAE30\uC900 \uBBF8\uCDA9\uC871"
$lowerRetestText = U "\uB354 \uB0AE\uC740 \uC694\uCCAD\uB960\uBD80\uD130 \uC7AC\uCE21\uC815 \uD544\uC694"
$upperUnknownText = U "\uD604\uC7AC \uD14C\uC2A4\uD2B8 \uBC94\uC704\uC5D0\uC11C\uB294 \uC0C1\uD55C \uBBF8\uD655\uC778"
$nextStepText = U "\uB2E4\uC74C \uC694\uCCAD\uB960 \uB2E8\uACC4 \uCD94\uAC00 \uD544\uC694"

foreach ($profileGroup in ($rows | Group-Object Profile | Sort-Object Name)) {
    $profileRows = @($profileGroup.Group | Sort-Object Rate)
    $stableRows = @($profileRows | Where-Object { $_.Stable })

    if ($stableRows.Count -eq 0) {
        Write-Output ("{0} {1}: {2}" -f $profileGroup.Name, $maxStableText, $minFailText)
        Write-Output $lowerRetestText
        continue
    }

    $maxStableRate = ($stableRows | Measure-Object -Property Rate -Maximum).Maximum
    Write-Output ("{0} {1}: {2} RPS" -f $profileGroup.Name, $maxStableText, $maxStableRate)

    $maxMeasuredRate = ($profileRows | Measure-Object -Property Rate -Maximum).Maximum

    if ($maxStableRate -eq $maxMeasuredRate) {
        Write-Output $upperUnknownText
        Write-Output $nextStepText
    }
}
