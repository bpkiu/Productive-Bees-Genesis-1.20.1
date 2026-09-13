package com.ayoshiko.productivebeesgenesis.client.screen;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.jei.interfaces.IJEIGhostTarget;
import mekanism.client.jei.interfaces.IJEIGhostTarget.IGhostIngredientConsumer;
import mekanism.client.jei.interfaces.IJEIGhostTarget.IGhostItemConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * 样本物品槽组件 — 显示一个物品样本，供标签选择器收集标签。
 * <br/>
 * 继承 MEK {@link GuiElement}，实现 {@link IJEIGhostTarget}（1.20.1 等价于
 * 原 1.21 的 {@code IRecipeViewerGhostTarget}），支持 JEI 拖拽物品。
 * <p>
 * 交互方式：
 * <ul>
 *   <li>左键点击 — 拾取玩家主手物品作为样本（不消耗原物品）</li>
 *   <li>右键点击 — 同上</li>
 *   <li>JEI 拖拽 — 接受任意非空物品堆叠作为样本</li>
 *   <li>右键空主手 + 已有样本 — 清空样本</li>
 * </ul>
 * 样本变化时通过 {@code onChanged} 回调通知上层（如 {@link TagPickerWidget}）刷新候选标签。
 */
public class TagSampleSlotWidget extends GuiElement implements IJEIGhostTarget {

	/** 槽位尺寸（宽=高，与 SlotType.NORMAL 一致） */
	public static final int SIZE = 18;

	private final Consumer<ItemStack> onChanged;
	private ItemStack sample = ItemStack.EMPTY;

	/**
	 * 构造样本物品槽组件。
	 *
	 * @param gui      所属 GUI 包装器
	 * @param x        相对 GUI 左上角 X 坐标
	 * @param y        相对 GUI 左上角 Y 坐标
	 * @param onChanged 样本变化回调（参数为新样本堆叠，空堆叠表示清空）
	 */
	public TagSampleSlotWidget(IGuiWrapper gui, int x, int y, Consumer<ItemStack> onChanged) {
		super(gui, x, y, SIZE, SIZE);
		this.onChanged = onChanged;
	}

	/** 是否为空样本 */
	public boolean isEmpty() {
		return sample.isEmpty();
	}

	/** 获取当前样本 */
	public ItemStack getSample() {
		return sample;
	}

	/**
	 * 设置样本物品并触发回调。
	 *
	 * @param stack 新样本（null 或空堆叠视为清空）
	 */
	public void setSample(ItemStack stack) {
		ItemStack newSample = stack == null ? ItemStack.EMPTY : stack.copyWithCount(1);
		if (ItemStack.isSameItemSameTags(newSample, sample)) {
			return;
		}
		sample = newSample;
		if (onChanged != null) {
			onChanged.accept(sample);
		}
	}

	/**
	 * JEI ghost ingredient 目标处理器 — 返回接受任意非空物品的消费者。
	 */
	@Override
	public IGhostIngredientConsumer getGhostHandler() {
		return new SampleGhostConsumer();
	}

	/** 命中检测 — 供外部 JEI 拖拽路由使用 */
	public boolean contains(double mouseX, double mouseY) {
		return isMouseOver(mouseX, mouseY);
	}

	/**
	 * 接受物品作为样本。
	 *
	 * @param stack 物品堆叠
	 */
	public void accept(ItemStack stack) {
		setSample(stack);
	}

	/**
	 * slot 背景在 renderWidget 阶段渲染，对齐 MEK GuiSlot 默认模式。
	 */
	@Override
	public void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		guiGraphics.blit(SlotType.NORMAL.getTexture(), relativeX, relativeY, 0, 0, SIZE, SIZE, SIZE, SIZE);
	}

	/**
	 * 物品在 drawBackground 阶段渲染，对齐 MEK GuiSequencedSlotDisplay。
	 */
	@Override
	public void drawBackground(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		super.drawBackground(guiGraphics, mouseX, mouseY, partialTicks);
		if (!sample.isEmpty()) {
			guiGraphics.renderFakeItem(sample, relativeX + 1, relativeY + 1);
		}
	}

	/**
	 * 鼠标点击处理 — 拾取玩家主手物品作为样本。
	 * <br/>
	 * 左键或右键均可拾取主手物品；空主手 + 已有样本时右键清空。
	 */
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!isMouseOver(mouseX, mouseY)) {
			return false;
		}
		if (button == 0 || button == 1) {
			var player = Minecraft.getInstance().player;
			if (player == null) {
				return false;
			}
			ItemStack mainHand = player.getMainHandItem();
			if (!mainHand.isEmpty()) {
				setSample(mainHand);
				return true;
			}
			// 空主手 + 已有样本：清空
			if (!isEmpty() && button == 1) {
				setSample(ItemStack.EMPTY);
				return true;
			}
		}
		return false;
	}

	/**
	 * 重写 renderForeground — 不绘制默认按钮文字。
	 * <br/>
	 * GuiElement 默认在 renderForeground 中渲染 getMessage() 文字，
	 * 对样本槽来说会遮挡图标。故重写为空实现。
	 */
	@Override
	public void renderForeground(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
		// 不调用 super.renderForeground 以避免绘制文字
	}

	@Override
	@NotNull
	public Component getMessage() {
		return sample.isEmpty() ? Component.empty() : Component.literal(sample.getHoverName().getString());
	}

	// TODO 1.20.1: 1.20.1 的 AbstractWidget#updateWidgetNarration 为抽象方法且 MEK GuiElement 实现对 javac 不可见
	@Override
	public void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
	}

	/** JEI 拖拽消费者 — 接受任意非空 ItemStack 作为样本 */
	private final class SampleGhostConsumer implements IGhostItemConsumer {

		@Override
		public boolean supportsIngredient(Object ingredient) {
			return ingredient instanceof ItemStack stack && !stack.isEmpty();
		}

		@Override
		public void accept(Object ingredient) {
			if (ingredient instanceof ItemStack stack) {
				TagSampleSlotWidget.this.accept(stack);
			}
		}
	}
}
