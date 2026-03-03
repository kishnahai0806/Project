param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArgs
)

$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
    Write-Error "Docker is required to run Maven in a container."
    exit 1
}

$null = docker info 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker daemon is not running. Start Docker Desktop (for example: Start-Process ""C:\Program Files\Docker\Docker\Docker Desktop.exe""), wait until it is fully running, then retry."
    exit 1
}

$workspace = (Get-Location).Path -replace '\\','/'
docker run --rm `
  -v "${workspace}:/workspace" `
  -v "//var/run/docker.sock:/var/run/docker.sock" `
  -e "DOCKER_HOST=unix:///var/run/docker.sock" `
  -e "TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock" `
  --add-host "host.docker.internal:host-gateway" `
  -e "TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal" `
  -w /workspace `
  maven:3.9.8-eclipse-temurin-17 `
  mvn @MavenArgs
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}