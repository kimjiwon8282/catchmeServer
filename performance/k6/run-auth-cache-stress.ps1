param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("perf-cache", "perf-nocache")]
    [string]$Profile,

    [int[]]$Rates = @(100, 200, 400, 600, 800),

    [string]$Duration = "1m",

    [int]$Runs = 1,

    [int]$PreAllocatedVUs = 50,

    [int]$MaxVUs = 300,

    [int]$WarmupRequests = 20,

    [int]$CooldownSeconds = 10,

    [switch]$StopOnFailure
)

$ErrorActionPreference = "Stop"

function Require-Environment {
    if ([string]::IsNullOrWhiteSpace($env:BASE_URL)) {
        throw "BASE_URL environment variable is required. Example: `$env:BASE_URL=`"http://localhost:8080`""
    }

    $hasAccessToken = -not [string]::IsNullOrWhiteSpace($env:ACCESS_TOKEN)
    $hasCredentials = -not [string]::IsNullOrWhiteSpace($env:EMAIL) -and -not [string]::IsNullOrWhiteSpace($env:PASSWORD)

    if (-not $hasAccessToken -and -not $hasCredentials) {
        throw "Set ACCESS_TOKEN or both EMAIL and PASSWORD before running the stress test."
    }
}

function Set-TestEnvironment {
    param(
        [int]$Rate,
        [string]$ResultLabel
    )

    $env:RATE = [string]$Rate
    $env:DURATION = $Duration
    $env:PRE_ALLOCATED_VUS = [string]$PreAllocatedVUs
    $env:MAX_VUS = [string]$MaxVUs
    $env:WARMUP_REQUESTS = [string]$WarmupRequests
    $env:PROFILE = $Profile
    $env:RESULT_LABEL = $ResultLabel
}

Require-Environment

$scriptPath = Join-Path $PSScriptRoot "auth-cache-load.js"
$hadFailure = $false
$executions = @()

foreach ($run in 1..$Runs) {
    foreach ($rate in $Rates) {
        $executions += [pscustomobject]@{
            Run = $run
            Rate = $rate
            Label = "{0}-stress-{1}-run{2}" -f $Profile, $rate, $run
        }
    }
}

for ($index = 0; $index -lt $executions.Count; $index += 1) {
    $execution = $executions[$index]
    Set-TestEnvironment -Rate $execution.Rate -ResultLabel $execution.Label

    Write-Host ("[{0}] profile={1} rate={2} duration={3} run={4}" -f `
            $execution.Label, $Profile, $execution.Rate, $Duration, $execution.Run)

    & k6 run $scriptPath
    $exitCode = $LASTEXITCODE

    if ($exitCode -eq 0) {
        Write-Host ("[{0}] PASS: k6 exit code 0" -f $execution.Label)
    } else {
        $hadFailure = $true
        Write-Warning ("[{0}] FAIL: k6 exit code {1}. Threshold failed or k6 execution failed." -f $execution.Label, $exitCode)

        if ($StopOnFailure) {
            exit $exitCode
        }
    }

    if ($CooldownSeconds -gt 0 -and $index -lt ($executions.Count - 1)) {
        Write-Host ("Waiting {0}s before next stress step..." -f $CooldownSeconds)
        Start-Sleep -Seconds $CooldownSeconds
    }
}

if ($hadFailure) {
    exit 1
}

exit 0
