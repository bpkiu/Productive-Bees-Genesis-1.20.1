package com.ayoshiko.productivebeesgenesis.compat.productivelib;
import com.ayoshiko.productivebeesgenesis.compat.productivelib.IUpgradeableBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * UpgradeableBlockEntity 本地 stub（1.20.1 迁移适配）
 * <br/>
 * <b>背景</b>：源码原本针对 NeoForge 1.21.1，PB 1.21.1 通过 ProductiveLib 提供此抽象类。
 * 在 Forge 1.20.1 中，PB 与 ProductiveLib 均不提供此抽象类。
 * <p>
 * <b>适配策略</b>：创建本地 stub 抽象类，实现 {@link IUpgradeableBlockEntity}。
 * 源码中的方块实体类原本继承 ProductiveLib 的 {@code UpgradeableBlockEntity}，
 * 现改为继承本类。子类需要实现 {@link #getUpgradeHandler()} 返回升级处理器。
 * <p>
 * <b>注意</b>：本 stub 不继承 PB 的 {@code AbstractBlockEntity}（包路径不兼容），
 * 子类需要自行继承 PB 1.20.1 的 {@code cy.jdkdigital.productivebees.common.block.entity.AbstractBlockEntity}
 * 或 Minecraft 原版 {@code BlockEntity}。
 *
 * @since 2.0.0
 * @author Ayoshiko
 */
public abstract class UpgradeableBlockEntity implements IUpgradeableBlockEntity {

	/**
	 * 默认构造 — stub 不持有 BlockEntity 引用
	 * <p>
	 * 子类应继承 PB 1.20.1 的 {@code AbstractBlockEntity} 或 Minecraft {@code BlockEntity}，
	 * 并通过 implements {@link IUpgradeableBlockEntity} 暴露升级接口。
	 */
	protected UpgradeableBlockEntity() {}
}
