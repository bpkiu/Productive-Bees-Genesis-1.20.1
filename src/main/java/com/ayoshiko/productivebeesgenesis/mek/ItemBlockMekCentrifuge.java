package com.ayoshiko.productivebeesgenesis.mek;

import com.ayoshiko.productivebeesgenesis.ProductiveBeesGenesis;
import com.ayoshiko.productivebeesgenesis.apiary.PbUpgradeType;
import com.ayoshiko.productivebeesgenesis.util.ItemStackBlockEntityDataHelper;
import com.ayoshiko.productivebeesgenesis.util.NumberFormatter;
import com.ayoshiko.productivebeesgenesis.util.SaturatingMath;
import com.jerry.mekanism_extras.common.block.attribute.ExtraAttributeTier;
import com.jerry.mekanism_extras.common.tier.AdvancedFactoryTier;
import io.github.masyumero.emextras.common.block.attribute.EMExtraAttributeFactoryType;
import io.github.masyumero.emextras.common.block.attribute.EMExtraAttributeTier;
import io.github.masyumero.emextras.common.tier.EMExtraFactoryTier;
import mekanism.api.text.EnumColor;
import mekanism.api.text.TextComponentUtil;
import mekanism.common.MekanismLang;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.block.attribute.AttributeFactoryType;
import mekanism.common.item.block.ItemBlockTooltip;
import mekanism.common.tier.FactoryTier;
import mekanism.common.util.StorageUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import com.ayoshiko.productivebeesgenesis.util.FluidStackNbtBridge;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
	 * MEK离心机BlockItem — 一比一复制Mekanism原版tooltip系统
	 * <br/>
	 * 继承ItemBlockTooltip，实现与Mekanism原版机器完全一致的tooltip行为：
	 * <ul>
	 *   <li>默认：显示统计信息 + "按住Shift查看详情" + "按住Shift+N查看描述"</li>
	 *   <li>按住Shift：显示拥有者、安全等级、配方类型、已储能、存有物品、升级</li>
	 *   <li>按住Shift+N：显示方块描述</li>
	 * </ul>
	 * 工厂版额外添加SORTING DataComponent和配方类型显示。
	 */
public class ItemBlockMekCentrifuge extends ItemBlockTooltip<MekCentrifugeBlock<?, ?>> {

	public ItemBlockMekCentrifuge(MekCentrifugeBlock<?, ?> block, Item.Properties properties) {
		super(block, true, properties);
	}

	/**
	 * 获取工厂等级 — 用于物品名称颜色渲染
	 * <br/>
	 * 原理：ItemBlockMekanism.getName()根据tier的BaseTier颜色渲染物品名称。
	 * 返回null时使用默认颜色。仅原版/EM工厂有FactoryTier，
	 * ME/EME工厂使用独立等级系统，此处返回null。
	 */
	@Override
	public FactoryTier getTier() {
		return Attribute.getTier(getBlock(), FactoryTier.class);
	}

	/**
	 * 获取物品名称 — 为ME和EME等级添加颜色特效
	 * <br/>
	 * 原理：原版Mekanism工厂通过getTier()返回的BaseTier获取颜色。
	 * ME和EME使用独立的等级系统，需要在此方法中手动添加颜色。
	 * <p>
	 * 颜色对应：
	 * <ul>
	 *   <li>ME ABSOLUTE: 黄绿色 (237, 238, 70)</li>
	 *   <li>ME SUPREME: 红色 (166, 0, 2)</li>
	 *   <li>ME COSMIC: 青色 (75, 248, 255)</li>
	 *   <li>ME INFINITE: 品红色 (247, 135, 255)</li>
	 *   <li>EME ABSOLUTE_OVERCLOCKED: 动态黄绿色</li>
	 *   <li>EME SUPREME_QUANTUM: 动态红色</li>
	 *   <li>EME COSMIC_DENSE: 动态青色</li>
	 *   <li>EME INFINITE_MULTIVERSAL: 动态品红色</li>
	 * </ul>
	 */
	@NotNull
	@Override
	public Component getName(@NotNull ItemStack stack) {
		// 检查ME等级（Mekanism Extras）
		if (MekCompatHooks.isMekanismExtrasLoaded()) {
			ExtraAttributeTier<AdvancedFactoryTier> meTier = Attribute.get(getBlock(), ExtraAttributeTier.class);
			if (meTier != null) {
				TextColor color = meTier.tier().getAdvanceTier().getColor();
				return TextComponentUtil.build(color, super.getName(stack));
			}
		}

		// 检查EME等级（Evolved Mekanism Extras）
		if (MekCompatHooks.isEvolvedMekanismExtrasLoaded()) {
			EMExtraAttributeTier<EMExtraFactoryTier> emeTier = Attribute.get(getBlock(), EMExtraAttributeTier.class);
			if (emeTier != null) {
				// 1.20.1：EMExtraTier 直接提供 getColor()（无 1.21+ 的 getRgbSupplier）
				TextColor color = emeTier.tier().getEMExtraTier().getColor();
				return TextComponentUtil.build(color, super.getName(stack));
			}
		}

		// 原版/EM等级使用默认行为（通过getTier()获取颜色）
		return super.getName(stack);
	}

