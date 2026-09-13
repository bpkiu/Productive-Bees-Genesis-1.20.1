package com.ayoshiko.productivebeesgenesis.mek.ae2;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.ArrayList;
import java.util.List;

/**
 * AE2 输出台账 — 跟踪输出槽中物品的预留/提交/确认/取消状态。
 * <br/>
 * 用于确保 AE2 推送操作的事务性：先预留，AE2 接受后提交，最终确认或取消。
 */
public final class Ae2OutputLedger {
	public static final int MAX_ENTRIES = 256;
	private static final String KEY_ENTRIES = "entries";
	private static final String KEY_PROCESS = "process";
	private static final String KEY_SLOT = "slot";
	private static final String KEY_FINGERPRINT = "fingerprint";
	private static final String KEY_ORIGINAL_COUNT = "original_count";
	private static final String KEY_REMAINING = "remaining";

	private final List<Settlement> entries = new ArrayList<>();

	public record Settlement(int process, int slot, String fingerprint, int originalCount, int remaining) {}

	public Ae2OutputLedger() {}

	public boolean reserve(int process, int slot, String fingerprint, int count) {
		if (entries.size() >= MAX_ENTRIES) return false;
		Settlement existing = find(process, slot);
		if (existing != null) return false;
		entries.add(new Settlement(process, slot, fingerprint, count, count));
		return true;
	}

	public void commitAccepted(int process, int slot, int accepted) {
		Settlement s = find(process, slot);
		if (s == null) return;
		entries.set(entries.indexOf(s), new Settlement(s.process(), s.slot(), s.fingerprint(), s.originalCount(), s.remaining() - accepted));
	}

	public void confirm(int process, int slot, int remaining) {
		Settlement s = find(process, slot);
		if (s == null) return;
		entries.set(entries.indexOf(s), new Settlement(s.process(), s.slot(), s.fingerprint(), s.originalCount(), remaining));
	}

	public void cancel(int process, int slot) {
		Settlement s = find(process, slot);
		if (s != null) entries.remove(s);
	}

	public boolean hasSlot(int process, int slot) { return find(process, slot) != null; }
	public List<Settlement> snapshot() { return new ArrayList<>(entries); }
	public int size() { return entries.size(); }

	public void save(CompoundTag nbt) {
		ListTag list = new ListTag();
		for (Settlement s : entries) {
			CompoundTag entry = new CompoundTag();
			entry.putInt(KEY_PROCESS, s.process());
			entry.putInt(KEY_SLOT, s.slot());
			entry.putString(KEY_FINGERPRINT, s.fingerprint());
			entry.putInt(KEY_ORIGINAL_COUNT, s.originalCount());
			entry.putInt(KEY_REMAINING, s.remaining());
			list.add(entry);
		}
		nbt.put(KEY_ENTRIES, list);
	}

	public void load(CompoundTag nbt) {
		entries.clear();
		if (!nbt.contains(KEY_ENTRIES, Tag.TAG_LIST)) return;
		ListTag list = nbt.getList(KEY_ENTRIES, Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompound(i);
			entries.add(new Settlement(
				entry.getInt(KEY_PROCESS),
				entry.getInt(KEY_SLOT),
				entry.getString(KEY_FINGERPRINT),
				entry.getInt(KEY_ORIGINAL_COUNT),
				entry.getInt(KEY_REMAINING)));
		}
	}

	public void clear() { entries.clear(); }

	private Settlement find(int process, int slot) {
		for (Settlement s : entries) {
			if (s.process() == process && s.slot() == slot) return s;
		}
		return null;
	}
}
