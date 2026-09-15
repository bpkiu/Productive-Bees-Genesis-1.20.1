# Changelog

所有重要变更将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本管理遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

本仓库是资源蜜蜂：创世（Productive Bees Genesis）的 Minecraft 1.20.1 / Forge 官方向下移植版，
功能基线对齐 1.21.1 / NeoForge 主线（`productive-bees-addon`）。版本号与主线对齐：
迁移未完成时使用主线版本号加 `-beta.N` 预发布后缀，功能迁移完成后发布与主线一致的版本号。

## [1.0.7-beta.1] - 2026-09-15

基于 1.21.1 主线 `v1.0.7` 的首个工程化整理版本（初始移植代码由 bpkiu 提供）。

### 工程化

- **编译依赖本地化**：移除对个人机器路径（`G:\versions\2\mods`、`C:\Users\XBX\...`）的硬编码引用，全部 13 个 1.20.1 Forge 编译依赖统一放入项目 `libs/`（不入库），由 CI 工作流按固定来源（CurseMaven / Modrinth）自动获取；ProductiveLib 1.20.1-0.0.4 从 Productive Bees jar 内嵌 jarjar 提取，与主线 1.21.1 CI 的做法一致。JEI 使用官方 15.48.0.185 替代原 JEIunofficial 15.48.0.182（API 兼容）。
- **开发运行时依赖**：Productive Bees、Mekanism、Mekanism Extras、Evolved Mekanism、EME Extras、AE2、JEI、Jade、KubeJS、Rhino 同时声明为 `runtimeOnly`，`runClient` / `runServer` 可直接进行集成冒烟测试；AppFlux 与 JDTE 仍为纯编译期依赖（运行时按模组加载状态条件启用）。
- **构建输出回归项目目录**：移除把构建输出重定向到系统临时目录（C 盘）的临时配置；Gradle 用户目录（`GRADLE_USER_HOME`）与 ForgeGradle 缓存全部位于 E 盘，不写入 C 盘。
- **新增 CI 构建工作流**（`.github/workflows/build.yml`）：推送 / PR 触发 Java 17 + ForgeGradle 6 全量构建并上传产物。1.20.1 版本暂未发布 CurseForge，工作流不含自动发布步骤。
- **元数据规范化**：`mod_authors` / `mod_description` 移除模板占位符，与主线一致；版本号采用主线 `1.0.7` 加 `-beta.1` 预发布后缀，如实表明迁移尚未完成。

### 已知待迁移项（相对主线 1.0.7）

以下功能在 1.21.1 主线 `v1.0.7` 中存在、尚未移植到 1.20.1，将在后续 beta 版本中分批补齐：

- 喂食槽逐格禁用与批量恢复（`FeederSlotDisableState`、`ToggleFeederSlotDisabledPayload` 等约 10 个类）
- 每台机器独立的产物直通开关与相邻容器直推（`ApiaryDirectContainerOutput`、`DirectContainerOutputPayload`、`ExternalLogisticsConfigSection` 等）
- 自研高性能物品弹出通道（`logistics` 包：`FastItemEjector`、`EjectItemMapBuilder`、`OutputWakeNotifier`、`RotatingContainerView` 等）
- 服务端配置三文件拆分与事务式自动迁移（`ServerConfigMigration*`、`FactoryTier*` 系列、容量矩阵数组化）
- 离心机输入返还按钮（`ReturnCentrifugeInputPayload`、`CentrifugeInputReturnButton`、`Ae2CentrifugeInputReturnService` 等）

### 已知问题

- `mek.CentrifugeUselessByproductMixin` 暂时禁用：Mekanism 1.20.1 的 `TileEntityCentrifuge` 目标类名（SRG 映射）尚未验证，验证后重新启用。
- 语言文件仍保留主线 1.0.7 已删除的 11 项弹出节流配置键（`ejection.*`）与旧版每等级容量矩阵键，待配置拆分迁移完成后一并清理。
- 机械蜂箱 `front_led` 模型仍为逐等级独立文件（主线 1.0.7 已改为引用共享模型），功能不受影响，后续对齐。
- 数据生成代码（`datagen` 包 12 个类）仍位于主源集并进入发布 JAR；主线 1.0.7 已移至独立源集。待配置拆分迁移批次一并处理。
