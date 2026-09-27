$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$images = @(
    @{ Name='EC2_Backend'; Context='BE/EC2_'; Original='ssafy-ec2-api:local' },
    @{ Name='EC2_Frontend'; Context='FE'; Original='ssafy-fe:local' },
    @{ Name='EC2_Nginx'; Context='BE/EC2_/nginx'; Original='ssafy-nginx:local' },
    @{ Name='GPU_Backend_Spring'; Context='BE/GPU_'; Original='ssafy-gpu-backend:local' },
    @{ Name='GPU_Backend_FastAPI'; Context='BE/GPU_/FastAPI_'; Original='ssafy-gpu-fastapi:local' },
    @{ Name='GPU_Backend_Preprocessor'; Context='BE/GPU_/umi_server_preprocessor'; Original='umi-server-preprocessor:2026.09.23-orbslam-v3' }
)
$records = @()
foreach ($item in $images) {
    $tag = $item.Name.ToLowerInvariant() + ':submission'
    $buildArgs = @('build', '--platform', 'linux/amd64', '-t', $tag)
    if ($item.Name -eq 'EC2_Frontend') { $buildArgs += @('--build-arg', 'NEXT_PUBLIC_API_BASE_URL=/api/v1') }
    $buildArgs += (Join-Path $repoRoot $item.Context)
    Write-Output "BUILD $($item.Name)"
    & docker @buildArgs
    if ($LASTEXITCODE -ne 0) { throw "Build failed: $tag" }
    $archive = Join-Path $PSScriptRoot ($item.Name + '.tar')
    Write-Output "EXPORT $($item.Name)"
    & docker image save --output $archive $tag
    if ($LASTEXITCODE -ne 0) { throw "Export failed: $tag" }
    $info = (& docker image inspect $tag | ConvertFrom-Json)[0]
    if ($LASTEXITCODE -ne 0) { throw "Inspect failed: $tag" }
    $hash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
    $records += [pscustomobject]@{ File=($item.Name+'.tar'); Tag=$tag; ComposeTag=$item.Original; ImageId=$info.Id; Platform=($info.Os+'/'+$info.Architecture); Bytes=(Get-Item -LiteralPath $archive).Length; SHA256=$hash }
    $records | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'images.json') -Encoding utf8
    $records | ForEach-Object { "$($_.SHA256)  $($_.File)" } | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'SHA256SUMS.txt') -Encoding ascii
    Write-Output "DONE $($item.Name) bytes=$((Get-Item -LiteralPath $archive).Length)"
}
