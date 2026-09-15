#!/usr/bin/env bash
# 获取 1.20.1 Forge 编译依赖到 libs/（与 scripts/fetch-libs.ps1 使用同一清单）
# 由 CI（.github/workflows/build.yml）调用；幂等：已存在的 jar 自动跳过
set -euo pipefail

libs="libs"
mkdir -p "$libs"
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

fetch() {
  if [ -s "$libs/$2" ] && [ "$(stat -c%s "$libs/$2" 2>/dev/null || stat -f%z "$libs/$2")" -gt 102400 ]; then
    echo "skip: $2"
    return
  fi
  curl --fail --location --retry 3 --retry-all-errors "$1" --output "$libs/$2"
  test -s "$libs/$2"
  echo "ok: $2"
}
curse_maven() {
  fetch "https://www.cursemaven.com/curse/maven/$1/$2/$1-$2.jar" "$3"
}

# Productive Bees 1.20.1-12.6.0
curse_maven productivebees-377897 5566102 productivebees-1.20.1-12.6.0.jar
# Mekanism 1.20.1-10.4.16（Modrinth 官方源）
fetch 'https://cdn.modrinth.com/data/Ce6I4WUE/versions/uxe1WQp4/Mekanism-1.20.1-10.4.16.80.jar' \
  Mekanism-1.20.1-10.4.16.80.jar
# Applied Energistics 2 1.20.1 Forge 15.4.10
fetch 'https://cdn.modrinth.com/data/XxWD5pD3/versions/7KVs6HMQ/appliedenergistics2-forge-15.4.10.jar' \
  appliedenergistics2-forge-15.4.10.jar
# JEI 1.20.1 Forge 15.48.0.185（官方版，替代 JEIunofficial 15.48.0.182）
fetch 'https://cdn.modrinth.com/data/u6dRKJwZ/versions/TvllnAfz/jei-1.20.1-forge-15.48.0.185.jar' \
  jei-1.20.1-forge-15.48.0.185.jar
# Jade 1.20.1 Forge 11.13.2
fetch 'https://cdn.modrinth.com/data/nvQzSEkH/versions/LecuGude/Jade-1.20.1-Forge-11.13.2.jar' \
  Jade-1.20.1-Forge-11.13.2.jar
# KubeJS 1.20.1 Forge 2001.6.5-build.16 + Rhino 2001.2.3-build.10
fetch 'https://cdn.modrinth.com/data/umyGl7zF/versions/g5igndAv/kubejs-forge-2001.6.5-build.16.jar' \
  kubejs-forge-2001.6.5-build.16.jar
fetch 'https://cdn.modrinth.com/data/sk9knFPE/versions/uNALdylI/rhino-forge-2001.2.3-build.10.jar' \
  rhino-forge-2001.2.3-build.10.jar

# Mekanism Extras（通用机械：扩展）1.20.1-1.4.6
curse_maven mekanism-extras-1026040 6936409 mekanism_extras-1.20.1-1.4.6.jar
# Evolved Mekanism（通用机械：进化）1.20.1-1.2.1-fix4
curse_maven evolved-mekanism-1230085 7599591 EvolvedMekanism-1.20.1-1.2.1-fix4.jar
# Evolved Mekanism Extras 1.20.1-1.3.8
curse_maven evolved-mekanism-extras-1268159 8076395 EvolvedMekanismExtras-1.20.1-1.3.8.jar
# AppliedFlux 1.20.1 Forge 1.3.7
curse_maven applied-flux-965012 7651647 AppliedFlux-1.20-1.3.7-forge.jar
# JDTE 0.5.9-Fix（仅编译期引用，运行时按加载状态条件启用）
curse_maven jdte-1566414 8706482 jdte-0.5.9-Fix.jar

# ProductiveLib：从 Productive Bees jar 的内嵌 jarjar 提取（勿单独放入游戏 mods 目录）
if [ ! -s "$libs/productivelib-1.20.1-0.0.4.jar" ]; then
  unzip -p "$libs/productivebees-1.20.1-12.6.0.jar" \
    META-INF/jarjar/productivelib-1.20.1-0.0.4.jar \
    > "$libs/productivelib-1.20.1-0.0.4.jar"
  test -s "$libs/productivelib-1.20.1-0.0.4.jar"
  echo 'ok: productivelib-1.20.1-0.0.4.jar (extracted)'
else
  echo 'skip: productivelib-1.20.1-0.0.4.jar'
fi

echo 'libs/ ready.'
