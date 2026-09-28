param(
    [string]$PostgresContainer = "ec2_-postgres-1",
    [string]$RedisContainer = "ec2_-redis-1",
    [int]$PostgresPort = 15432,
    [int]$RedisPort = 6379,
    [string[]]$GradleTasks = @("test")
)

$ErrorActionPreference = "Stop"

function Assert-RunningContainer([string]$ContainerName) {
    $running = docker inspect -f "{{.State.Running}}" $ContainerName 2>$null
    if ($LASTEXITCODE -ne 0 -or $running -ne "true") {
        throw "Docker container '$ContainerName' is not running."
    }
}

function Get-ContainerEnvironment([string]$ContainerName, [string]$VariableName) {
    $value = docker exec $ContainerName printenv $VariableName
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($value)) {
        throw "Could not read '$VariableName' from '$ContainerName'."
    }
    return $value.Trim()
}

Assert-RunningContainer $PostgresContainer
Assert-RunningContainer $RedisContainer

$previousEnvironment = @{
    TEST_POSTGRES_URL      = $env:TEST_POSTGRES_URL
    TEST_POSTGRES_USER     = $env:TEST_POSTGRES_USER
    TEST_POSTGRES_PASSWORD = $env:TEST_POSTGRES_PASSWORD
    TEST_REDIS_HOST        = $env:TEST_REDIS_HOST
    TEST_REDIS_PORT        = $env:TEST_REDIS_PORT
}

try {
    $database = Get-ContainerEnvironment $PostgresContainer "POSTGRES_DB"
    $username = Get-ContainerEnvironment $PostgresContainer "POSTGRES_USER"
    $password = Get-ContainerEnvironment $PostgresContainer "POSTGRES_PASSWORD"

    $env:TEST_POSTGRES_URL = "jdbc:postgresql://127.0.0.1:$PostgresPort/$database"
    $env:TEST_POSTGRES_USER = $username
    $env:TEST_POSTGRES_PASSWORD = $password
    $env:TEST_REDIS_HOST = "127.0.0.1"
    $env:TEST_REDIS_PORT = $RedisPort.ToString()

    & "$PSScriptRoot/../gradlew.bat" @GradleTasks
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle test execution failed with exit code $LASTEXITCODE."
    }
}
finally {
    foreach ($entry in $previousEnvironment.GetEnumerator()) {
        if ($null -eq $entry.Value) {
            Remove-Item "Env:$($entry.Key)" -ErrorAction SilentlyContinue
        }
        else {
            Set-Item "Env:$($entry.Key)" $entry.Value
        }
    }
}
