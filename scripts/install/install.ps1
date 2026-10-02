#!/usr/bin/env pwsh

param(
  [string]$InstallPath, # optional override for the installation directory
  [switch]$SkipJava,    # skip checking and downloading Java JDK/JRE
  [switch]$NoJdk,       # alias for -SkipJava
  [switch]$Help         # display help message
)

if ($Help) {
  Write-Host "Usage: .\install.ps1 [-InstallPath <path>] [-SkipJava] [-NoJdk] [-Help]"
  Write-Host ""
  Write-Host "Options:"
  Write-Host "  -InstallPath <path>  Target directory (default: %LOCALAPPDATA%\Bolt)"
  Write-Host "  -SkipJava            Skip checking and downloading Java runtime"
  Write-Host "  -NoJdk               Alias for -SkipJava"
  Write-Host "  -Help                Display this help message"
  return
}

if ($NoJdk) {
  $SkipJava = $true
}

$ErrorActionPreference = 'Stop'

if ($env:OS -ne "Windows_NT") {
  Write-Error "This script is only for Windows"
  return
}

# determine the target installation directory
if ($InstallPath) {
  $BoltHome = (Resolve-Path -Path $InstallPath -ErrorAction SilentlyContinue)?.Path
  if (!$BoltHome) {
    $BoltHome = [System.IO.Path]::GetFullPath($InstallPath)
  }
}
elseif ($null -ne $env:BOLT_HOME -and (Test-Path $env:BOLT_HOME)) {
  $BoltHome = $env:BOLT_HOME
}
elseif (Get-Command "bolt.bat" -ErrorAction SilentlyContinue) {
  $BoltHome = (Get-Item (Get-Command "bolt.bat").Path).Directory.Parent.FullName
}
else {
  $LocalApp = if ($env:LOCALAPPDATA) { $env:LOCALAPPDATA } else { "$Home\AppData\Local" }
  $BoltHome = "$LocalApp\Bolt"
}

if (!(Test-Path $BoltHome)) {
  New-Item $BoltHome -ItemType Directory -Force | Out-Null
}

$BinDir = "$BoltHome\bin"

# Check if bolt is already installed
if (Test-Path "$BinDir\bolt.bat") {
  $boltVersion = & "$BinDir\bolt.bat" --version
  Write-Host "`nBolt CLI is already installed!" -ForegroundColor Green
  Write-Host "Version: $boltVersion" -ForegroundColor Cyan
  Write-Host "If you want to reinstall or upgrade, please use 'bolt upgrade' or remove the $BoltHome directory first.`n"
  return
}

