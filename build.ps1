# Docker Manager 构建脚本 - Windows PowerShell (DDD 多模块)
# Usage: .\build.ps1 [-SkipFrontend] [-SkipTests]

param(
    [switch]$SkipFrontend,
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"

Write-Host "=== Docker Manager Build (DDD Multi-Module) ===" -ForegroundColor Cyan

# Step 1: Build frontend
if (-not $SkipFrontend) {
    Write-Host "`n>>> Building frontend..." -ForegroundColor Yellow
    Push-Location frontend
    npm ci
    npm run build-only

    Write-Host ">>> Copying frontend dist to docker-manager-starter/src/main/resources/static/..." -ForegroundColor Yellow
    $staticDir = "..\docker-manager-starter\src\main\resources\static"
    if (Test-Path $staticDir) {
        Remove-Item -Recurse -Force $staticDir
    }
    Copy-Item -Recurse -Force dist $staticDir
    Pop-Location
    Write-Host ">>> Frontend build complete." -ForegroundColor Green
} else {
    Write-Host ">>> Skipping frontend build." -ForegroundColor Gray
}

# Step 2: Build all Maven modules
Write-Host "`n>>> Building Maven modules..." -ForegroundColor Yellow
$mavenArgs = @("clean", "package", "-B")
if ($SkipTests) {
    $mavenArgs += "-DskipTests"
}
mvn @mavenArgs

Write-Host "`n=== Build successful! ===" -ForegroundColor Green
Write-Host "JAR file: docker-manager-starter/target/docker-manager-starter-1.0.0-SNAPSHOT.jar" -ForegroundColor White
Write-Host "`nRun with: java -jar docker-manager-starter/target/docker-manager-starter-1.0.0-SNAPSHOT.jar" -ForegroundColor White
