package com.ayoshiko.productivebeesgenesis.apiary.client;

import com.ayoshiko.productivebeesgenesis.apiary.BeeNbtHelper;
import com.ayoshiko.productivebeesgenesis.apiary.BeeSlot;
import com.ayoshiko.productivebeesgenesis.apiary.BeeState;
import com.ayoshiko.productivebeesgenesis.util.BeeInfoHelper.FlowerPreference;
import com.ayoshiko.productivebeesgenesis.util.BeeInfoHelper;
import com.ayoshiko.productivebeesgenesis.util.DevLog;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 蜜蜂 Tooltip 渲染器
 * <br/>
 * 鼠标悬停在蜜蜂槽上时显示详细信息 tooltip。
 * <p>
 * 未按 Shift：显示蜜蜂名称、停工原因（如有）和"按住 Shift 查看更多信息"提示。
 * 按住 Shift：显示完整属性（年龄/血量/生产力/耐力/脾气/行为/天气耐受性）
 * 及生产进度、当前状态、花蜜信息。
 * <p>
 * PB 1.20.1 兼容版：属性直接存为顶层 NBT 整数键
 * （{@code bee_productivity}=0-3, {@code bee_endurance}=0-3 等），
 * 无需 NeoForge 1.21+ 的 DataComponent 嵌套路径。
 * <p>
 * 设计原则：单一职责，仅负责 tooltip 渲染与 NBT 读取展示。
 * <br/>
 * 线程安全：仅从客户端渲染线程调用，无需同步。
 */
public class BeeTooltipRenderer {

	/** ARGB 不透明前缀 */
	private static final int ALPHA_OPAQUE = 0xFF000000;

	/** 灰色（用于无花蜜行） */
	private static final int COLOR_GRAY = 0xFFAAAAAA;

	/** 白色（用于标签文本） */
	private static final int COLOR_WHITE = 0xFFFFFFFF;

	/** 花蜜-有 的颜色（黄色） */
	private static final int COLOR_NECTAR_YES = 0xFFFFFF66;

	/** PB 1.20.1 顶层 NBT 键（直接整数值） */
	private static final String KEY_BEE_PRODUCTIVITY = "bee_productivity";
	private static final String KEY_BEE_ENDURANCE = "bee_endurance";
	private static final String KEY_BEE_TEMPER = "bee_temper";
	private static final String KEY_BEE_BEHAVIOR = "bee_behavior";
	private static final String KEY_BEE_WEATHER_TOLERANCE = "bee_weather_tolerance";

	/** 翻译键前缀 */
	private static final String TOOLTIP_PREFIX = "gui.productivebeesgenesis.bee_tooltip.";
	private static final String KEY_HOLD_SHIFT = TOOLTIP_PREFIX + "hold_shift";
	private static final String KEY_AGE_ADULT = TOOLTIP_PREFIX + "age.adult";
	private static final String KEY_AGE_CHILD = TOOLTIP_PREFIX + "age.child";
	private static final String KEY_HEALTH = TOOLTIP_PREFIX + "health";
	private static final String KEY_PRODUCTIVITY = TOOLTIP_PREFIX + "productivity";
	private static final String KEY_ENDURANCE = TOOLTIP_PREFIX + "endurance";
	private static final String KEY_TEMPER = TOOLTIP_PREFIX + "temper";
	private static final String KEY_BEHAVIOR = TOOLTIP_PREFIX + "behavior";
	private static final String KEY_WEATHER_TOLERANCE = TOOLTIP_PREFIX + "weather_tolerance";
	private static final String KEY_PROGRESS = TOOLTIP_PREFIX + "progress";
	private static final String KEY_STATE = TOOLTIP_PREFIX + "state";
	private static final String KEY_NECTAR_YES = TOOLTIP_PREFIX + "nectar.yes";
	private static final String KEY_NECTAR_NO = TOOLTIP_PREFIX + "nectar.no";
	private static final String KEY_FLOWER = TOOLTIP_PREFIX + "flower";
	private static final String KEY_FLOWER_ANY = TOOLTIP_PREFIX + "flower.any";

