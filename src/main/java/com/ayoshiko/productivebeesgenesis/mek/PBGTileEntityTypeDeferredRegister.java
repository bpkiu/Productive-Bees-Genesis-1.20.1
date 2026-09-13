package com.ayoshiko.productivebeesgenesis.mek;

import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/**
 * TileEntityTypeDeferredRegister 兼容包装
 * <br/>
 * Mekanism 1.21 (NeoForge) 的 {@code TileEntityTypeDeferredRegister.mekBuilder(DeferredHolder<Block, ?>, ...)}
 * 链式 API（serverTicker/clientTicker/withSimple/build）在 Mekanism 1.20.1 (Forge 10.4.x) 中不存在，
 * 1.20.1 对应方法为 {@code builder(BlockRegistryObject<?, ?>, BlockEntitySupplier)}，
 * 且 builder 无 withSimple（1.20.1 的 CONFIG_CARD capability 由 TileEntityMekanism 自行处理）。
 * <p>
 * 本类提供与 1.21 相同签名的 {@link #mekBuilder} + {@link PBGBlockEntityTypeBuilder} 包装，
 * 使 ModBlockEntities 及 ME/EME 隔离注册类的调用链无需修改即可在 1.20.1 编译。
 * <p>
 * 注意：包装逻辑刻意内联在此类中而不调用 {@code MekCentrifugeBlockType.wrapAsBlockRegistryObject}，
 * 避免在 ModBlockEntities 静态初始化期间提前触发 MekCentrifugeBlockType 的重型静态初始化（BlockType 构建）。
 */
public final class PBGTileEntityTypeDeferredRegister extends TileEntityTypeDeferredRegister {

	public PBGTileEntityTypeDeferredRegister(String modId) {
		super(modId);
	}

	/**
	 * 1.21 mekBuilder 兼容入口
	 * <br/>
	 * 接受方块 Supplier（实际均为 RegistryObject），包装为 1.20.1 builder() 所需的
	 * BlockRegistryObject 后委托给父类。item 部分通过方块的注册名延迟创建
	 * （RegistryObject.create 懒解析，与 1.21 DeferredHolder.create 语义一致）。
	 */
	public <BE extends BlockEntity> PBGBlockEntityTypeBuilder<BE> mekBuilder(
			Supplier<? extends Block> blockSupplier, BlockEntityType.BlockEntitySupplier<? extends BE> supplier) {
		return new PBGBlockEntityTypeBuilder<>(builder(wrapBlock(blockSupplier), supplier));
	}

	/**
	 * 将方块 RegistryObject 包装为 Mekanism BlockRegistryObject（block + item 两引用）
	 */
	@SuppressWarnings("unchecked")
	private static BlockRegistryObject<?, ?> wrapBlock(Supplier<? extends Block> blockSupplier) {
		if (!(blockSupplier instanceof RegistryObject<?> blockRO)) {
			throw new IllegalArgumentException(
					"PBGTileEntityTypeDeferredRegister.mekBuilder 仅支持 RegistryObject 方块引用，收到: "
							+ blockSupplier.getClass().getName());
		}
		RegistryObject<Block> typedBlock = (RegistryObject<Block>) blockRO;
		RegistryObject<net.minecraft.world.item.Item> itemRO =
				RegistryObject.create(blockRO.getId(), ForgeRegistries.ITEMS);
		return new BlockRegistryObject<>(typedBlock, itemRO);
	}

	/**
	 * 1.21 BlockEntityTypeBuilder 兼容包装
	 * <br/>
	 * serverTicker/clientTicker 直接透传；withSimple 在 1.20.1 为无操作
	 * （CONFIG_CARD capability 已由 TileEntityMekanism 1.20.1 的 capability 管线提供）。
	 */
	public static final class PBGBlockEntityTypeBuilder<BE extends BlockEntity> {

		private final TileEntityTypeDeferredRegister.BlockEntityTypeBuilder<BE> delegate;

		private PBGBlockEntityTypeBuilder(TileEntityTypeDeferredRegister.BlockEntityTypeBuilder<BE> delegate) {
			this.delegate = delegate;
		}

		public PBGBlockEntityTypeBuilder<BE> serverTicker(BlockEntityTicker<BE> ticker) {
			// 1.20.1 Mekanism builder 接收原版 BlockEntityTicker，本类的同名别名接口经方法引用适配
			delegate.serverTicker(ticker::tick);
			return this;
		}

		public PBGBlockEntityTypeBuilder<BE> clientTicker(BlockEntityTicker<BE> ticker) {
			delegate.clientTicker(ticker::tick);
			return this;
		}

		public PBGBlockEntityTypeBuilder<BE> withSimple(Object capability) {
			// 1.20.1 无需额外注册简单 capability：TileEntityMekanism 构造期已挂载 CONFIG_CARD 等
			return this;
		}

		public TileEntityTypeRegistryObject<BE> build() {
			return delegate.build();
		}
	}

	/** ticker 函数式接口别名，保持调用处 lambda 形态与 1.21 一致 */
	@FunctionalInterface
	public interface BlockEntityTicker<BE extends BlockEntity> {
		void tick(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
				net.minecraft.world.level.block.state.BlockState state, BE blockEntity);
	}
}
