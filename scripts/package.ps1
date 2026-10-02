$ErrorActionPreference = 'Stop'
Write-Host "Packaging platform ZIPs..." -ForegroundColor Cyan

$distDir = "distribution"
$binDir = "$distDir\bin"
if (!(Test-Path $binDir)) {
    New-Item -ItemType Directory -Force -Path $binDir | Out-Null
}

# Copy launchers and jar to bin
Copy-Item "$distDir\bolt.jar" "$binDir\bolt.jar" -Force
Copy-Item "$distDir\bolt.bat" "$binDir\bolt.bat" -Force
Copy-Item "$distDir\bolt" "$binDir\bolt" -Force

# Ensure icon.png is ONLY outside libs/ (at root of distribution)
if (Test-Path "$binDir\icon.png") { Remove-Item "$binDir\icon.png" -Force }
if (Test-Path "$distDir\libs\icon.png") { Remove-Item "$distDir\libs\icon.png" -Force }
if (Test-Path "$distDir\libs\tools\icon.png") { Remove-Item "$distDir\libs\tools\icon.png" -Force }
if (Test-Path "$distDir\libs\tools\aidl\icon.png") { Remove-Item "$distDir\libs\tools\aidl\icon.png" -Force }

Add-Type -AssemblyName System.IO.Compression.FileSystem

# Clean up any old ZIP files
$oldZips = @("bolt-universal.zip", "bolt-win.zip", "bolt-linux.zip", "bolt-mac.zip", "bolt-termux.zip", "bolt.zip")
foreach ($zipName in $oldZips) {
    if (Test-Path $zipName) { Remove-Item $zipName -Force }
}

# Create staging containing bin/, libs/, and icon.png
$stagingDir = "$distDir\staging"
if (Test-Path $stagingDir) { Remove-Item $stagingDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path "$stagingDir\bin" | Out-Null
New-Item -ItemType Directory -Force -Path "$stagingDir\libs" | Out-Null

Copy-Item "$binDir\*" "$stagingDir\bin\" -Recurse -Force
Copy-Item "$distDir\libs\*" "$stagingDir\libs\" -Recurse -Force
if (Test-Path "$distDir\icon.png") {
    Copy-Item "$distDir\icon.png" "$stagingDir\icon.png" -Force
}

# Ensure staging icon is clean
if (Test-Path "$stagingDir\bin\icon.png") { Remove-Item "$stagingDir\bin\icon.png" -Force }
if (Test-Path "$stagingDir\libs\icon.png") { Remove-Item "$stagingDir\libs\icon.png" -Force }
if (Test-Path "$stagingDir\libs\tools\icon.png") { Remove-Item "$stagingDir\libs\tools\icon.png" -Force }
if (Test-Path "$stagingDir\libs\tools\aidl\icon.png") { Remove-Item "$stagingDir\libs\tools\aidl\icon.png" -Force }

Write-Host "Compressing universal release package bolt.zip (Fresh)..." -ForegroundColor Yellow
$universalZip = "bolt.zip"
[IO.Compression.ZipFile]::CreateFromDirectory((Resolve-Path $stagingDir).Path, $universalZip)
Write-Host "Created universal $universalZip successfully!" -ForegroundColor Green

Remove-Item $stagingDir -Recurse -Force

# Create lightweight InPlace update package update.zip (contains bin/ ONLY, NO icon.png)
$updateStaging = "$distDir\update_staging"
if (Test-Path $updateStaging) { Remove-Item $updateStaging -Recurse -Force }
New-Item -ItemType Directory -Force -Path "$updateStaging\bin" | Out-Null

Copy-Item "$binDir\*" "$updateStaging\bin\" -Recurse -Force

# Ensure icon.png is NEVER inside update.zip
if (Test-Path "$updateStaging\icon.png") { Remove-Item "$updateStaging\icon.png" -Force }
if (Test-Path "$updateStaging\bin\icon.png") { Remove-Item "$updateStaging\bin\icon.png" -Force }

Write-Host "Compressing lightweight update package update.zip (InPlace, bin only without icon.png)..." -ForegroundColor Yellow
$updateZip = "update.zip"
if (Test-Path $updateZip) { Remove-Item $updateZip -Force }
[IO.Compression.ZipFile]::CreateFromDirectory((Resolve-Path $updateStaging).Path, $updateZip)
Write-Host "Created lightweight $updateZip successfully (without icon.png)!" -ForegroundColor Green

Remove-Item $updateStaging -Recurse -Force
Write-Host "All release packages (bolt.zip & update.zip) packaged successfully!" -ForegroundColor Green