	/**
	 * 渲染蜜蜂 tooltip
	 */
	public void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, BeeSlot beeSlot, int slotX, int slotY) {
		if (beeSlot.isEmpty()) return;

		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;

		ResourceLocation beeType = resolveBeeType(beeSlot);
		if (beeType == null) return;

		List<FormattedCharSequence> lines = new ArrayList<>(13);

		// 1. 蜜蜂名称（带状态颜色）
		Component name = BeeInfoHelper.getBeeDisplayName(beeType);
		int nameColor = ALPHA_OPAQUE | beeSlot.getState().getColor();
		lines.add(name.copy().withStyle(s -> s.withColor(nameColor)).getVisualOrderText());

		CompoundTag beeData = beeSlot.getBeeData();
		if (Screen.hasShiftDown()) {
			addDetailLines(lines, beeSlot, beeData);
		} else {
			if (beeSlot.getState() != BeeState.WORKING) {
				addStateLine(lines, beeSlot);
			}
			lines.add(Component.translatable(KEY_HOLD_SHIFT)
					.withStyle(ChatFormatting.WHITE).getVisualOrderText());
		}

		guiGraphics.renderTooltip(font, lines, mouseX, mouseY);
	}

	/**
	 * 添加 Shift 展开时的详细属性行
	 */
	private void addDetailLines(List<FormattedCharSequence> lines, BeeSlot beeSlot, @Nullable CompoundTag beeData) {
		if (beeData != null) {
			addAgeLine(lines, beeData);
			addHealthLine(lines, beeData);
		}

		// PB 1.20.1 属性（直接从顶层 NBT 读取整数值）
		if (beeData != null) {
			addIntAttributeLine(lines, beeData, KEY_BEE_PRODUCTIVITY, KEY_PRODUCTIVITY,
					TOOLTIP_PREFIX + "productivity.", BeeTooltipRenderer::getProductivityColor,
					BeeTooltipRenderer::productivityLevelName);
			addIntAttributeLine(lines, beeData, KEY_BEE_ENDURANCE, KEY_ENDURANCE,
					TOOLTIP_PREFIX + "endurance.", BeeTooltipRenderer::getEnduranceColor,
					BeeTooltipRenderer::enduranceLevelName);
			addIntAttributeLine(lines, beeData, KEY_BEE_TEMPER, KEY_TEMPER,
					TOOLTIP_PREFIX + "temper.", BeeTooltipRenderer::getTemperColor,
					BeeTooltipRenderer::temperLevelName);
			addIntAttributeLine(lines, beeData, KEY_BEE_BEHAVIOR, KEY_BEHAVIOR,
					TOOLTIP_PREFIX + "behavior.", BeeTooltipRenderer::getBehaviorColor,
					BeeTooltipRenderer::behaviorLevelName);
			addIntAttributeLine(lines, beeData, KEY_BEE_WEATHER_TOLERANCE, KEY_WEATHER_TOLERANCE,
					TOOLTIP_PREFIX + "weather_tolerance.", BeeTooltipRenderer::getWeatherToleranceColor,
					BeeTooltipRenderer::weatherToleranceLevelName);
		}

		addProgressLine(lines, beeSlot);
		addStateLine(lines, beeSlot);
		addNectarLine(lines, beeSlot);
		addFlowerLine(lines, beeSlot);
	}

	/**
	 * 添加整数属性行（PB 1.20.1 兼容版）
	 * <br/>
	 * 从顶层 NBT 读取整数值，通过 levelNameResolver 转换为等级名，
	 * 构建 "标签: 值" 格式行。属性缺失时跳过。
	 */
	private void addIntAttributeLine(List<FormattedCharSequence> lines, CompoundTag beeData,
			String nbtKey, String labelKey, String valueKeyPrefix,
			Function<String, ChatFormatting> colorResolver,
			Function<Integer, String> levelNameResolver) {
		if (!beeData.contains(nbtKey)) return;
		int level = beeData.getInt(nbtKey);
		String levelName = levelNameResolver.apply(level);
		Component valueComponent = Component.translatable(valueKeyPrefix + levelName)
				.withStyle(colorResolver.apply(levelName));
		lines.add(Component.translatable(labelKey, valueComponent)
				.withStyle(ChatFormatting.DARK_GRAY)
				.getVisualOrderText());
	}

	private void addAgeLine(List<FormattedCharSequence> lines, CompoundTag beeData) {
		if (!beeData.contains("Age")) return;
		int age = beeData.getInt("Age");
		String ageKey = age < 0 ? KEY_AGE_CHILD : KEY_AGE_ADULT;
		lines.add(Component.translatable(ageKey)
				.withStyle(ChatFormatting.AQUA)
				.withStyle(ChatFormatting.ITALIC)
				.getVisualOrderText());
	}

	private void addHealthLine(List<FormattedCharSequence> lines, CompoundTag beeData) {
		if (!beeData.contains("Health")) return;
		float current = beeData.getFloat("Health");
		float max = beeData.contains("MaxHealth") ? beeData.getFloat("MaxHealth") : 10.0f;
		lines.add(Component.translatable(KEY_HEALTH, formatHealth(current), formatHealth(max))
				.withStyle(ChatFormatting.DARK_GRAY)
				.getVisualOrderText());
	}

	private void addProgressLine(List<FormattedCharSequence> lines, BeeSlot beeSlot) {
		int ticksInHive = beeSlot.getTicksInHive();
		int minTicks = beeSlot.getMinOccupationTicks();
		int progressPercent = minTicks > 0 ? Math.min(100, (int) (beeSlot.getProgress() * 100)) : 0;
		lines.add(Component.translatable(KEY_PROGRESS, ticksInHive, minTicks, progressPercent)
				.withStyle(s -> s.withColor(COLOR_WHITE))
				.getVisualOrderText());
	}

	private void addStateLine(List<FormattedCharSequence> lines, BeeSlot beeSlot) {
		Component stateComponent = getStateComponent(beeSlot.getState());
		lines.add(Component.translatable(KEY_STATE, stateComponent)
				.withStyle(s -> s.withColor(COLOR_WHITE))
				.getVisualOrderText());
	}

	private void addNectarLine(List<FormattedCharSequence> lines, BeeSlot beeSlot) {
		boolean hasNectar = beeSlot.hasNectar();
		String nectarKey = hasNectar ? KEY_NECTAR_YES : KEY_NECTAR_NO;
		int nectarColor = hasNectar ? COLOR_NECTAR_YES : COLOR_GRAY;
		lines.add(Component.translatable(nectarKey)
				.withStyle(s -> s.withColor(nectarColor))
				.getVisualOrderText());
	}

	private void addFlowerLine(List<FormattedCharSequence> lines, BeeSlot beeSlot) {
		ResourceLocation beeType = resolveBeeType(beeSlot);
		if (beeType == null) return;

		FlowerPreference pref = BeeInfoHelper.getFlowerPreference(beeType);
		Component flowerComponent = resolveFlowerComponent(pref);
		lines.add(Component.translatable(KEY_FLOWER, flowerComponent)
				.withStyle(ChatFormatting.LIGHT_PURPLE)
				.getVisualOrderText());
	}

	private Component resolveFlowerComponent(FlowerPreference pref) {
		if (pref == null || !pref.hasFlowerDefinition()) {
			return Component.translatable(KEY_FLOWER_ANY);
		}
		if (!pref.flowerTag().isEmpty()) {
			return Component.literal("#" + pref.flowerTag())
					.withStyle(ChatFormatting.GRAY);
		}
		if (!pref.flowerItem().isEmpty()) {
			try {
				ResourceLocation itemId = ResourceLocation.parse(pref.flowerItem());
				Item item = BuiltInRegistries.ITEM.get(itemId);
				if (item != Items.AIR) {
					return Component.translatable(item.getDescriptionId())
							.withStyle(ChatFormatting.YELLOW);
				}
			} catch (RuntimeException e) {
				DevLog.warn("bee_tooltip", "花物品 ID 解析失败, 回退原始 ID: {}", pref.flowerItem());
			}
			return Component.literal(pref.flowerItem())
					.withStyle(ChatFormatting.GRAY);
		}
		if (!pref.flowerBlock().isEmpty()) {
			try {
				ResourceLocation blockId = ResourceLocation.parse(pref.flowerBlock());
				return Component.translatable("block." + blockId.getNamespace() + "." + blockId.getPath())
						.withStyle(ChatFormatting.YELLOW);
			} catch (RuntimeException e) {
				DevLog.warn("bee_tooltip", "花方块 ID 解析失败, 回退原始 ID: {}", pref.flowerBlock());
				return Component.literal(pref.flowerBlock())
						.withStyle(ChatFormatting.GRAY);
			}
		}
		if (!pref.flowerFluid().isEmpty()) {
			try {
				String fluidId = pref.flowerFluid().startsWith("#")
						? pref.flowerFluid().substring(1)
						: pref.flowerFluid();
				ResourceLocation rl = ResourceLocation.parse(fluidId);
				return Component.literal(fluidId)
						.withStyle(ChatFormatting.AQUA);
			} catch (RuntimeException e) {
				DevLog.warn("bee_tooltip", "花流体 ID 解析失败, 回退原始 ID: {}", pref.flowerFluid());
			}
			return Component.literal(pref.flowerFluid())
					.withStyle(ChatFormatting.GRAY);
		}
		return Component.translatable(KEY_FLOWER_ANY);
	}

	// ===== 辅助方法 =====

	@Nullable
	private ResourceLocation resolveBeeType(BeeSlot beeSlot) {
		CompoundTag beeData = beeSlot.getBeeData();
		if (beeData == null) return null;
		try {
			return BeeNbtHelper.resolveBeeTypeKey(beeData);
		} catch (RuntimeException e) {
			DevLog.warn("bee_tooltip", "解析蜜蜂类型键失败, 返回 null: {}", e.toString());
			return null;
		}
	}

	/**
	 * 根据整数等级返回等级名字符串（PB 1.20.1 兼容）
	 */
	private static String productivityLevelName(int v) {
		return switch (v) { case 1 -> "medium"; case 2 -> "high"; case 3 -> "very_high"; default -> "normal"; };
	}
	private static String enduranceLevelName(int v) {
		return switch (v) { case 1 -> "normal"; case 2 -> "medium"; case 3 -> "strong"; default -> "weak"; };
	}
	private static String temperLevelName(int v) {
		return switch (v) { case 1 -> "normal"; case 2 -> "aggressive"; case 3 -> "hostile"; default -> "passive"; };
	}
	private static String behaviorLevelName(int v) {
		return switch (v) { case 1 -> "nocturnal"; case 2 -> "metaturnal"; default -> "diurnal"; };
	}
	private static String weatherToleranceLevelName(int v) {
		return switch (v) { case 1 -> "rain"; case 2 -> "any"; default -> "none"; };
	}

	private static String formatHealth(float value) {
		if (value == Math.floor(value) && !Float.isInfinite(value)) {
			return String.valueOf((int) value);
		}
		return String.format("%.1f", value);
	}

	private Component getStateComponent(BeeState state) {
		String key = switch (state) {
			case IDLE -> "gui.productivebeesgenesis.bee_state.idle";
			case WORKING -> "gui.productivebeesgenesis.bee_state.working";
			case WAITING_FLOWER -> "gui.productivebeesgenesis.bee_state.waiting_flower";
			case WAITING_ENERGY -> "gui.productivebeesgenesis.bee_state.waiting_energy";
			case WAITING_OUTPUT -> "gui.productivebeesgenesis.bee_state.waiting_output";
			case WAITING_DAY_CYCLE -> "gui.productivebeesgenesis.bee_state.waiting_day_cycle";
			case WAITING_RAIN -> "gui.productivebeesgenesis.bee_state.waiting_rain";
			case WAITING_THUNDER -> "gui.productivebeesgenesis.bee_state.waiting_thunder";
		};
		int color = ALPHA_OPAQUE | state.getColor();
		return Component.translatable(key).withStyle(s -> s.withColor(color));
	}

	// ===== 等级颜色解析 =====

	private static ChatFormatting getProductivityColor(String level) {
		return switch (level) {
			case "normal" -> ChatFormatting.GREEN;
			case "medium" -> ChatFormatting.BLUE;
			case "high" -> ChatFormatting.LIGHT_PURPLE;
			case "very_high" -> ChatFormatting.RED;
			default -> ChatFormatting.WHITE;
		};
	}
	private static ChatFormatting getEnduranceColor(String level) {
		return switch (level) {
			case "weak" -> ChatFormatting.GREEN;
			case "normal" -> ChatFormatting.BLUE;
			case "medium" -> ChatFormatting.LIGHT_PURPLE;
			case "strong" -> ChatFormatting.RED;
			default -> ChatFormatting.WHITE;
		};
	}
	private static ChatFormatting getTemperColor(String level) {
		return switch (level) {
			case "passive" -> ChatFormatting.GREEN;
			case "normal" -> ChatFormatting.BLUE;
			case "aggressive" -> ChatFormatting.LIGHT_PURPLE;
			case "hostile" -> ChatFormatting.RED;
			default -> ChatFormatting.WHITE;
		};
	}
	private static ChatFormatting getBehaviorColor(String level) {
		return switch (level) {
			case "diurnal" -> ChatFormatting.GREEN;
			case "nocturnal" -> ChatFormatting.LIGHT_PURPLE;
			case "metaturnal" -> ChatFormatting.RED;
			default -> ChatFormatting.WHITE;
		};
	}
	private static ChatFormatting getWeatherToleranceColor(String level) {
		return switch (level) {
			case "none" -> ChatFormatting.GREEN;
			case "rain" -> ChatFormatting.LIGHT_PURPLE;
			case "any" -> ChatFormatting.RED;
			default -> ChatFormatting.WHITE;
		};
	}
}
