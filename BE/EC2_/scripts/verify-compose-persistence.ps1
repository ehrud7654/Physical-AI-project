<#
.SYNOPSIS
  Compose 볼륨(PostgreSQL)·AOF(Redis)가 컨테이너 재시작 후 데이터를 유지하는지 확인한다.
.DESCRIPTION
  테스트 전용 행/키를 기록 → docker compose restart → 정상 연결 대기 → 동일 데이터 확인 → 생성한 데이터만 정리.
  개발 데이터 삭제나 볼륨 초기화는 하지 않는다. compose.yaml과 같은 폴더(EC2_)에서 실행한다.
.EXAMPLE
  $env:POSTGRES_DB="ssafy"; $env:POSTGRES_USER="ssafy"; $env:POSTGRES_PASSWORD="..."
  .\scripts\verify-compose-persistence.ps1
#>
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

foreach ($v in "POSTGRES_DB", "POSTGRES_USER", "POSTGRES_PASSWORD") {
  if (-not (Get-Item "env:$v" -ErrorAction SilentlyContinue)) { throw "환경변수 $v 가 필요합니다" }
}

$marker = "persistence-check-" + [guid]::NewGuid().ToString("N")
$pgTable = "zz_persistence_check"

function Psql([string]$sql) {
  docker compose exec -T -e PGPASSWORD=$env:POSTGRES_PASSWORD postgres `
    psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -tA -c $sql
  if ($LASTEXITCODE -ne 0) { throw "psql 실패: $sql" }
}
function Redis([string[]]$cmd) {
  $out = docker compose exec -T redis redis-cli @cmd
  if ($LASTEXITCODE -ne 0) { throw "redis-cli 실패: $cmd" }
  return ($out | Out-String).Trim()
}
function WaitHealthy([string]$service) {
  for ($i = 0; $i -lt 30; $i++) {
    $status = docker compose ps --format json $service | ConvertFrom-Json | Select-Object -ExpandProperty Health -ErrorAction SilentlyContinue
    if ($status -eq "healthy") { return }
    Start-Sleep -Seconds 2
  }
  throw "$service 가 healthy 상태가 되지 않음"
}

try {
  Write-Host "1) 테스트 데이터 기록"
  Psql "CREATE TABLE IF NOT EXISTS $pgTable (marker text PRIMARY KEY, created_at timestamptz DEFAULT now());" | Out-Null
  Psql "INSERT INTO $pgTable (marker) VALUES ('$marker');" | Out-Null
  Redis @("SET", $marker, "1") | Out-Null
  # AOF는 appendfsync everysec 기본값이므로 재시작 전 디스크 반영을 강제한다
  Redis @("BGREWRITEAOF") | Out-Null
  Start-Sleep -Seconds 2

  Write-Host "2) Compose 서비스 재시작"
  docker compose restart postgres redis
  if ($LASTEXITCODE -ne 0) { throw "docker compose restart 실패" }

  Write-Host "3) 정상 연결 대기"
  WaitHealthy "postgres"
  WaitHealthy "redis"

  Write-Host "4) 동일 데이터 확인"
  $pgCount = (Psql "SELECT count(*) FROM $pgTable WHERE marker = '$marker';").Trim()
  $redisValue = Redis @("GET", $marker)
  $flywayCount = (Psql "SELECT count(*) FROM flyway_schema_history WHERE success;").Trim()

  if ($pgCount -ne "1") { throw "PostgreSQL 데이터 유실: count=$pgCount" }
  if ($redisValue -ne "1") { throw "Redis 데이터 유실: value=$redisValue" }
  Write-Host "  PostgreSQL 유지 OK (flyway history $flywayCount 건 유지)"
  Write-Host "  Redis 유지 OK"
}
finally {
  Write-Host "5) 생성한 데이터 정리"
  try { Psql "DELETE FROM $pgTable WHERE marker = '$marker';" | Out-Null } catch { Write-Warning $_ }
  try { Psql "DROP TABLE IF EXISTS $pgTable;" | Out-Null } catch { Write-Warning $_ }
  try { Redis @("DEL", $marker) | Out-Null } catch { Write-Warning $_ }
}
Write-Host "완료: 재시작 후 데이터 유지 검증 통과"
