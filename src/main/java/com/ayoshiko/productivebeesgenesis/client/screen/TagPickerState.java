package com.ayoshiko.productivebeesgenesis.client.screen;

import com.ayoshiko.productivebeesgenesis.util.tagfilter.TagExpressionText;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 标签选择器状态 — 管理标签选择器的物品样本和候选标签列表。
 * <br/>
 * 持有当前样本物品、添加候选标签列表和移除候选标签列表。
 * 当样本物品变化时，自动收集物品的所有标签。
 */
final class TagPickerState {

	/** 候选标签数量上限 */
	static final int MAX_CANDIDATES = 32;

	private final TagCursorList addCandidates = new TagCursorList();
	private final TagCursorList removeCandidates = new TagCursorList();
	private ItemStack stack = ItemStack.EMPTY;
	private List<String> sampleTags = new ArrayList<>();

	TagPickerState() {
	}

	/** 获取当前样本物品 */
	ItemStack getStack() {
		return stack;
	}

	/** 获取添加候选标签列表（未出现在当前表达式中的标签） */
	TagCursorList addList() {
		return addCandidates;
	}

	/** 获取移除候选标签列表（已出现在当前表达式中的标签） */
	TagCursorList removeList() {
		return removeCandidates;
	}

	/**
	 * 设置样本物品并刷新候选列表。
	 *
	 * @param stack 新的样本物品（null 视为空）
	 */
	void setStack(ItemStack stack) {
		this.stack = stack != null ? stack : ItemStack.EMPTY;
		refresh("");
	}

	/**
	 * 刷新候选标签列表。
	 * <br/>
	 * 根据当前表达式，将样本标签拆分为：
	 * <ul>
	 *   <li>添加候选 — 未出现在表达式中的标签</li>
	 *   <li>移除候选 — 已出现在表达式中的标签</li>
	 * </ul>
	 *
	 * @param currentExpression 当前标签表达式
	 */
	void refresh(String currentExpression) {
		sampleTags = collect(stack);
		List<String> addList = new ArrayList<>();
		List<String> removeList = new ArrayList<>();
		List<String> literals = TagExpressionText.listLiterals(currentExpression);
		for (String tag : sampleTags) {
			if (literals.contains(tag)) {
				removeList.add(tag);
			} else {
				addList.add(tag);
			}
		}
		addCandidates.setEntries(addList);
		removeCandidates.setEntries(removeList);
	}

	/**
	 * 收集物品的所有标签（注册表 ID + 物品标签）。
	 * <br/>
	 * 通过 {@link BuiltInRegistries#ITEM} 获取物品的注册表键，
	 * 再通过 {@link net.minecraft.core.Holder#tags()} 收集所有标签位置。
	 *
	 * @param stack 物品堆叠（空堆叠返回空列表）
	 * @return 标签字符串列表（格式为 {@code namespace:path}）
	 */
	private static List<String> collect(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return new ArrayList<>();
		}
		List<String> tags = new ArrayList<>();
		var item = stack.getItem();
		var rl = BuiltInRegistries.ITEM.getKey(item);
		if (rl != null) {
			tags.add(rl.toString());
		}
		// 通过 Holder 收集物品绑定的所有标签
		BuiltInRegistries.ITEM.getResourceKey(item).ifPresent(key ->
				BuiltInRegistries.ITEM.getHolder(key).ifPresent(holder ->
						holder.tags().forEach(tag -> tags.add(tag.location().toString()))));
		return tags;
	}
}
