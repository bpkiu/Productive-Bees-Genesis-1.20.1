package com.ayoshiko.productivebeesgenesis.mek.ae2;

import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

/**
	 * AE2 capability 注册器（Forge 1.20.1 适配）
	 * <br/>
	 * <b>Forge 1.20.1 与 NeoForge 1.21.1 差异</b>：
	 * <ul>
	 *   <li>NeoForge 1.21.1 使用新 capability 系统（{@code BlockCapability}），可在
	 *       {@link RegisterCapabilitiesEvent} 中通过 {@code registerBlockEntity(capability, type, provider)}
	 *       按 BlockEntityType 挂载 provider；</li>
	 *   <li>Forge 1.20.1 的 {@link RegisterCapabilitiesEvent} 仅支持 {@code register(Class)}
	 *       声明式注册，<b>不存在</b>按对象类型挂载 provider 的 API。</li>
	 * </ul>
	 * <p>
	 * <b>当前实现策略</b>：AE2 15.x（Forge 版）的 {@code GridHelper.getNodeHost(Level, BlockPos)}
	 * 首先执行 {@code level.getBlockEntity(pos) instanceof IInWorldGridNodeHost} 判断，
	 * 仅在类型判断失败时才回退查询 {@code Capabilities.IN_WORLD_GRID_NODE_HOST} capability。
	 * 本项目通过 {@code mixin/ae2} 包下的 Mixin（{@code Ae2ApiaryMixin}/{@code Ae2CentrifugeMixin}/
	 * {@code Ae2CentrifugeFactoryMixin} 等）将 {@link com.ayoshiko.productivebeesgenesis.mek.ae2.IAe2OutputHost}
	 * （继承 {@code IInWorldGridNodeHost}）注入到蜂箱与离心机方块实体，
	 * 因此 AE2 线缆通过类型判断路径即可发现并连接这些方块，无需 capability 挂载。
	 * <p>
	 * <b>覆盖范围（由 Mixin 实现，本类无需遍历注册）</b>：
	 * <ul>
	 *   <li>离心机 18 个 BlockEntityType（基础+原版4+EM5+ME4+EME4）</li>
	 *   <li>通用机械蜂箱 18 个 BlockEntityType（基础1+原版工厂4+EM5+ME4+EME4）</li>
	 * </ul>
	 * <p>
	 * <b>待办</b>：离心机对外暴露的 ME 存储（{@link CentrifugeExternalAeStorage}，原注册于
	 * {@code Capabilities.ME_STORAGE}）需要改为在各离心机 TileEntity 的
	 * {@code getCapability(Capability, Direction)} 重写中返回，待 capability 系统整体迁移时恢复。
	 *
	 * @since 1.5.3
	 * @author Ayoshiko
	 */
public final class Ae2CapabilityRegistrar {

	private Ae2CapabilityRegistrar() {}

	/**
	 * Forge 1.20.1 下为空实现。
	 * <br/>
	 * {@link RegisterCapabilitiesEvent} 在 Forge 1.20.1 仅用于声明自定义 capability 类型，
	 * 无法为 BlockEntityType 挂载 provider。AE2 发现逻辑已由 Mixin 注入接口 +
	 * {@code GridHelper.getNodeHost} 的 instanceof 路径覆盖，保留方法签名以兼容调用点，
	 * 待 capability 系统整体迁移时在此处补充 Forge 式注册逻辑。
	 *
	 * @param event Forge capability 注册事件
	 */
	public static void register(RegisterCapabilitiesEvent event) {
		// Forge 1.20.1 无按类型的 capability 挂载 API — 见类文档说明
	}
}
