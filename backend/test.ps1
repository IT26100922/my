$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
& "$PSScriptRoot/build.ps1"
$testSources = @(Get-ChildItem backend/src,backend/test -Recurse -Filter *.java | ForEach-Object FullName)
& javac --release 21 -encoding UTF-8 -d backend/build $testSources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& java -cp backend/build com.friendfinder.DomainTest
if ($LASTEXITCODE -ne 0) { throw 'Domain tests failed' }
