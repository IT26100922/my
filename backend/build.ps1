$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location $projectRoot
New-Item -ItemType Directory -Force backend/build | Out-Null
$javaSources = @(Get-ChildItem backend/src -Recurse -Filter *.java | ForEach-Object FullName)
& javac --release 21 -encoding UTF-8 -d backend/build $javaSources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
Write-Host 'Java compilation passed.'