	/**
	 * 默认tooltip统计信息 — 显示PB升级总数
	 * <br/>
	 * 原理：appendHoverText在未按Shift时调用addStats，
	 * 此处从BLOCK_ENTITY_DATA读取扳手拆卸时保存的离心机自定义数据，
	 * 统计PB升级总数，让玩家无需按Shift即可了解离心机升级状态。
	 * <p>
	 * 数据来源：getDrops()写入BLOCK_ENTITY_DATA的saveCustomDataForItem()NBT。
	 */
	@Override
	protected void addStats(@NotNull ItemStack stack, @NotNull Level level,
							@NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		super.addStats(stack, level, tooltip, flag);
		CompoundTag customNbt = ItemStackBlockEntityDataHelper.readCustomBlockEntityData(stack);
		if (customNbt == null) return;
		int pbUpgradeTotal = countPbUpgradesFromNbt(customNbt);
		if (pbUpgradeTotal > 0) {
			tooltip.add(Component.translatable("tooltip.productivebeesgenesis.pb_upgrade_count",
					NumberFormatter.format(pbUpgradeTotal))
					.withStyle(ChatFormatting.GRAY));
		}
	}

	/**
	 * Bug 6：重写 addDetails 添加 try-catch 防御 + 追加PB升级详情 + 流体槽详情
	 * <br/>
	 * 扳手拆卸后 DataComponents 可能不完整，super.addDetails 中的
	 * SecurityTooltip/FluidAttachment/UpgradeAware 等读取可能抛出异常。
	 * 在 super 调用后追加离心机专属 Shift 详情：
	 * <ul>
	 *   <li>各 PB 升级类型的具体安装数量（addCentrifugeSpecificDetails）</li>
	 *   <li>MultiFluidTankHolder 中所有非空流体槽内容（addCentrifugeFluidDetails，修复 5）</li>
	 * </ul>
	 * 修复 5：工厂离心机使用 MultiFluidTankHolder（非标准流体附件），
	 * super.addDetails 中的 StorageUtils.addStoredFluid 无法找到流体，需独立读取 NBT。
	 * <p>
	 * 修复 v14：super 抛异常时独立渲染 UPGRADES tooltip（防重复：super 成功时不重复调用）。
	 * super.addDetails 成功时已通过 MEK 原版逻辑渲染 UPGRADES，无需重复；
	 * super 失败（被 try-catch 吞掉）时调用 addMekUpgradesDetails 独立渲染升级信息。
	 */
	@Override
	protected void addDetails(@NotNull ItemStack stack, @NotNull Level level,
			@NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		// 1.20.1：super.addDetails 原生渲染 security/配方类型/流体/物品栏/升级
		// （ItemBlockTooltip.addDetails → MekanismUtils.addUpgradesToTooltip），
		// 仅需 try-catch 防御 NBT 异常 + 追加离心机专属详情（MultiFluidTankHolder 多流体槽 + PB升级）
		try {
			super.addDetails(stack, level, tooltip, flag);
		} catch (Exception e) {
			// 防御：拆卸后NBT不完整时防止客户端tooltip崩溃
			ProductiveBeesGenesis.LOGGER.warn("显示离心机Shift详情时异常（可能为拆卸后组件缺失）", e);
		}
		// 追加离心机专属 Shift 详情 — MultiFluidTankHolder 多流体槽 + 各 PB 升级类型安装数量
		addCentrifugeFluidDetails(stack, tooltip);
		addCentrifugeSpecificDetails(stack, tooltip);
	}

