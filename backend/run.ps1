$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
& "$PSScriptRoot/build.ps1"
& java -cp 'backend/build;backend/lib/*' com.friendfinder.Main
