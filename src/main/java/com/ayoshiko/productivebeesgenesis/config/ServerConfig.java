package com.ayoshiko.productivebeesgenesis.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * 服务端配置 — 存档级别配置
 * <p>
 * 从 {@link ModConfig} 抽取的独立配置类(Task 21),遵循单一职责原则(SRP)。
 * 随存档保存,不同存档可拥有不同配置。世界加载时自动生效,无需执行 /reload。
 * 实例由 {@link ModConfig#SERVER} 聚合持有；基础配置和非倍率委托字段继续通过
 * {@code ModConfig.SERVER.xxx} 访问，等级倍率统一通过各配置段访问。
 * <p>
 * <b>1.0.7 三文件拆分</b>：构造函数接收三个 Builder，配置键按领域分布：
 * <ul>
 *   <li>gameplay（{@code productivebeesgenesis-gameplay-server.toml}）— 蜜蜂玩法、平衡、过滤</li>
 *   <li>machines（{@code productivebeesgenesis-machines-server.toml}）— 离心机/蜂箱机器参数</li>
 *   <li>capacities（{@code productivebeesgenesis-capacities-server.toml}）— 容量矩阵（数组化）</li>
 * </ul>
 * 旧单文件由 {@link ServerConfigMigrationService} 事务式迁移。
 * <p>
 * <b>职责拆分(Task 12 / Task 19)</b>:本类作为聚合入口持有 {@link ConfigSectionRegistry},
 * 子配置段创建/查找逻辑委托至注册表,本类仅保留 Builder 入口与基础配置定义。
 * 为向后兼容保留 public final 委托字段(指向同一 ConfigValue 实例,零开销):
 * <ul>
 *   <li>{@link BeeAttributeConfigSection} — 万象创世蜜蜂属性覆盖配置(bee_attributes.*)</li>
 *   <li>{@link CentrifugeConfigSection} — MEK 离心机配置(mek_centrifuge.*)</li>
 *   <li>{@link ApiaryConfigSection} — MEK 通用机械蜂箱配置(mek_apiary.*,Task 18 新增)</li>
 *   <li>{@link ExternalLogisticsConfigSection} — 外部物流互操作(external_logistics.*)</li>
 * </ul>
 * <p>
 * <b>1.20.1 移植说明</b>：主线 1.0.7 已移除弹出节流键；本移植版旧 Ejector Mixin 仍在
 * 消费这些键（批次 4 logistics 迁移后移除），对应委托字段暂时保留。
 * apiaryStackXxx 逐等级键已删除，蜂箱容量倍率改经 {@link FactoryTierConfigService} 快照读取。
 */
public final class ServerConfig {

	// ========== 配置段注册表(Task 12 抽取)==========
	private final ConfigSectionRegistry sections;

	/** 获取万象创世蜜蜂属性配置段(供新代码使用,旧代码可继续通过委托字段访问) */
	public BeeAttributeConfigSection beeAttributes() { return sections.beeAttributes(); }
	/** 获取 MEK 离心机配置段(供新代码使用,旧代码可继续通过委托字段访问) */
	public CentrifugeConfigSection centrifuge() { return sections.centrifuge(); }
	/** 获取 MEK 通用机械蜂箱配置段(Task 18 新增) */
	public ApiaryConfigSection apiary() { return sections.apiary(); }
	/** 获取外部物流互操作配置段(1.0.7 新增) */
	public ExternalLogisticsConfigSection externalLogistics() { return sections.externalLogistics(); }

	// ========== 万象创世蜜蜂总开关(存档级别)==========
	public final ForgeConfigSpec.BooleanValue myriadCreationsEnabled;
	public final ForgeConfigSpec.EnumValue<BalancePreset> balancePreset;
	public final ForgeConfigSpec.BooleanValue productivityUpgradeTiersExclusive;
	public final ForgeConfigSpec.BooleanValue speedUpgradeTiersExclusive;
	public final ForgeConfigSpec.BooleanValue centrifugeProductivityAffectsOutput;
	public final ForgeConfigSpec.BooleanValue apiaryBeeGenesAffectWork;

	// ========== 万象创世过滤配置(存档级别)==========
	// 使用枚举类型,ConfigurationScreen自动渲染循环切换按钮
	public final ForgeConfigSpec.EnumValue<ModConfig.FilterMode> myriadCreationsFilterMode;
	public final ForgeConfigSpec.ConfigValue<List<? extends String>> myriadCreationsFilteredBeeTypes;

	// ========== 万象创世蜜蜂属性(服务端生效)—— 向后兼容委托字段 ==========
	public final ForgeConfigSpec.ConfigValue<String> primaryColor;
	public final ForgeConfigSpec.ConfigValue<String> secondaryColor;
	public final ForgeConfigSpec.ConfigValue<String> particleColor;
	public final ForgeConfigSpec.ConfigValue<String> glowColor;
	public final ForgeConfigSpec.ConfigValue<String> flowerItem;
	public final ForgeConfigSpec.ConfigValue<String> weatherTolerance;
	public final ForgeConfigSpec.ConfigValue<String> temper;
	public final ForgeConfigSpec.ConfigValue<String> behavior;
	public final ForgeConfigSpec.ConfigValue<String> endurance;
	public final ForgeConfigSpec.ConfigValue<String> productivity;
	public final ForgeConfigSpec.BooleanValue createComb;
	public final ForgeConfigSpec.DoubleValue size;
	public final ForgeConfigSpec.DoubleValue speed;
	public final ForgeConfigSpec.DoubleValue attack;
	public final ForgeConfigSpec.ConfigValue<String> breedingItem;
	public final ForgeConfigSpec.IntValue breedingItemCount;
	public final ForgeConfigSpec.BooleanValue selfbreed;
	public final ForgeConfigSpec.BooleanValue waterproof;
	public final ForgeConfigSpec.BooleanValue fireproof;

	// ========== 蜜蜂获得方式配置 ==========
	public final ForgeConfigSpec.BooleanValue fishingEnabled;
	public final ForgeConfigSpec.DoubleValue fishingChance;
	public final ForgeConfigSpec.ConfigValue<List<? extends String>> fishingBiomes;
	public final ForgeConfigSpec.BooleanValue breedingEnabled;
	public final ForgeConfigSpec.ConfigValue<String> breedingParent1;
	public final ForgeConfigSpec.ConfigValue<String> breedingParent2;
	public final ForgeConfigSpec.BooleanValue spawningEnabled;
	public final ForgeConfigSpec.ConfigValue<String> spawningNest;
	public final ForgeConfigSpec.ConfigValue<String> spawningBiomes;

	// ========== 蜜蜂转化与产出配置 ==========
	public final ForgeConfigSpec.BooleanValue conversionEnabled;
	public final ForgeConfigSpec.ConfigValue<String> conversionSource;
	public final ForgeConfigSpec.ConfigValue<String> conversionResult;
	public final ForgeConfigSpec.ConfigValue<String> conversionItem;
	public final ForgeConfigSpec.DoubleValue conversionChance;
	public final ForgeConfigSpec.BooleanValue apiaryItemConversionEnabled;
	public final ForgeConfigSpec.BooleanValue apiaryBlockConversionEnabled;
	public final ForgeConfigSpec.BooleanValue produceEnabled;
	public final ForgeConfigSpec.ConfigValue<String> produceOutputItem;
	public final ForgeConfigSpec.IntValue produceOutputMin;
	public final ForgeConfigSpec.IntValue produceOutputMax;
	public final ForgeConfigSpec.DoubleValue produceOutputChance;
	public final ForgeConfigSpec.IntValue myriadProduceThrottlePerTick;

	// ========== 高级蜂箱性能优化配置 ==========
	// 注意:isSim() / hasNectar() 缓存是默认开启且不可关闭的内部优化,
	// 不在配置界面暴露,避免玩家误操作导致性能回退。
	public final ForgeConfigSpec.IntValue advancedBeehiveSimulateCooldown;
	// 高级蜂箱 NBT 保存间隔(tick),降低高倍加速下的 CompoundTag 序列化开销
	public final ForgeConfigSpec.IntValue advancedBeehiveSaveInterval;
	public final ForgeConfigSpec.IntValue maxBatchTicksPerTick;

	// ========== MEK离心机配置 —— 向后兼容委托字段(基础参数,堆叠/流体倍率已迁移至子段)==========
	public final ForgeConfigSpec.LongValue mekCentrifugeEnergyPerTick;
	/** 能量存储容量(FE),工厂版按并行数倍增。Task 3 从硬编码 20000L 改为 config */
	public final ForgeConfigSpec.LongValue mekCentrifugeEnergyStorage;
	public final ForgeConfigSpec.IntValue mekCentrifugeProcessingTime;
	public final ForgeConfigSpec.IntValue mekCentrifugeEjectDelay;
	public final ForgeConfigSpec.IntValue mekCentrifugeEjectDelayActive;
	public final ForgeConfigSpec.IntValue mekCentrifugeFluidTankCapacity;
	/** 多流体槽模式开关:false=单槽共享(默认),true=按流体类型动态分配独立槽位 */
	public final ForgeConfigSpec.BooleanValue mekCentrifugeMultiFluidTank;
	/**
	 * 每种流体类型最大占用槽位数(委托自 CentrifugeConfigSection)
	 * <br/>
	 * 0=自动计算 maxTanks/2,>0=手动指定配额,防止高产出流体占用所有槽位
	 */
	public final ForgeConfigSpec.IntValue mekCentrifugeMaxTanksPerFluid;
	/**
	 * 流体弹出速率(mB/tick),默认 256,范围 1-Integer.MAX_VALUE
	 * <br/>
	 * 委托自 CentrifugeConfigSection,由 AbstractMekCentrifugeFactory 构造函数注入 Ejector。
	 * 100-tick CAS 缓存读取避免 TPS 退化(参考 MultiFluidSideConfigHandler.getCachedEjectRate)。
	 */
	public final ForgeConfigSpec.IntValue mekCentrifugeFluidEjectRate;
	public final ForgeConfigSpec.IntValue mekCentrifugeCombBlockMultiplier;
	// AE2/管道拉取限流(从未接线,保留键位,批次 4 停用)
	public final ForgeConfigSpec.IntValue mekCentrifugeMaxExtractPerTick;
	// Ejector 输出阻塞冷却参数(旧 Mixin 消费,批次 4 移除)
	public final ForgeConfigSpec.IntValue mekCentrifugeEjectBlockedThreshold;
	public final ForgeConfigSpec.IntValue mekCentrifugeEjectBlockedCooldown;
	// 输出槽内容未变化时跳过 outputItems(旧 Mixin 消费,批次 4 移除)
	public final ForgeConfigSpec.BooleanValue mekCentrifugeEjectSkipUnchanged;
	public final ForgeConfigSpec.IntValue mekCentrifugeEjectSkipTicks;
	// 最大弹出速度模式(旧 Mixin 消费,批次 4 移除)
	public final ForgeConfigSpec.BooleanValue mekCentrifugeEjectMaxSpeedMode;
	// Ejector 持续高负载下降频:最小调用间隔与长冷却(旧 Mixin 消费,批次 4 移除)
	public final ForgeConfigSpec.IntValue mekCentrifugeEjectMinInterval;
	public final ForgeConfigSpec.IntValue mekCentrifugeEjectBusyThreshold;
	public final ForgeConfigSpec.IntValue mekCentrifugeEjectBusyCooldown;
	// 单 tick 最大弹出次数上限(旧 Mixin 消费,批次 4 移除)
	public final ForgeConfigSpec.IntValue mekCentrifugeEjectMaxPerTick;
	// 单 tick 最大 PB 配方操作数上限(0=无限制),防止 256× 加速下 CPU 过载
	public final ForgeConfigSpec.IntValue mekCentrifugeMaxOpsPerTick;
	// AE2 直接输出集成开关
	public final ForgeConfigSpec.BooleanValue mekCentrifugeAeOutputEnabled;
	// AE2 流体输出集成开关(独立于物品输出)
	public final ForgeConfigSpec.BooleanValue mekCentrifugeAeFluidOutputEnabled;
	// AE 网络能量输入集成
	public final ForgeConfigSpec.BooleanValue mekCentrifugeAeEnergyInputEnabled;
	public final ForgeConfigSpec.BooleanValue mekCentrifugePreferAppliedFluxOverAeEnergy;
	// 允许提取 AE2 原生能量
	public final ForgeConfigSpec.BooleanValue mekCentrifugeAeNativeEnergyInputEnabled;
	// AE2 输入拉取集成
	public final ForgeConfigSpec.BooleanValue mekCentrifugeAeInputEnabled;
	public final ForgeConfigSpec.IntValue mekCentrifugeAeInputRatePerTick;
	public final ForgeConfigSpec.IntValue mekCentrifugeAeInputIntervalTicks;
	public final ForgeConfigSpec.IntValue mekCentrifugeAeInputMinPages;
	public final ForgeConfigSpec.IntValue mekCentrifugePbUpgradeProductivityMaxCount;
	public final ForgeConfigSpec.IntValue mekCentrifugePbUpgradeTimeMaxCount;
	/** 稳定性升级最大安装数量(委托自 CentrifugeConfigSection,仅离心机生效,对齐 PB 原版上限 7) */
	public final ForgeConfigSpec.IntValue mekCentrifugePbUpgradeStabilityMaxCount;
	/** 通用机械:扩展 堆叠升级最大数量(委托自 CentrifugeConfigSection,由 ExtraUpgradeStackMixin 读取) */
	public final ForgeConfigSpec.IntValue mekCentrifugeMaxStackUpgrades;
	/** 离心机电力熔炼炉配方兼容总开关（委托自 CentrifugeConfigSection） */
	public final ForgeConfigSpec.BooleanValue mekCentrifugeSmeltingCompatEnabled;

	// ========== MEK通用机械蜂箱配置 —— 向后兼容委托字段 ==========
	public final ForgeConfigSpec.LongValue apiaryEnergyPerTick;
	public final ForgeConfigSpec.IntValue apiaryProcessingTime;
	public final ForgeConfigSpec.IntValue apiaryFluidTankCapacity;
	// 弹出策略(旧 Mixin 消费,批次 4 移除)
	public final ForgeConfigSpec.IntValue apiaryEjectDelay;
	public final ForgeConfigSpec.IntValue apiaryEjectDelayActive;
	public final ForgeConfigSpec.BooleanValue apiaryEjectMaxSpeedMode;
	public final ForgeConfigSpec.IntValue apiaryEjectMaxPerTick;
	public final ForgeConfigSpec.IntValue apiaryEjectBlockedThreshold;
	public final ForgeConfigSpec.IntValue apiaryEjectBlockedCooldown;
	// AE2 集成
	public final ForgeConfigSpec.BooleanValue apiaryAeOutputEnabled;
	public final ForgeConfigSpec.BooleanValue apiaryAeFluidOutputEnabled;
	public final ForgeConfigSpec.BooleanValue apiaryAeEnergyInputEnabled;
	// AE 网络能量优先级
	public final ForgeConfigSpec.BooleanValue apiaryPreferAppliedFluxOverAeEnergy;
	// 允许提取 AE2 原生能量
	public final ForgeConfigSpec.BooleanValue apiaryAeNativeEnergyInputEnabled;
	// PB升级上限
	public final ForgeConfigSpec.IntValue apiaryPbUpgradeProductivityMaxCount;
	public final ForgeConfigSpec.IntValue apiaryPbUpgradeTimeMaxCount;
	public final ForgeConfigSpec.IntValue apiaryPbUpgradeGeneSamplerMaxCount;
	public final ForgeConfigSpec.IntValue apiaryPbUpgradeBlockMaxCount;

	// ========== 外部物流互操作 —— 向后兼容委托字段（离心机与蜂箱通用）==========
	/** 产物直通相邻容器（跳过输出槽缓存） */
	public final ForgeConfigSpec.BooleanValue externalDirectContainerOutput;

	ServerConfig(
			ForgeConfigSpec.Builder builder,
			ForgeConfigSpec.Builder machineBuilder,
			ForgeConfigSpec.Builder capacityBuilder) {
		this.sections = new ConfigSectionRegistry();

		// ===== gameplay 文件 =====

		// 万象创世蜜蜂总开关
		myriadCreationsEnabled = builder
				.comment("启用万象创世蜜蜂", "false时禁用蜜蜂相关功能，仅保留通用机械资源蜜蜂机器")
				.define("myriadCreationsEnabled", true);

		balancePreset = builder
				.comment("全局平衡性预设",
						"基础、悖论无限或自定义。配置文件中的 BASIC、PARADOX_INFINITY、CUSTOM 是兼容存档所需的固定标识。",
						"切换预设不会删除机器中已经安装的升级。")
				.translation("productivebeesgenesis.configuration.balance.profile")
				.defineEnum("balanceProfile", BalanceConfig.DEFAULT_PRESET);

		builder.comment("平衡性规则；仅在全局平衡性配置为“自定义”时直接生效").push("balance");
		productivityUpgradeTiersExclusive = builder
				.comment("禁止产量升级 α/β/γ/Ω 在同一台机器中混装")
				.translation("productivebeesgenesis.configuration.balance.productivityUpgradeTiersExclusive")
				.define("productivityUpgradeTiersExclusive",
						BalanceConfig.DEFAULT_CUSTOM_PRODUCTIVITY_EXCLUSIVE);
		speedUpgradeTiersExclusive = builder
				.comment("禁止时间 I 与时间 II 两种速度升级在同一台机器中混装")
				.translation("productivebeesgenesis.configuration.balance.speedUpgradeTiersExclusive")
				.define("speedUpgradeTiersExclusive", BalanceConfig.DEFAULT_CUSTOM_SPEED_EXCLUSIVE);
		centrifugeProductivityAffectsOutput = builder
				.comment("离心机产量升级是否额外增加单次产出；无论此项如何，资源蜜蜂原版并行能力始终保留")
				.translation("productivebeesgenesis.configuration.balance.centrifugeProductivityAffectsOutput")
				.define("centrifugeProductivityAffectsOutput",
						BalanceConfig.DEFAULT_CUSTOM_CENTRIFUGE_OUTPUT);
		apiaryBeeGenesAffectWork = builder
				.comment("机械蜂箱是否根据蜜蜂的昼夜行为与天气耐受基因暂停工作")
				.translation("productivebeesgenesis.configuration.balance.apiaryBeeGenesAffectWork")
				.define("apiaryBeeGenesAffectWork",
						BalanceConfig.DEFAULT_CUSTOM_APIARY_BEE_GENES_AFFECT_WORK);
		builder.pop();

		builder.comment("万象创世蜜蜂过滤配置（存档级别）").push("myriad_creations_filter");

		myriadCreationsFilterMode = builder
				.comment("过滤模式", "可选模式：禁用、黑名单、白名单")
				.translation("productivebeesgenesis.configuration.myriad_creations_filter.filterMode")
				.defineEnum("filterMode", ModConfig.FilterMode.DISABLED);

		myriadCreationsFilteredBeeTypes = builder
				.comment("过滤的蜜蜂类型列表", "格式: modID:beeType")
				.defineList("filteredBeeTypes", List.of(), ModConfig::validateResourceLocationElement);

		builder.pop();

		// 万象创世蜜蜂属性配置(抽取至 BeeAttributeConfigSection,Task 12 委托至 ConfigSectionRegistry)
		BeeAttributeConfigSection beeAttributes = this.sections.registerBeeAttributes(builder);
		// 向后兼容委托字段赋值(指向同一 ConfigValue 实例,零开销)
		this.primaryColor = beeAttributes.primaryColor;
		this.secondaryColor = beeAttributes.secondaryColor;
		this.particleColor = beeAttributes.particleColor;
		this.glowColor = beeAttributes.glowColor;
		this.flowerItem = beeAttributes.flowerItem;
		this.weatherTolerance = beeAttributes.weatherTolerance;
		this.temper = beeAttributes.temper;
		this.behavior = beeAttributes.behavior;
		this.endurance = beeAttributes.endurance;
		this.productivity = beeAttributes.productivity;
		this.createComb = beeAttributes.createComb;
		this.size = beeAttributes.size;
		this.speed = beeAttributes.speed;
		this.attack = beeAttributes.attack;
		this.breedingItem = beeAttributes.breedingItem;
		this.breedingItemCount = beeAttributes.breedingItemCount;
		this.selfbreed = beeAttributes.selfbreed;
		this.waterproof = beeAttributes.waterproof;
		this.fireproof = beeAttributes.fireproof;

		builder.comment("蜜蜂获得方式配置").push("bee_acquisition");

		builder.push("fishing").comment("钓鱼获得万象创世蜜蜂");
		fishingEnabled = builder
				.comment("是否启用钓鱼获得万象创世蜜蜂")
				.translation("productivebeesgenesis.configuration.bee_acquisition.fishingEnabled")
				.define("enabled", false);
		fishingChance = builder
				.comment("钓鱼获得蜜蜂的概率（0.0~1.0）")
				.translation("productivebeesgenesis.configuration.bee_acquisition.fishingChance")
				.defineInRange("chance", 0.1D, 0.0D, 1.0D);
		fishingBiomes = builder
				.comment("可钓鱼获得蜜蜂的群系列表")
				.translation("productivebeesgenesis.configuration.bee_acquisition.fishingBiomes")
				.defineList("biomes", List.of(
						"minecraft:ocean",
						"minecraft:deep_ocean",
						"minecraft:cold_ocean",
						"minecraft:deep_cold_ocean",
						"minecraft:frozen_ocean",
						"minecraft:deep_frozen_ocean",
						"minecraft:warm_ocean",
						"minecraft:lukewarm_ocean",
						"minecraft:deep_lukewarm_ocean"
				), ModConfig::validateResourceLocationElement);
		builder.pop(); // fishing

		builder.push("breeding").comment("繁殖获得万象创世蜜蜂");
		breedingEnabled = builder
				.comment("是否启用繁殖获得万象创世蜜蜂")
				.translation("productivebeesgenesis.configuration.bee_acquisition.breedingEnabled")
				.define("enabled", true);
		breedingParent1 = builder
				.comment("亲代蜜蜂1（注册名，如 productivebees:myriadcreations）")
				.translation("productivebeesgenesis.configuration.bee_acquisition.breedingParent1")
				.define("parent1", "productivebees:myriadcreations", ModConfig::validateResourceLocation);
		breedingParent2 = builder
				.comment("亲代蜜蜂2（注册名）")
				.translation("productivebeesgenesis.configuration.bee_acquisition.breedingParent2")
				.define("parent2", "productivebees:myriadcreations", ModConfig::validateResourceLocation);
		builder.pop(); // breeding

		builder.push("spawning").comment("蜂巢生成万象创世蜜蜂");
		spawningEnabled = builder
				.comment("是否启用蜂巢自然生成万象创世蜜蜂")
				.translation("productivebeesgenesis.configuration.bee_acquisition.spawningEnabled")
				.define("enabled", false);
		spawningNest = builder
				.comment("生成蜜蜂的蜂巢方块（如 productivebees:stone_nest）")
				.translation("productivebeesgenesis.configuration.bee_acquisition.spawningNest")
				.define("nest", "productivebees:stone_nest", ModConfig::validateResourceLocation);
		spawningBiomes = builder
				.comment("生成蜜蜂的群系（标签或群系ID，如 #c:is_plains）")
				.translation("productivebeesgenesis.configuration.bee_acquisition.spawningBiomes")
				.define("biomes", "#c:is_plains", ModConfig::validateBiomeSpec);
		builder.pop(); // spawning

		builder.pop(); // bee_acquisition

		builder.comment("蜜蜂转化配方配置（用其他物品转化获得万象创世）").push("bee_conversion");
		conversionEnabled = builder
				.comment("是否启用万象创世的物品转化配方")
				.translation("productivebeesgenesis.configuration.bee_conversion.conversionEnabled")
				.define("enabled", true);
		conversionSource = builder
				.comment("源蜜蜂类型（注册名，如 minecraft:bee）")
				.translation("productivebeesgenesis.configuration.bee_conversion.conversionSource")
				.define("source", "minecraft:bee", ModConfig::validateResourceLocation);
		conversionResult = builder
				.comment("转化目标蜜蜂（注册名，如 productivebees:myriadcreations）")
				.translation("productivebeesgenesis.configuration.bee_conversion.conversionResult")
				.define("result", "productivebees:myriadcreations", ModConfig::validateResourceLocation);
		conversionItem = builder
				.comment("转化所需物品ID（如 minecraft:stick）")
				.translation("productivebeesgenesis.configuration.bee_conversion.conversionItem")
				.define("item", "minecraft:stick", ModConfig::validateResourceLocation);
		conversionChance = builder
				.comment("转化概率（0.0~1.0）")
				.translation("productivebeesgenesis.configuration.bee_conversion.conversionChance")
				.defineInRange("chance", 1.0D, 0.0D, 1.0D);
		apiaryItemConversionEnabled = builder
				.comment("允许机械蜂箱使用 PB 物品转化配方")
				.translation("productivebeesgenesis.configuration.bee_conversion.apiaryItemConversionEnabled")
				.define("apiaryItemConversionEnabled", true);
		apiaryBlockConversionEnabled = builder
				.comment("允许机械蜂箱使用 PB 方块转化配方")
				.translation("productivebeesgenesis.configuration.bee_conversion.apiaryBlockConversionEnabled")
				.define("apiaryBlockConversionEnabled", true);
		builder.pop(); // bee_conversion

		builder.comment("蜜蜂产出配方配置（万象创世蜜脾产出参数）").push("bee_produce");
		produceEnabled = builder
				.comment("是否启用万象创世的蜜脾产出")
				.translation("productivebeesgenesis.configuration.bee_produce.produceEnabled")
				.define("enabled", true);
		produceOutputItem = builder
				.comment("产出物品ID")
				.translation("productivebeesgenesis.configuration.bee_produce.produceOutputItem")
				.define("outputItem", "productivebees:configurable_honeycomb", ModConfig::validateResourceLocation);
		produceOutputMin = builder
				.comment("最小产出数量")
				.translation("productivebeesgenesis.configuration.bee_produce.produceOutputMin")
				.defineInRange("outputMin", 1, 1, 64);
		produceOutputMax = builder
				.comment("最大产出数量")
				.translation("productivebeesgenesis.configuration.bee_produce.produceOutputMax")
				.defineInRange("outputMax", 1, 1, 64);
		produceOutputChance = builder
				.comment("产出概率（0.0~1.0）")
				.translation("productivebeesgenesis.configuration.bee_produce.produceOutputChance")
				.defineInRange("outputChance", 1.0D, 0.0D, 1.0D);

		myriadProduceThrottlePerTick = builder
				.comment("每tick每只蜜蜂最大产物事件数", "0=无限制，高倍加速时降低CPU")
				.translation("productivebeesgenesis.configuration.bee_produce.myriadProduceThrottlePerTick")
				.defineInRange("myriadProduceThrottlePerTick", 0, 0, 20);
		builder.pop(); // bee_produce

		builder.comment("高级蜂箱性能优化（缓解大量模拟蜂箱导致的CPU压力）").push("advanced_beehive");

		advancedBeehiveSimulateCooldown = builder
				.comment("模拟行为查询冷却(tick)", "0=原版，1-5降低高倍加速CPU开销")
				.translation("productivebeesgenesis.configuration.advanced_beehive.simulateCooldown")
				.defineInRange("simulateCooldown", 0, 0, 20);

		advancedBeehiveSaveInterval = builder
				.comment("NBT保存间隔(tick)", "默认20，值越大性能越好但宕机风险增加")
				.translation("productivebeesgenesis.configuration.advanced_beehive.saveInterval")
				.defineInRange("saveInterval", 20, 1, 200);

		maxBatchTicksPerTick = builder
				.comment("批量加速每个真实游戏刻的虚拟 tick 上限",
						"默认 1024；实际预算会根据当前 MSPT 自动降级，超出部分进入短期挂账")
				.translation("productivebeesgenesis.configuration.advanced_beehive.maxBatchTicksPerTick")
				.defineInRange("maxBatchTicksPerTick", 1024, 1, 1024);

		builder.pop(); // advanced_beehive

		// ===== machines / capacities 文件 =====

		// MEK离心机配置(抽取至 CentrifugeConfigSection,机器参数入 machines,容量矩阵入 capacities)
		CentrifugeConfigSection centrifuge = this.sections.registerCentrifuge(
				machineBuilder, capacityBuilder);
		// 向后兼容委托字段赋值(指向同一 ConfigValue 实例,零开销)
		this.mekCentrifugeEnergyPerTick = centrifuge.mekCentrifugeEnergyPerTick;
		this.mekCentrifugeEnergyStorage = centrifuge.mekCentrifugeEnergyStorage;
		this.mekCentrifugeProcessingTime = centrifuge.mekCentrifugeProcessingTime;
		this.mekCentrifugeEjectDelay = centrifuge.mekCentrifugeEjectDelay;
		this.mekCentrifugeEjectDelayActive = centrifuge.mekCentrifugeEjectDelayActive;
		this.mekCentrifugeFluidTankCapacity = centrifuge.mekCentrifugeFluidTankCapacity;
		this.mekCentrifugeMultiFluidTank = centrifuge.mekCentrifugeMultiFluidTank;
		// v2.0.9: 每种流体类型最大占用槽位数(配额机制)
		this.mekCentrifugeMaxTanksPerFluid = centrifuge.mekCentrifugeMaxTanksPerFluid;
		this.mekCentrifugeFluidEjectRate = centrifuge.mekCentrifugeFluidEjectRate;
		this.mekCentrifugeCombBlockMultiplier = centrifuge.mekCentrifugeCombBlockMultiplier;
		this.mekCentrifugeMaxExtractPerTick = centrifuge.mekCentrifugeMaxExtractPerTick;
		this.mekCentrifugeEjectBlockedThreshold = centrifuge.mekCentrifugeEjectBlockedThreshold;
		this.mekCentrifugeEjectBlockedCooldown = centrifuge.mekCentrifugeEjectBlockedCooldown;
		this.mekCentrifugeEjectSkipUnchanged = centrifuge.mekCentrifugeEjectSkipUnchanged;
		this.mekCentrifugeEjectSkipTicks = centrifuge.mekCentrifugeEjectSkipTicks;
		this.mekCentrifugeEjectMaxSpeedMode = centrifuge.mekCentrifugeEjectMaxSpeedMode;
		this.mekCentrifugeEjectMinInterval = centrifuge.mekCentrifugeEjectMinInterval;
		this.mekCentrifugeEjectBusyThreshold = centrifuge.mekCentrifugeEjectBusyThreshold;
		this.mekCentrifugeEjectBusyCooldown = centrifuge.mekCentrifugeEjectBusyCooldown;
		this.mekCentrifugeEjectMaxPerTick = centrifuge.mekCentrifugeEjectMaxPerTick;
		this.mekCentrifugeMaxOpsPerTick = centrifuge.mekCentrifugeMaxOpsPerTick;
		// 堆叠倍率/流体罐倍率已迁移至 capacities 子段,运行时经 FactoryTierConfigService 快照读取
		this.mekCentrifugeAeOutputEnabled = centrifuge.mekCentrifugeAeOutputEnabled;
		this.mekCentrifugeAeFluidOutputEnabled = centrifuge.mekCentrifugeAeFluidOutputEnabled;
		// v2.0.0: AE 网络能量输入集成 — 向后兼容委托字段赋值
		this.mekCentrifugeAeEnergyInputEnabled = centrifuge.mekCentrifugeAeEnergyInputEnabled;
		this.mekCentrifugePreferAppliedFluxOverAeEnergy = centrifuge.mekCentrifugePreferAppliedFluxOverAeEnergy;
		this.mekCentrifugeAeNativeEnergyInputEnabled = centrifuge.mekCentrifugeAeNativeEnergyInputEnabled;
		// AE2 输入拉取集成 — 向后兼容委托字段赋值(指向同一 ConfigValue 实例,零开销)
		this.mekCentrifugeAeInputEnabled = centrifuge.mekCentrifugeAeInputEnabled;
		this.mekCentrifugeAeInputRatePerTick = centrifuge.mekCentrifugeAeInputRatePerTick;
		this.mekCentrifugeAeInputIntervalTicks = centrifuge.mekCentrifugeAeInputIntervalTicks;
		this.mekCentrifugeAeInputMinPages = centrifuge.mekCentrifugeAeInputMinPages;
		// PB升级上限委托字段赋值(指向同一 ConfigValue 实例,零开销)
		this.mekCentrifugePbUpgradeProductivityMaxCount = centrifuge.mekCentrifugePbUpgradeProductivityMaxCount;
		this.mekCentrifugePbUpgradeTimeMaxCount = centrifuge.mekCentrifugePbUpgradeTimeMaxCount;
		this.mekCentrifugePbUpgradeStabilityMaxCount = centrifuge.mekCentrifugePbUpgradeStabilityMaxCount;
		// 通用机械:扩展 堆叠升级上限委托字段赋值(Task 13,指向同一 ConfigValue 实例,零开销)
		this.mekCentrifugeMaxStackUpgrades = centrifuge.mekCentrifugeMaxStackUpgrades;
		// 熔炉配方兼容总开关委托字段赋值（指向同一 ConfigValue 实例，零开销）
		this.mekCentrifugeSmeltingCompatEnabled = centrifuge.mekCentrifugeSmeltingCompatEnabled;

		// MEK通用机械蜂箱配置(抽取至 ApiaryConfigSection,机器参数入 machines,容量矩阵入 capacities)
		ApiaryConfigSection apiary = this.sections.registerApiary(machineBuilder, capacityBuilder);
		// 向后兼容委托字段赋值(指向同一 ConfigValue 实例,零开销)
		this.apiaryEnergyPerTick = apiary.apiaryEnergyPerTick;
		this.apiaryProcessingTime = apiary.apiaryProcessingTime;
		this.apiaryFluidTankCapacity = apiary.apiaryFluidTankCapacity;
		this.apiaryEjectDelay = apiary.apiaryEjectDelay;
		this.apiaryEjectDelayActive = apiary.apiaryEjectDelayActive;
		this.apiaryEjectMaxSpeedMode = apiary.apiaryEjectMaxSpeedMode;
		this.apiaryEjectMaxPerTick = apiary.apiaryEjectMaxPerTick;
		this.apiaryEjectBlockedThreshold = apiary.apiaryEjectBlockedThreshold;
		this.apiaryEjectBlockedCooldown = apiary.apiaryEjectBlockedCooldown;
		this.apiaryAeOutputEnabled = apiary.apiaryAeOutputEnabled;
		this.apiaryAeFluidOutputEnabled = apiary.apiaryAeFluidOutputEnabled;
		this.apiaryAeEnergyInputEnabled = apiary.apiaryAeEnergyInputEnabled;
		this.apiaryPreferAppliedFluxOverAeEnergy = apiary.apiaryPreferAppliedFluxOverAeEnergy;
		this.apiaryAeNativeEnergyInputEnabled = apiary.apiaryAeNativeEnergyInputEnabled;
		this.apiaryPbUpgradeProductivityMaxCount = apiary.apiaryPbUpgradeProductivityMaxCount;
		this.apiaryPbUpgradeTimeMaxCount = apiary.apiaryPbUpgradeTimeMaxCount;
		this.apiaryPbUpgradeGeneSamplerMaxCount = apiary.apiaryPbUpgradeGeneSamplerMaxCount;
		this.apiaryPbUpgradeBlockMaxCount = apiary.apiaryPbUpgradeBlockMaxCount;

		// 外部物流互操作配置（离心机与蜂箱通用，注册在机器参数文件末尾）
		ExternalLogisticsConfigSection externalLogistics =
				this.sections.registerExternalLogistics(machineBuilder);
		this.externalDirectContainerOutput = externalLogistics.externalDirectContainerOutput;
	}
}
