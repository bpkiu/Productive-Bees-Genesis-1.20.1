package com.ayoshiko.productivebeesgenesis.client.screen;

import java.util.ArrayList;
import java.util.List;

/**
 * 标签光标列表 — 候选标签的循环浏览列表。
 * <br/>
 * 维护一个字符串列表和光标位置，支持滚轮切换（cycle）。
 * 用于标签选择器中上下翻页浏览候选标签。
 */
final class TagCursorList {

	static final int MAX_ENTRIES = 64;

	private List<String> entries = new ArrayList<>();
	private int cursor;

	TagCursorList() {
	}

	List<String> getEntries() {
		return entries;
	}

	int getCursor() {
		return cursor;
	}

	boolean isEmpty() {
		return entries.isEmpty();
	}

	String current() {
		if (entries.isEmpty()) return "";
		return entries.get(cursor);
	}

	void setEntries(List<String> entries) {
		this.entries = entries != null ? new ArrayList<>(entries) : new ArrayList<>();
		this.cursor = 0;
	}

	void clear() {
		entries.clear();
		cursor = 0;
	}

	/**
	 * 滚轮切换光标。
	 *
	 * @param direction 向下滚动为正，向上为负
	 * @return true 如果光标移动成功
	 */
	boolean cycle(double direction) {
		if (entries.isEmpty()) return false;
		int size = entries.size();
		if (size == 1) return false;
		int delta = direction > 0 ? 1 : -1;
		cursor = (cursor + delta + size) % size;
		return true;
	}
}