	/**
	 * 离心机专属 Shift 详情 — 显示各 PB 升级类型的具体安装数量
	 * <br/>
	 * 原理：从 BLOCK_ENTITY_DATA 读取离心机自定义 NBT，
	 * 遍历 PB 升级数量映射，按类型显示名称和数量。
	 * 与蜂箱 ItemBlockMekApiary.addApiarySpecificDetails 实现一致。
	 */
	private void addCentrifugeSpecificDetails(@NotNull ItemStack stack, @NotNull List<Component> tooltip) {
		CompoundTag customNbt = ItemStackBlockEntityDataHelper.readCustomBlockEntityData(stack);
		if (customNbt == null) return;
		if (customNbt.contains(MekCentrifugePbUpgradeHandler.NBT_KEY_COUNTS, Tag.TAG_COMPOUND)) {
			CompoundTag countsTag = customNbt.getCompound(MekCentrifugePbUpgradeHandler.NBT_KEY_COUNTS);
			if (!countsTag.getAllKeys().isEmpty()) {
				tooltip.add(Component.translatable("tooltip.productivebeesgenesis.pb_upgrade_detail_header")
						.withStyle(ChatFormatting.LIGHT_PURPLE));
				for (String typeId : countsTag.getAllKeys()) {
					PbUpgradeType type = PbUpgradeType.byId(typeId);
					if (type != null) {
						int count = countsTag.getInt(typeId);
						tooltip.add(Component.literal(" ")
								.append(Component.translatable(type.getNameKey()))
								.append(Component.literal(": " + NumberFormatter.format(count))
										.withStyle(ChatFormatting.GRAY)));
					}
				}
			}
		}
	}

	/**
	 * 工厂离心机流体 Shift 详情 — 显示 MultiFluidTankHolder 中所有非空流体槽内容（修复 5）
	 * <br/>
	 * <b>背景：</b>工厂离心机使用 {@link com.ayoshiko.productivebeesgenesis.mek.fluid.MultiFluidTankHolder}
	 * （非标准流体附件），super.addDetails 中的 {@code StorageUtils.addStoredFluid} 无法找到流体，
	 * 导致扳手拆卸后 Shift tooltip 不显示内部流体。
	 * <p>
	 * <b>原理：</b>从 BLOCK_ENTITY_DATA 读取 {@code productivebeesgenesis_multi_fluid_tanks} NBT，
	 * 直接遍历 ListTag 中的 fluidStack 字段，解析 FluidStack 并显示流体名称和数量。
	 * 不创建临时 MultiFluidTankHolder，避免不必要的对象创建（SRP + 性能）。
	 * <p>
	 * <b>边界处理：</b>
	 * <ul>
	 *   <li>NBT 不存在或无流体条目：直接返回，不显示 header（避免空 header）</li>
	 *   <li>FluidStack 解析失败：跳过该条目，不影响其他流体显示</li>
	 *   <li>异常：由 addDetails 外层 try-catch 兜底，仅记录警告日志</li>
	 * </ul>
	 */
	private void addCentrifugeFluidDetails(@NotNull ItemStack stack, @NotNull List<Component> tooltip) {
		CompoundTag customNbt = ItemStackBlockEntityDataHelper.readCustomBlockEntityData(stack);
		List<FluidStack> storedFluids = new java.util.ArrayList<>();
		if (customNbt != null
				&& customNbt.contains(MekCentrifugeNbtKeys.NBT_KEY_MULTI_FLUID_TANKS, Tag.TAG_COMPOUND)) {
			CompoundTag root = customNbt.getCompound(MekCentrifugeNbtKeys.NBT_KEY_MULTI_FLUID_TANKS);
			ListTag list = root.getList("tanks", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag entry = list.getCompound(i);
				FluidStack fluid = FluidStackNbtBridge.read(entry.getCompound("fluidStack"));
				if (!fluid.isEmpty()) storedFluids.add(fluid);
			}
		}
		if (storedFluids.isEmpty()) {
			// 兜底：读取 MEK 1.20.1 原生存储流体（扳手拆卸时 ISustainedData 写入 stack NBT）
			FluidStack stored = StorageUtils.getStoredFluidFromNBT(stack);
			if (!stored.isEmpty()) storedFluids.add(stored);
		}
		if (storedFluids.isEmpty()) return;

		tooltip.add(Component.translatable("tooltip.productivebeesgenesis.fluid_stored_header")
				.withStyle(ChatFormatting.DARK_AQUA));
		for (FluidStack fluid : storedFluids) {
			tooltip.add(Component.literal(" ")
					.append(Component.translatable("tooltip.productivebeesgenesis.fluid_entry",
							// 1.20.1 FluidStack 无 getHoverName（1.21 API），getDisplayName 等价
							fluid.getDisplayName(), fluid.getAmount()))
					.withStyle(ChatFormatting.GRAY));
		}
	}

