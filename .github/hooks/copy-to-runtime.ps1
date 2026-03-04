$ErrorActionPreference = 'Stop'

function Write-Info {
    param([string]$Message)
    Write-Output "[copy-to-runtime] $Message"
}

try {
    $repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\.." )).Path
}
catch {
    Write-Info "Unable to resolve repository root from script location."
    exit 0
}

$runtimeRoot = $env:GEFILTERS_RUNTIME_ROOT
if ([string]::IsNullOrWhiteSpace($runtimeRoot)) {
    $runtimeRoot = "C:\Users\Nick\Dropbox\Runescape\IntelljProjectsRunelite\runelitedevNEW\runelite-client"
}

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Info "git is not available on PATH. Skipping runtime sync."
    exit 0
}

if (-not (Test-Path $runtimeRoot)) {
    Write-Info "Runtime root not found: $runtimeRoot"
    exit 0
}

$changed = @(git -C $repoRoot diff --name-only HEAD 2>$null)
$untracked = @(git -C $repoRoot ls-files --others --exclude-standard 2>$null)

$allCandidates = @(
    $changed + $untracked |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
    Sort-Object -Unique
)

$files = @(
    $allCandidates |
    Where-Object {
        $_ -like "src/main/java/com/salverrs/GEFilters/*" -or
        $_ -like "src/test/java/com/salverrs/GEFilters/*"
    }
)

if ($files.Count -eq 0) {
    Write-Info "No GE-Filters source/test file changes to sync."
    exit 0
}

$copied = New-Object System.Collections.Generic.List[string]
$deleted = New-Object System.Collections.Generic.List[string]
$mismatch = New-Object System.Collections.Generic.List[string]

foreach ($rel in $files) {
    $src = Join-Path $repoRoot $rel
    $dst = Join-Path $runtimeRoot $rel

    if (-not (Test-Path $src)) {
        if (Test-Path $dst) {
            Remove-Item -Path $dst -Force
            $deleted.Add($rel) | Out-Null
        }
        continue
    }

    $dstDir = Split-Path -Path $dst -Parent
    if (-not (Test-Path $dstDir)) {
        New-Item -ItemType Directory -Path $dstDir -Force | Out-Null
    }

    Copy-Item -Path $src -Destination $dst -Force
    $copied.Add($rel) | Out-Null

    if (Test-Path $dst) {
        $srcHash = (Get-FileHash -Path $src -Algorithm SHA256).Hash
        $dstHash = (Get-FileHash -Path $dst -Algorithm SHA256).Hash
        if ($srcHash -ne $dstHash) {
            $mismatch.Add($rel) | Out-Null
        }
    }
    else {
        $mismatch.Add($rel) | Out-Null
    }
}

Write-Info "Copied: $($copied.Count), Deleted in runtime: $($deleted.Count), Mismatches: $($mismatch.Count)"

if ($mismatch.Count -gt 0) {
    Write-Info "Mismatched files:"
    $mismatch | ForEach-Object { Write-Info " - $_" }
}

exit 0
