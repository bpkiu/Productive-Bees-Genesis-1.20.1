package com.ayoshiko.productivebeesgenesis.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 外部物流互操作配置段（{@code external_logistics.*}）。
 * <p>
 * 主线 1.0.7 中物品/流体弹出由自研高性能通道接管（logistics 包），旧的 Mekanism 弹出器
 * 节流参数全部移除。1.20.1 移植版分批迁移：本批先落地该配置段（注册在机器参数文件末尾），
 * 弹出节流键与旧 Ejector Mixin 在 logistics 批次（批次 4）一并清理。
 * <p>
 * 这里只保留一个会<b>改变产物落点</b>、因此需要玩家自主决定的总开关：
 * {@link #externalDirectContainerOutput}。
 */
public final class ExternalLogisticsConfigSection {

	/** 产物直通相邻容器：配方完成时直接写入目标容器，跳过输出槽缓存 */
	public final ForgeConfigSpec.BooleanValue externalDirectContainerOutput;

	private ExternalLogisticsConfigSection(ForgeConfigSpec.Builder builder) {
		builder.comment("外部物流互操作（离心机与机械蜂箱通用）").push("external_logistics");
		externalDirectContainerOutput = builder
				.comment("产物直通：配方完成时先模拟再直接写入已配置输出面的相邻容器，跳过输出槽中转",
						"减少一次「写入输出槽 → 再被抽走」的往返，产物在输出槽里停留的时间大幅缩短",
						"关闭后回到「先进输出槽，再由弹出器/物流模组取走」的传统流程",
						"目标塞不下的部分始终回落输出槽，不会丢失；机器的自动弹出关闭时本功能同样不生效",
						"本项是总开关：开启后仍可在每台机器的侧面配置界面用「O」按钮单独关闭该机器的直通")
				.translation("productivebeesgenesis.configuration.external_logistics.directContainerOutput")
				.define("directContainerOutput", true);
		builder.pop(); // external_logistics
	}

	/**
	 * 工厂方法：注册全部外部物流配置项并返回实例。
	 *
	 * @param builder 机器参数配置构建器
	 * @return 已注册配置项的实例
	 */
	public static ExternalLogisticsConfigSection create(ForgeConfigSpec.Builder builder) {
		return new ExternalLogisticsConfigSection(builder);
	}
}
