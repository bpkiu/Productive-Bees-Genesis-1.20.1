package com.ayoshiko.productivebeesgenesis.mek.ae2;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.ArrayList;
import java.util.List;

/**
 * AE2 待处理物品缓冲区 — 存储推送失败的物品，延迟重试。
 * <br/>
 * 当 AE2 网络暂时不可用或已满时，物品暂存在此缓冲区，定期重试推送。
 */
public final class Ae2PendingItemBuffer {
	public static final int MAX_ENTRIES = 64;
	private static final int MAX_RETRY_COUNT = 5;
	private static final long MAX_RETRY_DELAY_TICKS = 200L;
	private static final String KEY_ENTRIES = "pending_entries";
	private static final String KEY_FINGERPRINT = "fingerprint";
	private static final String KEY_AMOUNT = "amount";
	private static final String KEY_RETRIES = "retries";
	private static final String KEY_NEXT_ATTEMPT = "next_attempt";

	private final List<PendingItem> entries = new ArrayList<>();
	private long totalAmount = 0;

	public static class PendingItem {
		final String fingerprint;
		long amount;
		int retries;
		long nextAttemptTick;

		PendingItem(String fingerprint, long amount, long currentTick) {
			this.fingerprint = fingerprint;
			this.amount = amount;
			this.retries = 0;
			this.nextAttemptTick = currentTick + 1;
		}

		PendingItem(String fingerprint, long amount, int retries, long nextAttemptTick) {
			this.fingerprint = fingerprint;
			this.amount = amount;
			this.retries = retries;
			this.nextAttemptTick = nextAttemptTick;
		}
	}

	public Ae2PendingItemBuffer() {}

	public long enqueue(String fingerprint, long amount, long currentTick) {
		if (entries.size() >= MAX_ENTRIES && find(fingerprint) == null) return 0;
		PendingItem item = find(fingerprint);
		if (item != null) {
			item.amount += amount;
			totalAmount += amount;
			return amount;
		}
		entries.add(new PendingItem(fingerprint, amount, currentTick));
		totalAmount += amount;
		return amount;
	}

	public boolean canRegister(String fingerprint) {
		if (find(fingerprint) != null) return true;
		return entries.size() < MAX_ENTRIES;
	}

	public List<PendingItem> snapshot(long currentTick) {
		List<PendingItem> result = new ArrayList<>();
		for (PendingItem item : entries) {
			if (item.nextAttemptTick <= currentTick) result.add(item);
		}
		return result;
	}

	public void consume(String fingerprint, long amount, long currentTick) {
		PendingItem item = find(fingerprint);
		if (item == null) return;
		item.amount -= amount;
		totalAmount -= amount;
		if (item.amount <= 0) {
			entries.remove(item);
		}
	}

	public void recordFailure(String fingerprint, long currentTick) {
		PendingItem item = find(fingerprint);
		if (item == null) return;
		item.retries++;
		if (item.retries >= MAX_RETRY_COUNT) {
			entries.remove(item);
		} else {
			item.nextAttemptTick = currentTick + Math.min(MAX_RETRY_DELAY_TICKS, (1L << item.retries));
		}
	}

	public void save(CompoundTag nbt) {
		ListTag list = new ListTag();
		for (PendingItem item : entries) {
			CompoundTag entry = new CompoundTag();
			entry.putString(KEY_FINGERPRINT, item.fingerprint);
			entry.putLong(KEY_AMOUNT, item.amount);
			entry.putInt(KEY_RETRIES, item.retries);
			entry.putLong(KEY_NEXT_ATTEMPT, item.nextAttemptTick);
			list.add(entry);
		}
		nbt.put(KEY_ENTRIES, list);
	}

	public void load(CompoundTag nbt) {
		entries.clear();
		totalAmount = 0;
		if (!nbt.contains(KEY_ENTRIES, Tag.TAG_LIST)) return;
		ListTag list = nbt.getList(KEY_ENTRIES, Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompound(i);
			PendingItem item = new PendingItem(
				entry.getString(KEY_FINGERPRINT),
				entry.getLong(KEY_AMOUNT),
				entry.getInt(KEY_RETRIES),
				entry.getLong(KEY_NEXT_ATTEMPT));
			entries.add(item);
			totalAmount += item.amount;
		}
	}

	public void clear() { entries.clear(); totalAmount = 0; }
	public int size() { return entries.size(); }
	public long getTotalAmount() { return totalAmount; }

	private PendingItem find(String fingerprint) {
		for (PendingItem item : entries) {
			if (item.fingerprint.equals(fingerprint)) return item;
		}
		return null;
	}
}
