# Strips all comments from source files. config.yml keeps its comments
# (end-user documentation). Drives scripts/scrub-comments.js per file.

param(
    [string]$Root = (Resolve-Path "$PSScriptRoot\.."),
    [string[]]$IncludeExtensions = @('*.java', '*.js', '*.yml', '*.yaml', '*.json', '*.md', '*.xml'),
    [string[]]$ExcludeDirs = @('node_modules', 'target', '.git'),
    [string[]]$ExcludeFiles = @('package-lock.json'),
    [string[]]$PreserveCommentPaths = @(
        (Join-Path $PSScriptRoot '..\plugin\src\main\resources\config.yml')
    )
)

$ErrorActionPreference = 'Stop'

$preserve = @{}
foreach ($p in $PreserveCommentPaths) {
    $resolved = (Resolve-Path $p -ErrorAction SilentlyContinue)
    if ($resolved) { $preserve[$resolved.Path.ToLower()] = $true }
}

$allFiles = @()
foreach ($ext in $IncludeExtensions) {
    $allFiles += @(Get-ChildItem -Path $Root -Recurse -Force -Include $ext -ErrorAction SilentlyContinue)
}

$scrubber = Join-Path $PSScriptRoot 'scrub-comments.js'
$nodeModules = Join-Path $Root 'cli\node_modules'
$nodeExe = (Get-Command node.exe -ErrorAction SilentlyContinue).Source
if (-not $nodeExe) {
    $nodeExe = Join-Path $env:ProgramFiles 'nodejs\node.exe'
}
$env:NODE_PATH = $nodeModules

$scrubbed = 0
$failed = 0
foreach ($f in $allFiles) {
    if ($f.PSIsContainer) { continue }
    $rel = $f.FullName
    $base = $rel.ToLower()
    $skip = $false
    foreach ($d in $ExcludeDirs) {
        if ($base -like ("*\" + $d.ToLower() + "\*")) { $skip = $true; break }
    }
    if ($skip) { continue }
    foreach ($ef in $ExcludeFiles) {
        if ($f.Name -eq $ef) { $skip = $true; break }
    }
    if ($skip) { continue }

    $preserveThis = $preserve.ContainsKey($base)
    $ext = $f.Extension.ToLower()
    $nodeArgs = @($ext, $rel)
    if ($preserveThis) { $nodeArgs += 'preserve' }

    $proc = Start-Process -FilePath $nodeExe -ArgumentList (@($scrubber) + $nodeArgs) -WorkingDirectory $nodeModules -NoNewWindow -PassThru -Wait -RedirectStandardOutput 'stdout.txt' -RedirectStandardError 'stderr.txt'
    if ($proc.ExitCode -ne 0) {
        Write-Host "FAILED on $rel (exit=$($proc.ExitCode))"
        if (Test-Path 'stderr.txt') { Get-Content 'stderr.txt' | Select-Object -First 10 }
        $failed++
        continue
    }
    if ((Test-Path 'stdout.txt') -and (Get-Content 'stdout.txt') -match '^SCRUBBED') {
        $scrubbed++
    }
}
Remove-Item 'stdout.txt','stderr.txt' -ErrorAction SilentlyContinue

Write-Host "Scrubbed: $scrubbed files"
Write-Host "Failed: $failed files"