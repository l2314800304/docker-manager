# Build script for docker-manager (Windows PowerShell)
# Usage: .\build.ps1 [-SkipFrontend] [-SkipTests]

param(
    [switch]$SkipFrontend,
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"

Write-Host "=== Docker Manager Build ===" -ForegroundColor Cyan

# Step 1: Build frontend
if (-not $SkipFrontend) {
    Write-Host "`n>>> Building frontend..." -ForegroundColor Yellow
    Push-Location frontend
    npm ci
    npm run build-only

    # Copy dist to Spring Boot static resources
    Write-Host ">>> Copying frontend dist to src/main/resources/static/..." -ForegroundColor Yellow
    $staticDir = "..\src\main\resources\static"
    if (Test-Path $staticDir) {
        Remove-Item -Recurse -Force $staticDir
    }
    Copy-Item -Recurse -Force dist $staticDir
    Pop-Location
    Write-Host ">>> Frontend build complete." -ForegroundColor Green
} else {
    Write-Host ">>> Skipping frontend build." -ForegroundColor Gray
}

# Step 2: Build backend with Maven
Write-Host "`n>>> Building backend with Maven..." -ForegroundColor Yellow
$mavenArgs = @("clean", "package", "-B")
if ($SkipTests) {
    $mavenArgs += "-DskipTests"
}
mvn @mavenArgs

Write-Host "`n=== Build successful! ===" -ForegroundColor Green
Write-Host "JAR file: target/docker-manager-1.0.0-SNAPSHOT.jar" -ForegroundColor White
Write-Host "`nRun with: java -jar target/docker-manager-1.0.0-SNAPSHOT.jar" -ForegroundColor White
