#!/usr/bin/env pwsh

param(
    [string]$InstallPath  # optional override for the installation directory
)

$ErrorActionPreference = 'Stop'

if ($env:OS -ne "Windows_NT") {
  Write-Error "This script is only for Windows"
  Exit 1
}

# determine the target installation directory
if ($InstallPath) {
    # explicit parameter takes precedence
    $BoltHome = $InstallPath
}
elseif ($null -ne $env:BOLT_HOME) {
  $BoltHome = $env:BOLT_HOME
}
else {
  if (Get-Command "bolt.exe" -ErrorAction SilentlyContinue) {
    $BoltHome = (Get-Item (Get-Command "bolt.exe").Path).Directory.Parent.FullName
  }
  else {
    $BoltHome = "$Home\.bolt"
    if (!(Test-Path $BoltHome)) {
      New-Item $BoltHome -ItemType Directory | Out-Null
    }
  }
}

# choose the correct binary; currently only Windows ZIP is published, but future versions
# may include architecture/runtime-specific bundles.  The URL could be parameterized if
# needed.
$ZipUrl = "https://github.com/TechHamara/bolt-cli/releases/latest/download/bolt-win.zip"
$ZipLocation = "$BoltHome\bolt-win.zip"

# GitHub requires TLS 1.2
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# Download $ZipUrl to $ZipLocation with progress
Write-Host "Downloading Bolt CLI from $ZipUrl..."
$progressPreference = 'Continue'
try {
  $ProgressPreference = 'SilentlyContinue'
  Invoke-WebRequest -OutFile $ZipLocation $ZipUrl -UseBasicParsing
  Write-Host "Download completed!" -ForegroundColor Green
} catch {
  Write-Error "Download failed: $_"
  exit 1
}

# Extract it
Write-Host "Extracting Bolt CLI files..."
try {
  if (Get-Command Expand-Archive -ErrorAction SilentlyContinue) {
    Expand-Archive $ZipLocation -DestinationPath "$BoltHome" -Force
  }
  else {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::ExtractToDirectory($ZipLocation, $BoltHome)
  }
  Write-Host "Extraction completed!" -ForegroundColor Green
  Remove-Item $ZipLocation
} catch {
  Write-Error "Extraction failed: $_"
  exit 1
}

Write-Output "Successfully downloaded the Bolt CLI binary at $BoltHome\bin\bolt.exe"

# Prompt user if they want to download dev dependencies now
$Yes = New-Object System.Management.Automation.Host.ChoiceDescription "&Yes"
$No = New-Object System.Management.Automation.Host.ChoiceDescription "&No"
$Options = [System.Management.Automation.Host.ChoiceDescription[]]($Yes, $No)

$Title = "Now, proceeding to download necessary Java libraries (approx size: 170 MB)."
$Message = "Do you want to continue?"
$Result = $host.ui.PromptForChoice($Title, $Message, $Options, 0)
if ($Result -eq 0) {
  $BinDir = "$BoltHome\bin"
  Start-Process -NoNewWindow -FilePath "$BinDir\bolt.exe" -ArgumentList "deps", "sync", "--dev-deps", "--no-logo" -Wait 
}

# Update PATH
$User = [EnvironmentVariableTarget]::User
$Path = [Environment]::GetEnvironmentVariable('Path', $User)
if (!(";$Path;".ToLower() -like "*;$BinDir;*".ToLower())) {
  [Environment]::SetEnvironmentVariable('Path', "$Path;$BinDir", $User)
  $Env:Path += ";$BinDir"
}

if ($Result -eq 0) {
  Write-Output "`nSuccess! Installed Bolt at $BinDir\bolt.exe!"
  Write-Output "Run ``bolt --help`` to get started."
}
else {
  Write-Output "`nBolt has been partially installed at $BinDir\bolt.exe!"
  Write-Output "Please run ``bolt deps sync --dev-deps`` to download necessary Java libraries."
}
