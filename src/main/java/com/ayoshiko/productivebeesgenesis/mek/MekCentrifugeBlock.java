package com.ayoshiko.productivebeesgenesis.mek;

import com.ayoshiko.productivebeesgenesis.util.WrenchCapabilityHelper;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.block.attribute.AttributeState;
import mekanism.common.block.attribute.AttributeStateFacing;
import mekanism.common.block.attribute.Attributes;
import mekanism.common.block.interfaces.IHasDescription;
import mekanism.common.block.interfaces.IHasTileEntity;
import mekanism.common.block.interfaces.ITypeBlock;
import mekanism.common.block.states.BlockStateHelper;
import mekanism.common.content.blocktype.BlockType;
import mekanism.common.content.blocktype.BlockTypeTile;
import mekanism.common.lib.security.ISecurityTile;
import mekanism.common.network.to_client.PacketSecurityUpdate;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.base.TileEntityUpdateable;
import mekanism.common.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import mekanism.common.tile.base.WrenchResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
	 * MEK离心机方块
	 * <br/>
	 * 参考Mek-Energistics的MeMekanismMachineBlock，支持Mekanism的Attribute系统。
	 * 实现IHasTileEntity/ITypeBlock/IHasDescription接口，与Mekanism的GUI/侧面配置/升级体系兼容。
	 * <p>
	 * 支持基础机器和工厂版，通过泛型TYPE参数区分BlockType类型。
	 */
