package com.ayoshiko.productivebeesgenesis.mek.ae2;

import com.ayoshiko.productivebeesgenesis.util.tagfilter.TagCandidate;
import com.ayoshiko.productivebeesgenesis.util.tagfilter.TagPattern;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * AE2 物品标签视图 — 将 Minecraft 物品标签转换为 {@link TagCandidate}。
 * <br/>
 * 桥接 Minecraft 标签系统和 PBG 标签过滤器，用于 AE2 输入过滤。
 */
public final class Ae2ItemTagView {
	private Ae2ItemTagView() {}

	public static TagCandidate candidateOf(Item item) {
		List<String> tagIds = new ArrayList<>();
		collectTagIds(item, tagIds);
		String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
		return TagCandidate.of(itemId, tagIds);
	}

	public static void collectTagIds(Item item, Collection<String> tagIds) {
		BuiltInRegistries.ITEM.getResourceKey(item).ifPresent(resourceKey ->
			BuiltInRegistries.ITEM.getHolder(resourceKey).ifPresent(holder ->
				collectTagIds(holder, tagIds)));
	}

	private static <T> void collectTagIds(Holder<T> holder, Collection<String> tagIds) {
		holder.tags().forEach(tag -> tagIds.add(tag.location().toString()));
	}

	private static <T> boolean matchesAnyTag(Holder<T> holder, TagPattern pattern) {
		return holder.tags().anyMatch(tag -> pattern.matches(tag.location().toString()));
	}
}
