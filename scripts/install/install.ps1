#!/usr/bin/env pwsh

param(
    [string]$InstallPath  # optional override for the installation directory
)

$ErrorActionPreference = 'Stop'
$Esc = [char]27

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

$BinDir = "$BoltHome\bin"

# Check if bolt is already installed
if (Test-Path "$BinDir\bolt.exe") {
    $boltVersion = & "$BinDir\bolt.exe" --version
    Write-Host "`n$Esc[32;1mBolt CLI is already installed!$Esc[0m"
    Write-Host "$Esc[36mVersion: $boltVersion$Esc[0m"
    Write-Host "If you want to reinstall or upgrade, please use 'bolt upgrade' or remove the $BoltHome directory first.`n"
    Exit 0
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
            
            $ProgressMsg = "`r$Esc[33;1m$($PrefixText):$Esc[0m $Esc[33;1m$PctStr%$Esc[0m ($Esc[36m$RxMB MB/$TotalMB MB$Esc[0m) | $Esc[32;1m$SpeedStr MB/s$Esc[0m |"
            [Console]::Write($ProgressMsg)
            $LastReportTime = $Now
            $LastReportBytes = $ReceivedBytes
        }
    }
    $FileStream.Close()
    $Stream.Close()
    Write-Host "`n$Esc[32;1mDownload complete!$Esc[0m"
}

# choose the correct binary
$ZipUrl = "https://github.com/TechHamara/bolt-cli/releases/latest/download/bolt-win.zip"
$ZipLocation = "$BoltHome\bolt-win.zip"

# GitHub requires TLS 1.2
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

Write-Host "$Esc[36mStarting Bolt CLI download...$Esc[0m"
Invoke-DownloadWithProgress -Url $ZipUrl -Destination $ZipLocation -PrefixText "Downloading Bolt"

# Extract it
Write-Host "$Esc[36mExtracting Bolt CLI...$Esc[0m"
if (Get-Command Expand-Archive -ErrorAction SilentlyContinue) {
  Expand-Archive $ZipLocation -DestinationPath "$BoltHome" -Force
}
else {
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  [IO.Compression.ZipFile]::ExtractToDirectory($ZipLocation, $BoltHome)
}
Remove-Item $ZipLocation

Write-Host "$Esc[32;1mSuccessfully downloaded the Bolt CLI binary at $BoltHome\bin\bolt.exe$Esc[0m"

# Update PATH securely and automatically
$User = [EnvironmentVariableTarget]::User
$Path = [Environment]::GetEnvironmentVariable('Path', $User)
if ($null -eq $Path) {
  $Path = ""
}
if (!(";$Path;".ToLower() -like "*;$BinDir;*".ToLower())) {
  $NewPath = if ($Path -eq "") { $BinDir } else { "$Path;$BinDir" }
  [Environment]::SetEnvironmentVariable('Path', $NewPath, $User)
  $Env:Path += ";$BinDir"
  Write-Host "$Esc[32;1mAutomatically configured Windows Environment PATH variable with Bolt CLI bin path.$Esc[0m"
}

# Check for Java installation
Write-Host "`n$Esc[36mChecking for Java SDK...$Esc[0m"
if (Get-Command "java" -ErrorAction SilentlyContinue) {
    # Java is installed
    $javaVersion = java -version 2>&1 | Select-Object -First 1
    Write-Host "$Esc[32;1mJava is already installed!$Esc[0m"
    Write-Host "$Esc[36m$javaVersion$Esc[0m"
}
else {
    Write-Host "$Esc[33;1mJava is not installed. Downloading and installing OpenJDK 17...$Esc[0m"
    $JdkUrl = "https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip"
    $JdkZip = "$BoltHome\jdk17.zip"
    $JdkDir = "$BoltHome\jdk-17"
    
    Invoke-DownloadWithProgress -Url $JdkUrl -Destination $JdkZip -PrefixText "Downloading JDK 17"
    
    Write-Host "$Esc[36mExtracting JDK 17...$Esc[0m"
    if (Test-Path "$BoltHome\jdk-tmp") { Remove-Item "$BoltHome\jdk-tmp" -Recurse -Force }
    Expand-Archive $JdkZip -DestinationPath "$BoltHome\jdk-tmp" -Force
    
    # move extracted folder to $JdkDir
    $ExtractedFolder = Get-ChildItem "$BoltHome\jdk-tmp" | Select-Object -First 1
    if (Test-Path $JdkDir) { Remove-Item $JdkDir -Recurse -Force }
    Move-Item -Path $ExtractedFolder.FullName -Destination $JdkDir -Force
    Remove-Item "$BoltHome\jdk-tmp" -Recurse -Force
    Remove-Item $JdkZip
    
    $JavaBinDir = "$JdkDir\bin"
    # set java home and path
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $JdkDir, $User)
    
    # refresh path
    $Path = [Environment]::GetEnvironmentVariable('Path', $User)
    if (!(";$Path;".ToLower() -like "*;$JavaBinDir;*".ToLower())) {
        $NewPath = "$Path;$JavaBinDir"
        [Environment]::SetEnvironmentVariable('Path', $NewPath, $User)
        $Env:Path += ";$JavaBinDir"
    }
    Write-Host "$Esc[32;1mSuccessfully installed Java JDK 17 and updated Environment Variables!$Esc[0m"
}

# Prompt user if they want to download dev dependencies now
$Yes = New-Object System.Management.Automation.Host.ChoiceDescription "&Yes"
$No = New-Object System.Management.Automation.Host.ChoiceDescription "&No"
$Options = [System.Management.Automation.Host.ChoiceDescription[]]($Yes, $No)

$Title = "Now, proceeding to download necessary Java libraries (approx size: 170 MB)."
$Message = "Do you want to continue?"
$Result = $host.ui.PromptForChoice($Title, $Message, $Options, 0)
if ($Result -eq 0) {
  Start-Process -NoNewWindow -FilePath "$BinDir\bolt.exe" -ArgumentList "deps", "sync", "--dev-deps", "--no-logo" -Wait 
}

if ($Result -eq 0) {
  Write-Host "`n$Esc[32;1mSuccess! Installed Bolt CLI at $BinDir\bolt.exe!$Esc[0m"
  Write-Host "$Esc[36mRun ``bolt --help`` to get started.$Esc[0m"
}
else {
  Write-Host "`n$Esc[33;1mBolt CLI has been partially installed at $BinDir\bolt.exe!$Esc[0m"
  Write-Host "$Esc[36mPlease run ``bolt deps sync --dev-deps`` to download necessary Java libraries.$Esc[0m"
}
