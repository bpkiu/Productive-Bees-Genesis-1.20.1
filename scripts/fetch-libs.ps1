# 获取 1.20.1 Forge 编译依赖到 libs/（与 .github/workflows/build.yml 使用同一清单）
# 用法：powershell -ExecutionPolicy Bypass -File scripts/fetch-libs.ps1
# 幂等：已存在且大于 100KB 的 jar 自动跳过
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$libs = Join-Path $root 'libs'
New-Item -ItemType Directory -Force -Path $libs | Out-Null

# 依赖清单：url + 保存文件名（必须与 build.gradle 中 libs/ 引用一致）
$deps = @(
    @{ u = 'https://www.cursemaven.com/curse/maven/productivebees-377897/5566102/productivebees-377897-5566102.jar'; f = 'productivebees-1.20.1-12.6.0.jar' },
    @{ u = 'https://cdn.modrinth.com/data/Ce6I4WUE/versions/uxe1WQp4/Mekanism-1.20.1-10.4.16.80.jar';           f = 'Mekanism-1.20.1-10.4.16.80.jar' },
    @{ u = 'https://cdn.modrinth.com/data/XxWD5pD3/versions/7KVs6HMQ/appliedenergistics2-forge-15.4.10.jar';    f = 'appliedenergistics2-forge-15.4.10.jar' },
    @{ u = 'https://cdn.modrinth.com/data/u6dRKJwZ/versions/TvllnAfz/jei-1.20.1-forge-15.48.0.185.jar';         f = 'jei-1.20.1-forge-15.48.0.185.jar' },
    @{ u = 'https://cdn.modrinth.com/data/nvQzSEkH/versions/LecuGude/Jade-1.20.1-Forge-11.13.2.jar';            f = 'Jade-1.20.1-Forge-11.13.2.jar' },
    @{ u = 'https://cdn.modrinth.com/data/umyGl7zF/versions/g5igndAv/kubejs-forge-2001.6.5-build.16.jar';       f = 'kubejs-forge-2001.6.5-build.16.jar' },
    @{ u = 'https://cdn.modrinth.com/data/sk9knFPE/versions/uNALdylI/rhino-forge-2001.2.3-build.10.jar';        f = 'rhino-forge-2001.2.3-build.10.jar' },
    @{ u = 'https://www.cursemaven.com/curse/maven/mekanism-extras-1026040/6936409/mekanism-extras-1026040-6936409.jar';         f = 'mekanism_extras-1.20.1-1.4.6.jar' },
    @{ u = 'https://www.cursemaven.com/curse/maven/evolved-mekanism-1230085/7599591/evolved-mekanism-1230085-7599591.jar';       f = 'EvolvedMekanism-1.20.1-1.2.1-fix4.jar' },
    @{ u = 'https://www.cursemaven.com/curse/maven/evolved-mekanism-extras-1268159/8076395/evolved-mekanism-extras-1268159-8076395.jar'; f = 'EvolvedMekanismExtras-1.20.1-1.3.8.jar' },
    @{ u = 'https://www.cursemaven.com/curse/maven/applied-flux-965012/7651647/applied-flux-965012-7651647.jar';                 f = 'AppliedFlux-1.20-1.3.7-forge.jar' },
    @{ u = 'https://www.cursemaven.com/curse/maven/jdte-1566414/8706482/jdte-1566414-8706482.jar';                                f = 'jdte-0.5.9-Fix.jar' }
)

foreach ($d in $deps) {
    $out = Join-Path $libs $d.f
    if ((Test-Path $out) -and ((Get-Item $out).Length -gt 100KB)) { Write-Host "skip: $($d.f)"; continue }
    & curl.exe -sSL --retry 3 --retry-all-errors -o $out $d.u
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $out) -or (Get-Item $out).Length -lt 100KB) { throw "download failed: $($d.f)" }
    Write-Host "ok: $($d.f) ($([math]::Round((Get-Item $out).Length/1MB,1))MB)"
}

# ProductiveLib：从 Productive Bees jar 的内嵌 jarjar 提取（勿单独放入游戏 mods 目录）
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$pbJar = Join-Path $libs 'productivebees-1.20.1-12.6.0.jar'
$plOut = Join-Path $libs 'productivelib-1.20.1-0.0.4.jar'
if (-not (Test-Path $plOut) -or (Get-Item $plOut).Length -lt 1KB) {
    $z = [System.IO.Compression.ZipFile]::OpenRead($pbJar)
    try {
        $e = $z.Entries | Where-Object { $_.FullName -eq 'META-INF/jarjar/productivelib-1.20.1-0.0.4.jar' } | Select-Object -First 1
        if (-not $e) { throw 'productivelib not found in PB jarjar' }
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, $plOut, $true)
        Write-Host "ok: productivelib-1.20.1-0.0.4.jar (extracted)"
    } finally { $z.Dispose() }
} else { Write-Host 'skip: productivelib-1.20.1-0.0.4.jar' }

Write-Host 'libs/ ready.'