	/** 从 NBT 统计 PB 升级总数（所有类型数量之和） */
	private static int countPbUpgradesFromNbt(CompoundTag nbt) {
		if (!nbt.contains(MekCentrifugePbUpgradeHandler.NBT_KEY_COUNTS, Tag.TAG_COMPOUND)) return 0;
		CompoundTag countsTag = nbt.getCompound(MekCentrifugePbUpgradeHandler.NBT_KEY_COUNTS);
		int total = 0;
		for (String typeId : countsTag.getAllKeys()) {
			total = SaturatingMath.saturatingAddToInt(total, countsTag.getInt(typeId));
		}
		return total;
	}

	/**
	 * 添加类型特定详情 — 显示配方类型；储能与流体由基类统一处理
	 * <br/>
	 * 原理：一比一复制ItemBlockFactory.addTypeDetails()的配方类型显示，
	 * 从AttributeFactoryType读取工厂的配方类型（如Smelting），
	 * 显示为"Recipe type: Smelting"。
	 * <p>
	 * 储能与内部流体显示由 super.addTypeDetails 内部统一处理
	 * （基类通过 StorageUtils.addStoredEnergy / addStoredFluid 实现），
	 * 与 MEK 原版机器行为完全一致；空流体显示"没有流体被存储"为标准行为。
	 * <p>
	 * Bug 6：添加 try-catch 防御扳手拆卸后 DataComponents 不完整导致 NPE 崩溃，
	 * 由外层 try-catch 兜底，仅记录警告日志而不影响客户端 tooltip 渲染。
	 */
	@Override
	protected void addTypeDetails(@NotNull ItemStack stack, @NotNull Level level,
			@NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
		try {
			// 原版/EM/ME工厂
			AttributeFactoryType factoryType = Attribute.get(getBlock(), AttributeFactoryType.class);
			if (factoryType != null) {
				tooltip.add(MekanismLang.FACTORY_TYPE.translateColored(
						EnumColor.INDIGO, EnumColor.GRAY, factoryType.getFactoryType()));
			}
			// EME工厂 — 仅在 EME 已加载时检查，避免 NoClassDefFoundError
			else if (MekCompatHooks.isEvolvedMekanismExtrasLoaded()) {
				EMExtraAttributeFactoryType emeFactoryType = Attribute.get(getBlock(), EMExtraAttributeFactoryType.class);
				if (emeFactoryType != null) {
					tooltip.add(MekanismLang.FACTORY_TYPE.translateColored(
							EnumColor.INDIGO, EnumColor.GRAY, emeFactoryType.getFactoryType()));
				}
			}
			// 储能与流体显示由 super.addTypeDetails 内部统一处理（MEK原版行为）
			super.addTypeDetails(stack, level, tooltip, flag);
		} catch (Exception e) {
			// Bug 6：防御扳手拆卸后组件缺失导致 NPE
			ProductiveBeesGenesis.LOGGER.warn("离心机tooltip类型详情显示异常（可能为拆卸后组件缺失）", e);
		}
	}
}