public class MekCentrifugeBlock<TILE extends TileEntityMekanism, TYPE extends BlockTypeTile<TILE>>
		extends Block implements IHasDescription, ITypeBlock, IHasTileEntity<TILE> {

	/** 方块状态属性：朝向 + 活跃状态 */
	private static final List<Attribute> STATE_ATTRIBUTES = List.of(
			new AttributeStateFacing(), Attributes.ACTIVE_LIGHT);

	private final TYPE blockType;

	public MekCentrifugeBlock(TYPE blockType) {
		super(properties(blockType));
		this.blockType = blockType;
		// 使用所有AttributeState设置默认状态（facing + active）
		BlockState defaultState = this.stateDefinition.any();
		for (Attribute attr : STATE_ATTRIBUTES) {
			if (attr instanceof AttributeState atr) {
				defaultState = atr.getDefaultState(defaultState);
			}
		}
		this.registerDefaultState(defaultState);
	}

	/** 根据BlockType属性调整方块属性 */
	private static BlockBehaviour.Properties properties(BlockTypeTile<?> blockType) {
		BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
				.strength(3.5F, 16.0F)
				.requiresCorrectToolForDrops();
		for (Attribute attribute : blockType.getAll()) {
			attribute.adjustProperties(props);
		}
		return props;
	}

	@Override
	public BlockType getType() {
		return blockType;
	}

	@Override
	public mekanism.api.text.ILangEntry getDescription() {
		return blockType.getDescription();
	}

	@Override
	public MutableComponent getName() {
		return super.getName();
	}

	@Override
	public TileEntityTypeRegistryObject<TILE> getTileType() {
		return blockType.getTileType();
	}

	/**
	 * EntityBlock 实现 — 创建方块实体
	 * <br/>
	 * 1.20.1 的 Mekanism IHasTileEntity 默认实现在发布 jar 中为 SRG 名（m_142194_），
	 * 编译期不参与实现解析，因此必须显式覆写。
	 */
	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return getTileType().get().create(pos, state);
	}

	/** 使用Mekanism Attribute系统填充BlockState定义 */
	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		List<Property<?>> properties = new ArrayList<>();
		for (Attribute attr : STATE_ATTRIBUTES) {
			if (attr instanceof AttributeState atr) {
				atr.fillBlockStateContainer(this, properties);
			}
		}
		if (!properties.isEmpty()) {
			builder.add(properties.toArray(new Property[0]));
		}
	}

	/** 放置时根据玩家朝向设置facing方向 — 参考Mekanism BlockMekanism */
	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return BlockStateHelper.getStateForPlacement(this, super.getStateForPlacement(context), context);
	}

	/** 邻居方块变化时通知TileEntity更新红石状态 — 参考Mekanism BlockTile */
	@Override
	public void neighborChanged(BlockState state, Level world, BlockPos pos, Block neighborBlock,
								BlockPos neighborPos, boolean isMoving) {
		if (!world.isClientSide) {
			TileEntityMekanism tile = WorldUtils.getTileEntity(TileEntityMekanism.class, world, pos);
			if (tile != null) {
				tile.onNeighborChange(neighborBlock, neighborPos);
			}
		}
	}

	/**
	 * 右键交互 — 1.20.1 合并了 1.21 的 useWithoutItem/useItemOn 钩子，只保留 use()
	 * <br/>
	 * 模块 1 修复（v2.4 最终版）：扳手（{@link WrenchCapabilityHelper} 判定）服务端委托
	 * {@code tile.tryWrench} 处理（与 MEK 原版 {@code BlockTile} 一致），客户端返回 SUCCESS
	 * 阻止 GUI 打开与 omnitools 的 Item.useOn 拦截。tryWrench 通过
	 * {@link mekanism.common.tags.MekanismTags.Items#CONFIGURATORS} 标签判定扳手，
	 * 该标签引用 {@code c:tools/wrench}，AE2/OmniTools 扳手均在此标签中。
	 * <ul>
	 *   <li>shift+右键扳手：dismantleBlock → 拆卸</li>
	 *   <li>右键扳手（非shift）：tryWrench → tryWrenchRotate → 旋转方向</li>
	 *   <li>非扳手：打开 GUI</li>
	 * </ul>
	 * 详见 {@link com.ayoshiko.productivebeesgenesis.apiary.MekApiaryBlock#use} 的完整说明。
	 */
	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos,
									Player player, InteractionHand hand, BlockHitResult hitResult) {
		TileEntityMekanism tile = WorldUtils.getTileEntity(TileEntityMekanism.class, level, pos);
		if (tile == null) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		boolean isWrench = !stack.isEmpty() && WrenchCapabilityHelper.canUseAsWrench(stack);
		if (isWrench) {
			// 客户端：扳手返回 SUCCESS 阻止 GUI 打开与物品默认交互
			if (level.isClientSide) {
				return InteractionResult.SUCCESS;
			}
			// 服务端：shift+扳手优先拆卸，直接返回 SUCCESS 阻止 omnitools 的 Item.useOn 拦截
			if (player.isShiftKeyDown()) {
				if (tile.getRadiationScale() <= 0) {
					WorldUtils.dismantleBlock(state, level, pos, tile);
					return InteractionResult.SUCCESS;
				}
				return InteractionResult.FAIL;
			}
			// 非 shift 场景委托 tryWrench 处理旋转（1.20.1 无 ItemStack 参数，内部取主手物品）
			WrenchResult wrenchResult = tile.tryWrench(state, player, hand, hitResult);
			if (wrenchResult == WrenchResult.PASS) {
				return InteractionResult.PASS;
			}
			if (wrenchResult == WrenchResult.NO_SECURITY) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		// 非扳手：打开 GUI（客户端 SUCCESS 预期，服务端实际打开）
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		return tile.openGui(player);
	}

	/**
	 * 掉落物 — 使用 vanilla 标准 {@code saveToItem} 路径保留完整方块实体数据
	 * <br/>
	 * {@code BlockEntity.saveToItem} 内部执行：
	 * <ol>
	 *   <li>{@code saveCustomOnly} → {@code saveAdditional}：保存 PB进度/PB升级/流体罐到 NBT</li>
	 *   <li>{@code removeComponentsFromTag}：移除已被 collectImplicitComponents 处理的 MEK 标准字段</li>
	 *   <li>{@code BlockItem.setBlockEntityData}：设置 BLOCK_ENTITY_DATA 组件（含 id）</li>
	 *   <li>{@code collectComponents} → {@code collectImplicitComponents}：收集 ATTACHED_ITEMS/UPGRADES 等组件</li>
	 *   <li>{@code stack.applyComponents}：设置组件到 ItemStack</li>
	 * </ol>
	 * 放置时 NeoForge 双路径恢复：
	 * <ol>
	 *   <li>{@code loadCustomOnly} → {@code loadAdditional}：从 BLOCK_ENTITY_DATA 恢复 PB进度/PB升级</li>
	 *   <li>{@code applyImplicitComponents}：从 ATTACHED_ITEMS/UPGRADES 恢复 MEK 标准槽位（输出槽/输入槽/能量槽）</li>
	 * </ol>
	 * PB进度/PB升级不在 ContainerType 中，不会被 applyImplicitComponents 覆盖。
	 * <p>
	 * 此路径统一了镐子挖掘和扳手拆卸的数据保存方式，确保两者 NBT 结构一致、物品栏可合并。
	 */
	@Override
	public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		List<ItemStack> drops = super.getDrops(state, params);
		if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof TileEntityUpdateable updateable) {
			// getDrops 幂等防护：二次调用时数据已清空，跳过重复序列化（防异常场景读到空数据）
			boolean alreadySerialized = updateable instanceof TileEntityMekCentrifuge centrifuge0
					&& centrifuge0.isDropsSerialized();
			if (!alreadySerialized) {
				for (ItemStack drop : drops) {
					if (drop.is(this.asItem())) {
						// 1.20.1 路径：saveWithFullMetadata + BlockItem.setBlockEntityData 写入 BlockEntityTag
						// （1.20.5 的 BlockEntity.saveToItem 在 1.20.1 不存在）
						BlockItem.setBlockEntityData(drop, updateable.getType(), updateable.saveWithFullMetadata());
						if (updateable instanceof TileEntityMekanism mekanismTile && mekanismTile.getCustomName() != null) {
						drop.setHoverName(mekanismTile.getCustomName());
					}
					}
				}
				// 保存数据后清空所有槽位，防止 setRemoved 触发 Ejector 重复 popResource
				if (updateable instanceof TileEntityMekCentrifuge centrifugeTile) {
					centrifugeTile.markDropsSerialized();
					centrifugeTile.saveAllItemsForDrop();
				}
			}
		}
		return drops;
	}

	/** 放置时初始化方块实体并设置安全拥有者 */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state,
			@Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.getBlockEntity(pos) instanceof TileEntityMekanism updateable) {
			updateable.onAdded();
			// 设置安全系统拥有者（参考BlockMekanism.setPlacedBy）
			// TileEntityMekanism 自身实现 ISecurityTile，Java 17 禁止对子类型表达式使用冗余 instanceof 模式，直接赋值
			ISecurityTile securityTile = updateable;
			if (securityTile.getOwnerUUID() == null && placer != null) {
				securityTile.setOwnerUUID(placer.getUUID());
				if (!level.isClientSide && placer instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
					// Task 6：定向发送给放置者，避免全服广播（安全拥有者仅需放置者客户端同步）
					mekanism.common.Mekanism.packetHandler().sendTo(
							new PacketSecurityUpdate(placer.getUUID()), serverPlayer);
				}
			}
		}
	}

	/**
	 * 方块被移除时清理 — 对齐 MEK 原版 {@code BlockMekanism.onRemove}
	 * <br/>
	 * NeoForge 1.21.1 方块破坏顺序：{@code onRemove} 在 {@code getDrops} 之前调用，
	 * 因此<strong>不能在此清空槽位</strong>，否则 {@code getDrops} 读到空数据。
	 * 槽位清空由 {@code getDrops} 中的 {@code saveAllItemsForDrop} 在 {@code saveToItem} 后执行。
	 */
	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (state.hasBlockEntity() && !state.is(newState.getBlock())) {
			TileEntityUpdateable tile = WorldUtils.getTileEntity(TileEntityUpdateable.class, level, pos);
			if (tile != null) {
				tile.blockRemoved();
			}
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}
}
