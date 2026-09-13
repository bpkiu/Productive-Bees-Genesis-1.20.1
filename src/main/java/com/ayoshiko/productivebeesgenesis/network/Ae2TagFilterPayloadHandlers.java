package com.ayoshiko.productivebeesgenesis.network;

import com.ayoshiko.productivebeesgenesis.mek.ae2.Ae2TagFilter;
import com.ayoshiko.productivebeesgenesis.mek.ae2.IAe2InputHost;
import com.ayoshiko.productivebeesgenesis.util.LogThrottle;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * AE2 输入标签过滤器相关数据包的服务端/客户端处理集合
 * <br/>
 * 与 {@link Ae2FilterPayloadHandlers} 风格一致：每个 handler 均校验玩家身份、
 * 当前打开容器与目标方块一致（防 IDOR）、方块实体类型与 8 格交互距离，
 * 并对触发广播的请求施加频次限制。
 * <p>
 * 服务端处理 {@link SetAeInputTagFilterPayload}：校验通过后调用
 * {@link Ae2TagFilter#apply(String, String)} 应用新表达式，标记 dirty 持久化，
 * 并推送 {@link SyncAeInputTagFilterPayload} 同步到客户端。
 * <p>
 * 客户端处理 {@link SyncAeInputTagFilterPayload}：定位方块实体并调用
 * {@link Ae2TagFilter#apply(String, String)} 同步本地副本。
 */
final class Ae2TagFilterPayloadHandlers {

	private Ae2TagFilterPayloadHandlers() {
	}

	/**
	 * 服务端处理：设置 per-tile AE2 输入标签过滤器（白名单 + 黑名单）
	 * <br/>
	 * 处理流程：
	 * <ol>
	 *   <li>校验玩家身份与服务端 level 是否就绪</li>
	 *   <li>强制容器一致性校验 — 防止 IDOR 攻击（玩家打开自己方块后远程操作 8 格内他人方块）</li>
	 *   <li>频次限制 — 防止恶意客户端高频触发同步广播（流量放大攻击）</li>
	 *   <li>获取方块实体，校验 8 格交互距离</li>
	 *   <li> instanceof 检查 {@link IAe2InputHost} 接口</li>
	 *   <li>通过 {@code productivebeesgenesis$getAe2TagFilter()} 获取标签过滤器</li>
	 *   <li>调用 {@link Ae2TagFilter#apply(String, String)} 应用新表达式</li>
	 *   <li>markForSave 持久化，推送 SyncAeInputTagFilterPayload 同步到客户端</li>
	 * </ol>
	 */
	static void handleSetAeInputTagFilter(SetAeInputTagFilterPayload payload, Supplier<NetworkEvent.Context> ctx) {
		ServerPlayer serverPlayer = ctx.get().getSender();
		if (serverPlayer == null) {
			return;
		}
		if (serverPlayer.level() == null) {
			return;
		}
		// 强制容器一致性校验：防止 IDOR 攻击
		if (!Ae2PayloadHandlers.validateContainerMatch(serverPlayer, payload.pos(),
				"ae2_input_tag_filter_pos_mismatch")) {
			return;
		}
		// 频次限制：防止恶意客户端高频触发 syncTagFilterToClient 广播（流量放大攻击）
		if (!PayloadRateLimiter.tryAccept(serverPlayer, "ae_input_tag_filter_set",
				NetworkSecurityConstants.PAYLOAD_RATE_LIMIT_INTERVAL_MS)) {
			return;
		}
		BlockEntity be = serverPlayer.level().getBlockEntity(payload.pos());
		if (be == null) {
			return;
		}
		double distance = serverPlayer.distanceToSqr(payload.pos().getCenter());
		if (distance > NetworkSecurityConstants.GUI_INTERACTION_DISTANCE_SQ) {
			LogThrottle.warn("ae2_input_tag_filter_distance",
					"玩家 {} 尝试远距离修改 AE2 输入标签过滤器：距离 {} 格",
					serverPlayer.getName().getString(), Math.sqrt(distance));
			return;
		}
		if (!(be instanceof IAe2InputHost host)) {
			return;
		}
		Ae2TagFilter tagFilter = host.productivebeesgenesis$getAe2TagFilter();
		if (tagFilter == null) {
			return;
		}
		// apply 内部会做幂等性比较，无变化时返回 false，避免不必要的同步
		if (tagFilter.apply(payload.whitelist(), payload.blacklist())
				&& be instanceof TileEntityMekanism mek) {
			mek.markForSave();
		}
		syncTagFilterToClient(be, serverPlayer);
	}

	/**
	 * 客户端处理：接收服务端推送的 AE2 输入标签过滤器
	 * <br/>
	 * 客户端定位 {@link BlockPos} 对应的方块实体，若其实现 {@link IAe2InputHost}，
	 * 则通过 {@code productivebeesgenesis$getAe2TagFilter()} 获取标签过滤器，
	 * 调用 {@link Ae2TagFilter#apply(String, String)} 同步本地副本。
	 */
	static void handleSyncAeInputTagFilter(SyncAeInputTagFilterPayload payload, Level level) {
		BlockEntity be = level == null ? null : level.getBlockEntity(payload.pos());
		if (!(be instanceof IAe2InputHost host)) {
			return;
		}
		Ae2TagFilter tagFilter = host.productivebeesgenesis$getAe2TagFilter();
		if (tagFilter == null) {
			return;
		}
		tagFilter.apply(payload.whitelist(), payload.blacklist());
	}

	/**
	 * 推送 AE2 输入标签过滤器到指定玩家
	 * <br/>
	 * 服务端在玩家打开 GUI 或标签过滤器变更后调用此方法，
	 * 将当前白名单/黑名单表达式同步到客户端。
	 *
	 * @param be 目标方块实体，必须实现 {@link IAe2InputHost}
	 */
	static void syncTagFilterToClient(BlockEntity be, ServerPlayer player) {
		if (be == null || !(be instanceof IAe2InputHost host)) {
			return;
		}
		Ae2TagFilter tagFilter = host.productivebeesgenesis$getAe2TagFilter();
		if (tagFilter == null) {
			return;
		}
		SyncAeInputTagFilterPayload payload = new SyncAeInputTagFilterPayload(
				be.getBlockPos(),
				tagFilter.getWhitelistSource(),
				tagFilter.getBlacklistSource());
		ModPayloads.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
	}
}
