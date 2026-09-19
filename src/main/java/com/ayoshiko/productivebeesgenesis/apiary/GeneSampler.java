package com.ayoshiko.productivebeesgenesis.apiary;

import cy.jdkdigital.productivebees.common.item.Gene;
import cy.jdkdigital.productivebees.util.BeeAttribute;
import cy.jdkdigital.productivebees.util.BeeAttributes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
	 * 基因采样器产出处理器
	 * <br/>
	 * 从 {@link BeeProduceProcessor} 抽取的基因采样逻辑，负责生成 TYPE 基因物品。
	 * <p>
	 * 复刻 PB 原版 {@code AdvancedBeehiveBlockEntity#beeReleasePostAction} 的基因采样语义：
	 * <ul>
	 *   <li>每次蜜蜂产出独立判定，命中概率 = {@link #SAMPLER_BASE_CHANCE} × 采样器数量</li>
	 *   <li>命中时生成 1 个 TYPE 基因，purity = random.nextInt(4) + 1（范围 1-4）</li>
	 *   <li>基因物品格式：{@link Gene#getStack(String, int)}</li>
	 * </ul>
	 * <p>
	 * 与 PB 原版的差异：机械蜂箱虽无实体蜜蜂，但可从蜜蜂 NBT 的
	 * neoforge:attachments.productivebees:attributes_handler 读取 GeneAttribute 属性类基因
	 * （参考 BeeTooltipRenderer.getAttributesCompound）。当前 GeneSampler 仅生成 TYPE 基因，
	 * PRODUCTIVITY 基因加成已在 BeeProduceProcessor.buildAdjustedItems 中应用，
	 * ENDURANCE/TEMPER 不适用（无实体蜜蜂），BEHAVIOR/WEATHER_TOLERANCE 待后续实现。
	 * TYPE 基因的 type 字段使用 {@link BeeNbtHelper#resolveBeeTypeKey} 解析的 ResourceLocation 字符串，
	 * 与 PB 原版 {@code ConfigurableBee#getBeeType().toString()} 语义一致。
	 * <p>
	 * 性能保护：当 produceCount 超过 {@link #GENE_SAMPLER_MAX_LOOP} 时，改用批量概率聚合计算，
	 * 避免高倍加速场景下（STACK满级+多蜜蜂+20tick累积）循环暴增导致服务端假死。
	 * 批量计算公式：expectedHits = produceCount * chance，使用正态分布近似添加随机扰动。
	 * <p>
	 * 线程安全：仅服务端 tick 线程调用，level.getRandom() 单线程访问无需同步。
	 */
public class GeneSampler {

	/**
	 * 基因采样器单次产出概率基数（与 PB 原版 ProductiveBeesConfig.UPGRADES.samplerChance 默认值 0.05 一致）
	 * <br/>
	 * 实际概率 = {@link #SAMPLER_BASE_CHANCE} × 采样器数量。
	 * PB 原版 {@code AdvancedBeehiveBlockEntity#beeReleasePostAction} 中：
	 * {@code level.random.nextFloat() <= samplerChance * samplerUpgrades}
	 */
	private static final float SAMPLER_BASE_CHANCE = 0.05f;

	/**
	 * 基因采样器独立伯努利判定的循环上限
	 * <br/>
	 * 当累积产出次数超过此值时，改用批量概率聚合计算（正态分布近似），
	 * 避免高倍加速场景下循环暴增导致服务端假死。
	 * 默认值 1000 足以覆盖正常场景（20 tick 累积 × 8 只蜜蜂 × 6 倍加速 ≈ 960）。
	 */
	private static final int GENE_SAMPLER_MAX_LOOP = 1000;

	/**
	 * 生成基因采样器产出的 TYPE 基因物品
	 * <br/>
	 * 复刻 PB 原版 {@code AdvancedBeehiveBlockEntity#beeReleasePostAction} 的基因采样逻辑：
	 * <ul>
	 *   <li>每次蜜蜂产出独立判定，命中概率 = {@link #SAMPLER_BASE_CHANCE} × 采样器数量</li>
	 *   <li>命中时生成 1 个 TYPE 基因，purity = random.nextInt(4) + 1（范围 1-4，与 PB 原版一致）</li>
	 *   <li>基因物品格式：{@link Gene#getStack(String, int)}，使用蜜蜂类型键字符串作为 type</li>
	 * </ul>
	 * <p>
	 * 性能保护：当 produceCount 超过 {@link #GENE_SAMPLER_MAX_LOOP} 时，改用批量概率聚合计算，
	 * 避免高倍加速场景下循环暴增导致服务端假死。
	 * 批量计算公式：expectedHits = produceCount * chance，使用正态分布近似添加随机扰动。
	 *
	 * @param beeTypeKey   蜜蜂类型键（如 productivebees:iron）
	 * @param produceCount 累积产出次数（独立伯努利判定次数）
	 * @param samplerCount 基因采样器安装数量（来自 {@link ApiaryUpgradeHandler#getGeneSamplerCount}）
	 * @param level        世界实例（随机数源）
	 * @param beeData      蜜蜂 NBT 数据（来自 {@link BeeSlot#getBeeData()}），用于读取真实属性值
	 * @return 基因物品列表（可能为空，未命中时返回空列表）
	 */
	public List<ItemStack> generateGeneSamples(ResourceLocation beeTypeKey, int produceCount,
			int samplerCount, Level level, CompoundTag beeData,
			boolean typeOnly, boolean fullPurity) {
		if (beeTypeKey == null || produceCount <= 0 || level == null || samplerCount <= 0) {
			return List.of();
		}
		// 单次产出概率 = 基础概率 × 采样器数量（与 PB 原版 samplerChance * samplerUpgrades 一致）
		float chance = SAMPLER_BASE_CHANCE * samplerCount;
		if (chance <= 0.0f) {
			return List.of();
		}
		// 蜜蜂类型字符串 — 与 PB 原版 ConfigurableBee#getBeeType().toString() 格式一致
		String typeString = beeTypeKey.toString();
		int[] purityCounts = new int[4];
		var random = level.getRandom();

		if (produceCount <= GENE_SAMPLER_MAX_LOOP) {
			// 正常场景：独立伯努利判定，保留完整随机性
			for (int i = 0; i < produceCount; i++) {
				if (random.nextFloat() <= chance) {
					if (fullPurity) {
						purityCounts[3]++; // 固定纯度 4（最大）
					} else {
						purityCounts[random.nextInt(4)]++;
					}
				}
			}
		} else {
			// 高倍加速场景：批量概率聚合，避免循环暴增
			float effectiveChance = Math.min(1.0f, chance);
			double expectedHits = produceCount * effectiveChance;
			double variance = expectedHits * (1.0 - effectiveChance);
			double stddev = Math.sqrt(variance);
			double u1 = random.nextDouble();
			double u2 = random.nextDouble();
			double z = Math.sqrt(-2.0 * Math.log(u1 + 1e-10)) * Math.cos(2.0 * Math.PI * u2);
			long roundedHits = Math.round(expectedHits + z * stddev);
			int hitCount = (int) Math.min(produceCount, Math.max(0L, roundedHits));
			if (fullPurity) {
				// 固定纯度 4：所有命中归入 purityCounts[3]
				purityCounts[3] = hitCount;
			} else {
				int perPurity = hitCount / purityCounts.length;
				Arrays.fill(purityCounts, perPurity);
				int remainder = hitCount % purityCounts.length;
				int purityStart = random.nextInt(purityCounts.length);
				for (int i = 0; i < remainder; i++) {
					purityCounts[(purityStart + i) % purityCounts.length]++;
				}
			}
		}

		// 读取蜜蜂真实属性 — 采样出的属性基因值反映蜜蜂本身属性，而非随机
		GeneSampleProfile profile = GeneSampleProfile.fromBeeData(beeData);
		BeeAttribute<Integer>[] attributes = new BeeAttribute[]{
				BeeAttributes.PRODUCTIVITY,
				BeeAttributes.ENDURANCE,
				BeeAttributes.TEMPER,
				BeeAttributes.BEHAVIOR,
				BeeAttributes.WEATHER_TOLERANCE
		};

		int totalSlots = purityCounts.length + attributes.length * purityCounts.length;
		List<ItemStack> genes = new ArrayList<>(totalSlots);
		// 1) TYPE 基因（蜜蜂种类）
		for (int purityIndex = 0; purityIndex < purityCounts.length; purityIndex++) {
			int count = purityCounts[purityIndex];
			if (count <= 0) continue;
			ItemStack gene = Gene.getStack(typeString, purityIndex + 1);
			gene.setCount(count);
			genes.add(gene);
		}
		// 2) 属性基因（生产力/耐力/性情/行为/天气耐受）— 值来自蜜蜂真实 NBT
		// typeOnly=true 时跳过属性基因生成，仅保留 TYPE 基因
		if (!typeOnly) {
			for (BeeAttribute<Integer> attr : attributes) {
				int value = profile.value(attr);
				for (int purityIndex = 0; purityIndex < purityCounts.length; purityIndex++) {
					int count = purityCounts[purityIndex];
					if (count <= 0) continue;
					ItemStack gene = Gene.getStack(attr, value, count, purityIndex + 1);
					genes.add(gene);
				}
			}
		}
		return genes;
	}
}
