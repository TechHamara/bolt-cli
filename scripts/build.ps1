param (
    [Alias("v")]
    [string]$Version
)

if ([string]::IsNullOrWhiteSpace($Version)) {
    Write-Host "error: Version argument is required. Use -v <version>" -ForegroundColor Red
    exit 1
}

$dateString = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$versionFile = ".\lib\version.dart"

# Write version.dart file
$content = @"
// Auto-generated; DO NOT modify
const boltVersion = '$Version';
const boltBuiltOn = '$dateString';
"@

Set-Content -Path $versionFile -Value $content
Write-Host "Generated lib/version.dart"

if (-not (Test-Path "build\bin")) {
    New-Item -ItemType Directory -Force -Path "build\bin" | Out-Null
}

# Compile Bolt executable
Write-Host "Compiling bolt.exe..."
dart compile exe -o build\bin\bolt.exe bin\bolt.dart

Write-Host "Execution permissions are not explicitly needed in Windows for .exe files."
Write-Host "Build complete!"

# create a release zip containing the executable and any ancillary files
$zipPath = "bolt-win.zip"
if (Test-Path $zipPath) { Remove-Item $zipPath }

# include the main executable and optionally a `features` directory
Write-Host "Packaging $zipPath..."
Add-Type -AssemblyName System.IO.Compression.FileSystem
$filesToZip = @()
$filesToZip += "build\bin\bolt.exe"
if (Test-Path "features") {
    # include all files under features directory
    Get-ChildItem -Path "features" -Recurse | ForEach-Object { $filesToZip += $_.FullName }
}

[IO.Compression.ZipFile]::CreateFromDirectory((Resolve-Path "build\bin").Path, $zipPath)
# if features exist, merge them manually
if (Test-Path "features") {
    $tempZip = "build\bin\temp.zip"
    if (Test-Path $tempZip) { Remove-Item $tempZip }
    [IO.Compression.ZipFile]::CreateFromDirectory((Resolve-Path "features").Path, $tempZip)
    # append entries from temp.zip into bolt-win.zip
    $zip = [IO.Compression.ZipFile]::Open($zipPath, [IO.Compression.ZipArchiveMode]::Update)
    $temp = [IO.Compression.ZipFile]::Open($tempZip, [IO.Compression.ZipArchiveMode]::Read)
    foreach ($entry in $temp.Entries) {
        $entry.CopyTo($zip)
    }
    $temp.Dispose(); $zip.Dispose();
    Remove-Item $tempZip
}
Write-Host "Created $zipPath with executable and features (if present)."