function Invoke-DownloadWithProgress {
  param(
    [string]$Url,
    [string]$Destination,
    [string]$PrefixText
  )
  $Client = New-Object System.Net.Http.HttpClient
  $Response = $Client.GetAsync($Url, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
  $TotalBytes = $Response.Content.Headers.ContentLength
  $Stream = $Response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
  $FileStream = [System.IO.File]::Create($Destination)
  $Buffer = New-Object byte[] 65536
  $ReceivedBytes = 0
  $Stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
  $LastReportTime = 0
  $LastReportBytes = 0

  while (($read = $Stream.Read($Buffer, 0, $Buffer.Length)) -gt 0) {
    $FileStream.Write($Buffer, 0, $read)
    $ReceivedBytes += $read
    $Now = $Stopwatch.ElapsedMilliseconds
    if (($Now - $LastReportTime) -ge 200 -or $ReceivedBytes -eq $TotalBytes) {
      $TimeSec = ($Now - $LastReportTime) / 1000.0
      $BytesSince = $ReceivedBytes - $LastReportBytes
      $Speed = if ($TimeSec -gt 0) { ($BytesSince / $TimeSec) / (1024 * 1024) } else { 0 }
      $Pct = if ($TotalBytes -gt 0) { ($ReceivedBytes / $TotalBytes) * 100 } else { 0 }
      $RxMB = ($ReceivedBytes / 1MB).ToString("0.00")
      $TotalMB = if ($TotalBytes) { ($TotalBytes / 1MB).ToString("0.00") } else { "???" }
      $SpeedStr = $Speed.ToString("0.00")
      $PctStr = $Pct.ToString("0.00").PadLeft(6)
            
      $ProgressMsg = "`r$($PrefixText): $PctStr% ($RxMB MB / $TotalMB MB) | $SpeedStr MB/s |"
      [Console]::Write($ProgressMsg)
      $LastReportTime = $Now
      $LastReportBytes = $ReceivedBytes
    }
  }
  $FileStream.Close()
  $Stream.Close()
  Write-Host "`nDownload complete!" -ForegroundColor Green
}

# Download universal release
$ZipUrl = "https://github.com/TechHamara/bolt-cli/releases/latest/download/bolt.zip"
$ZipLocation = "$BoltHome\bolt.zip"

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

Write-Host "Starting Universal Bolt CLI download..." -ForegroundColor Cyan
Invoke-DownloadWithProgress -Url $ZipUrl -Destination $ZipLocation -PrefixText "Downloading Bolt"

Write-Host "Extracting Bolt CLI..." -ForegroundColor Cyan
if (Get-Command Expand-Archive -ErrorAction SilentlyContinue) {
  Expand-Archive $ZipLocation -DestinationPath "$BoltHome" -Force
}
else {
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  [IO.Compression.ZipFile]::ExtractToDirectory($ZipLocation, $BoltHome)
}
Remove-Item $ZipLocation

Write-Host "Successfully downloaded Bolt CLI at $BinDir\bolt.bat" -ForegroundColor Green

# Update BOLT_HOME and PATH securely and automatically
$User = [EnvironmentVariableTarget]::User
[Environment]::SetEnvironmentVariable('BOLT_HOME', $BoltHome, $User)
$Env:BOLT_HOME = $BoltHome

$Path = [Environment]::GetEnvironmentVariable('Path', $User)
if ($null -eq $Path) {
  $Path = ""
}
if (!(";$Path;".ToLower() -like "*;$BinDir;*".ToLower())) {
  $NewPath = if ($Path -eq "") { $BinDir } else { "$Path;$BinDir" }
  [Environment]::SetEnvironmentVariable('Path', $NewPath, $User)
  $Env:Path += ";$BinDir"
  Write-Host "Automatically configured Windows Environment PATH variable with Bolt CLI bin path." -ForegroundColor Green
}

# Check for Java installation
if ($SkipJava) {
  Write-Host "`nSkipping Java check/download as requested (-SkipJava)." -ForegroundColor Yellow
  Write-Host "Note: Bolt CLI requires a Java runtime (Java 11+) to execute." -ForegroundColor Cyan
  Write-Host "`nSuccess! Installed Bolt CLI at $BinDir\bolt.bat!" -ForegroundColor Green
  Write-Host "Run ``bolt --help`` to get started." -ForegroundColor Cyan
}
else {
  Write-Host "`nChecking for Java Runtime / JDK..." -ForegroundColor Cyan
  if (Get-Command "java" -ErrorAction SilentlyContinue) {
    $javaVersion = java -version 2>&1 | Select-Object -First 1
    Write-Host "Java runtime is already installed!" -ForegroundColor Green
    Write-Host "$javaVersion" -ForegroundColor Cyan
    Write-Host "`nSuccess! Installed Bolt CLI at $BinDir\bolt.bat!" -ForegroundColor Green
    Write-Host "Run ``bolt --help`` to get started." -ForegroundColor Cyan
  }
  else {
    Write-Host "Java runtime not found on your system." -ForegroundColor Yellow
    Write-Host "Bolt bundles its own ECJ compiler (ecj.jar) for Java compilation," -ForegroundColor Cyan
    Write-Host "but a Java runtime (JVM) is required to execute Bolt CLI itself." -ForegroundColor Cyan
    Write-Host "Downloading and installing OpenJDK 17..." -ForegroundColor Yellow
    $JdkUrl = "https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip"
    $JdkZip = "$BoltHome\jdk17.zip"
    $JdkDir = "$BoltHome\jdk-17"
    
    Invoke-DownloadWithProgress -Url $JdkUrl -Destination $JdkZip -PrefixText "Downloading JDK 17"
    
    Write-Host "Extracting JDK 17..." -ForegroundColor Cyan
    if (Test-Path "$BoltHome\jdk-tmp") { Remove-Item "$BoltHome\jdk-tmp" -Recurse -Force }
    Expand-Archive $JdkZip -DestinationPath "$BoltHome\jdk-tmp" -Force
    
    $ExtractedFolder = Get-ChildItem "$BoltHome\jdk-tmp" | Select-Object -First 1
    if (Test-Path $JdkDir) { Remove-Item $JdkDir -Recurse -Force }
    Move-Item -Path $ExtractedFolder.FullName -Destination $JdkDir -Force
    Remove-Item "$BoltHome\jdk-tmp" -Recurse -Force
    Remove-Item $JdkZip
    
    $JavaBinDir = "$JdkDir\bin"
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $JdkDir, $User)
    
    $Path = [Environment]::GetEnvironmentVariable('Path', $User)
    if (!(";$Path;".ToLower() -like "*;$JavaBinDir;*".ToLower())) {
      $NewPath = "$Path;$JavaBinDir"
      [Environment]::SetEnvironmentVariable('Path', $NewPath, $User)
      $Env:Path += ";$JavaBinDir"
    }
    Write-Host "Successfully installed Java JDK 17 and updated Environment Variables!" -ForegroundColor Green
    Write-Host "`nSuccess! Installed Bolt CLI at $BinDir\bolt.bat!" -ForegroundColor Green
    Write-Host "Run ``bolt --help`` to get started." -ForegroundColor Cyan
  }
}
