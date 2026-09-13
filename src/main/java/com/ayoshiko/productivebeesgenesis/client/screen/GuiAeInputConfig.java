package com.ayoshiko.productivebeesgenesis.client.screen;

import appeng.api.stacks.AEItemKey;
import com.ayoshiko.productivebeesgenesis.mek.ae2.Ae2InputFilter.EntryInfo;
import com.ayoshiko.productivebeesgenesis.mek.ae2.Ae2InputFilter;
import com.ayoshiko.productivebeesgenesis.mek.ae2.Ae2ItemFingerprint;
import com.ayoshiko.productivebeesgenesis.mek.ae2.Ae2OutputStateHolder;
import com.ayoshiko.productivebeesgenesis.mek.ae2.CombFuzzyMatcher;
import com.ayoshiko.productivebeesgenesis.mek.ae2.IAe2InputHost;
import com.ayoshiko.productivebeesgenesis.network.CycleAeInputFilterModePayload;
import com.ayoshiko.productivebeesgenesis.network.OpenAeInputConfigPayload;
import com.ayoshiko.productivebeesgenesis.network.SetAeInputFilterEntryPayload.OperationType;
import com.ayoshiko.productivebeesgenesis.network.SetAeInputFilterEntryPayload;
import com.ayoshiko.productivebeesgenesis.network.ToggleAllAeInputFilterUnlimitedPayload;
import com.ayoshiko.productivebeesgenesis.network.ToggleAllAeInputFilterNetworkStockPayload;
import com.ayoshiko.productivebeesgenesis.network.ToggleAeInputNbtIgnorePayload;
import com.ayoshiko.productivebeesgenesis.network.ToggleAeInputPayload;
import com.ayoshiko.productivebeesgenesis.network.ToggleAeInputPreciseModePayload;
import com.ayoshiko.productivebeesgenesis.util.BeeInfoHelper;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElementHolder;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.common.inventory.container.SelectedWindowData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.ayoshiko.productivebeesgenesis.network.ModPayloads;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
	 * AE2 input pull configuration window.
	 * <p>
	 * Layout mirrors the AE2LT overloaded ME interface (OverloadedInterfaceScreen):
	 * a 9 x 2 grid where each cell stacks the amount/unlimited gear button on top,
	 * the marker (ghost) slot 18px below and a network output slot 18px further
	 * down. The output slot shows the pullable network stock and lets the player
	 * take items from / put items back into the ME network directly, matching the
	 * ProxiedStorageInv behaviour of AE2LT (gear on top, marker row, output row).
	 */
public final class GuiAeInputConfig extends GuiWindow {

	private final IAe2InputHost host;
	private final BlockPos pos;
	private final GhostItemWidget[] ghostSlots;
	private final StockGearButton[] stockButtons;
	private final OutputSlotWidget[] outputSlots;
	private final MekanismButton toggleBtn;
	private final MekanismButton nbtBtn;
	private final MekanismButton filterModeBtn;
	private final MekanismButton preciseBtn;
	private final ReserveModeButton reserveBtn;
	private final GlobalGearButton globalGearBtn;
	private final MekanismButton prevPageBtn;
	private final MekanismButton nextPageBtn;
	private final MekanismButton clearBtn;
	private final GuiInnerScreen infoScreen;
	private int currentPage;
	private int stockSyncTicks;
	/** Minimum page count (from config), guarantees spare pages in fixed-position mode. */
	private int minPages = 2;

	public GuiAeInputConfig(IGuiWrapper gui, int x, int y, IAe2InputHost host, SelectedWindowData windowData) {
		super(gui, x, y, AeInputConfigLayout.WINDOW_WIDTH, AeInputConfigLayout.WINDOW_HEIGHT,
				windowData == null ? AeInputWindowData.INSTANCE : windowData);
		this.host = host;
		this.pos = host.productivebeesgenesis$getAe2BlockPos();
		this.currentPage = 0;
		this.stockSyncTicks = 0;
		this.interactionStrategy = InteractionStrategy.ALL;

		// TODO 1.20.1: Mekanism 10.4.16 无 GuiPinButton（固定按钮），直接移除该元素；
		// 窗口关闭/拖动逻辑不受影响（固定窗口能力放弃，如需可改用 MekanismImageButton 实现）。
		addChild(new GuiElementHolder(gui(), relativeX + AeInputConfigLayout.GRID_X,
				relativeY + AeInputConfigLayout.GRID_Y, AeInputConfigLayout.GRID_WIDTH,
				AeInputConfigLayout.GRID_HEIGHT));
		infoScreen = addChild(new GuiInnerScreen(gui(), relativeX + AeInputConfigLayout.INFO_X,
				relativeY + AeInputConfigLayout.GRID_Y, AeInputConfigLayout.INFO_WIDTH,
				AeInputConfigLayout.INFO_HEIGHT));

		// Control buttons (I/N/F/P)
		int btnX = 8;
		toggleBtn = addChild(new CtrlButton(gui(), relativeX + btnX,
				AeInputConfigLayout.controlY(relativeY), AeInputConfigLayout.TOGGLE_BTN_WIDTH, AeInputConfigLayout.CTRL_BTN_HEIGHT,
				"I", () -> ModPayloads.CHANNEL.sendToServer(new ToggleAeInputPayload(pos))));
		toggleBtn.setTooltip(Tooltip.create(
				Component.translatable("productivebeesgenesis.gui.ae_input_config.toggle.tooltip")));
		btnX += AeInputConfigLayout.TOGGLE_BTN_WIDTH + 2;
		nbtBtn = addChild(new CtrlButton(gui(), relativeX + btnX,
				AeInputConfigLayout.controlY(relativeY),
				AeInputConfigLayout.TOGGLE_BTN_WIDTH, AeInputConfigLayout.CTRL_BTN_HEIGHT, "N",
				() -> ModPayloads.CHANNEL.sendToServer(new ToggleAeInputNbtIgnorePayload(pos))));
		nbtBtn.setTooltip(Tooltip.create(
				Component.translatable("productivebeesgenesis.gui.ae_input_config.nbt_ignore.tooltip")));
		btnX += AeInputConfigLayout.TOGGLE_BTN_WIDTH + 2;
		filterModeBtn = addChild(new CtrlButton(gui(), relativeX + btnX,
				AeInputConfigLayout.controlY(relativeY),
				AeInputConfigLayout.TOGGLE_BTN_WIDTH, AeInputConfigLayout.CTRL_BTN_HEIGHT, "F", () -> ModPayloads.CHANNEL.sendToServer(new CycleAeInputFilterModePayload(pos))));
		filterModeBtn.setTooltip(Tooltip.create(
				Component.translatable("productivebeesgenesis.gui.ae_input_config.filter_mode.tooltip")));
		btnX += AeInputConfigLayout.TOGGLE_BTN_WIDTH + 2;
		preciseBtn = addChild(new CtrlButton(gui(), relativeX + btnX,
				AeInputConfigLayout.controlY(relativeY),
				AeInputConfigLayout.TOGGLE_BTN_WIDTH, AeInputConfigLayout.CTRL_BTN_HEIGHT, "P", () -> ModPayloads.CHANNEL.sendToServer(new ToggleAeInputPreciseModePayload(pos))));
		preciseBtn.setTooltip(Tooltip.create(
				Component.translatable("productivebeesgenesis.gui.ae_input_config.precise_mode.tooltip")));
		btnX += AeInputConfigLayout.TOGGLE_BTN_WIDTH + 2;
		// 与下方逐槽齿轮一致：16×16 方形按钮，垂直方向相对 14px 控制行居中
		globalGearBtn = addChild(new GlobalGearButton(gui(), relativeX + btnX, AeInputConfigLayout.controlY(relativeY) - 1,
				this::onOpenGlobalAmount,
				() -> ModPayloads.CHANNEL.sendToServer(new ToggleAllAeInputFilterUnlimitedPayload(pos))));
		globalGearBtn.setTooltip(Tooltip.create(Component.translatable(
				"productivebeesgenesis.gui.ae_input_config.global_gear.tooltip")));
		btnX += AeInputConfigLayout.GEAR_SIZE + 4;
		reserveBtn = addChild(new ReserveModeButton(gui(), relativeX + btnX,
				AeInputConfigLayout.controlY(relativeY),
				() -> onOpenGlobalReserve(),
				() -> ModPayloads.CHANNEL.sendToServer(new ToggleAllAeInputFilterNetworkStockPayload(pos))));
		reserveBtn.setTooltip(Tooltip.create(Component.translatable(
				"productivebeesgenesis.gui.ae_input_config.reserve_button.tooltip")));

		// Page buttons (prev / clear / next)
		prevPageBtn = addChild(new CtrlButton(gui(), relativeX + AeInputConfigLayout.WINDOW_WIDTH
				- 3 * (AeInputConfigLayout.PAGE_BTN_WIDTH + 2) - 8,
				AeInputConfigLayout.controlY(relativeY),
				AeInputConfigLayout.PAGE_BTN_WIDTH, AeInputConfigLayout.CTRL_BTN_HEIGHT,
					"\u25C0", () -> changePage(-1)));
		prevPageBtn.setTooltip(Tooltip.create(
				Component.translatable("productivebeesgenesis.gui.ae_input_config.prev_page.tooltip")));
		clearBtn = addChild(new CtrlButton(gui(), relativeX + AeInputConfigLayout.WINDOW_WIDTH
				- 2 * (AeInputConfigLayout.PAGE_BTN_WIDTH + 2) - 8,
				AeInputConfigLayout.controlY(relativeY),
				AeInputConfigLayout.PAGE_BTN_WIDTH, AeInputConfigLayout.CTRL_BTN_HEIGHT,
					"C", this::sendClear));
		clearBtn.setTooltip(Tooltip.create(
				Component.translatable("productivebeesgenesis.gui.ae_input_config.clear.tooltip")));
		nextPageBtn = addChild(new CtrlButton(gui(), relativeX + AeInputConfigLayout.WINDOW_WIDTH
				- (AeInputConfigLayout.PAGE_BTN_WIDTH + 2) - 8,
				AeInputConfigLayout.controlY(relativeY),
				AeInputConfigLayout.PAGE_BTN_WIDTH, AeInputConfigLayout.CTRL_BTN_HEIGHT,
					"\u25B6", () -> changePage(1)));
		nextPageBtn.setTooltip(Tooltip.create(
				Component.translatable("productivebeesgenesis.gui.ae_input_config.next_page.tooltip")));

		// AE2LT overloaded-interface cells: gear (top) / marker (middle) / output (bottom)
		ghostSlots = new GhostItemWidget[AeInputConfigLayout.SLOTS_PER_PAGE];
		stockButtons = new StockGearButton[AeInputConfigLayout.SLOTS_PER_PAGE];
		outputSlots = new OutputSlotWidget[AeInputConfigLayout.SLOTS_PER_PAGE];
		for (int i = 0; i < AeInputConfigLayout.SLOTS_PER_PAGE; i++) {
			int col = i % AeInputConfigLayout.GRID_COLS;
			int row = i / AeInputConfigLayout.GRID_COLS;
			int cellX = AeInputConfigLayout.GRID_X + col * AeInputConfigLayout.CELL_PITCH_X;
			int cellY = AeInputConfigLayout.GRID_Y + row * AeInputConfigLayout.CELL_PITCH_Y;
			ghostSlots[i] = addChild(new GhostItemWidget(gui(), relativeX + cellX,
					relativeY + cellY + 18, i,
					null, false, this::onSlotPlaced, this::onSlotRemoved));
			stockButtons[i] = addChild(new StockGearButton(gui(), relativeX + cellX,
					relativeY + cellY, AeInputConfigLayout.GEAR_SIZE, i,
					this::onSlotConfigureAmount, this::onSlotToggleUnlimited,
					this::onSlotToggleNetworkStock, this::onSlotConfigureReserve));
			outputSlots[i] = addChild(new OutputSlotWidget(gui(), relativeX + cellX,
					relativeY + cellY + 36, pos, i));
		}
	}

	/**
	 * 标题左侧内边距 — 为关闭按钮留出空间
	 * <br/>
	 * TODO 1.20.1: 原 {@code 14 + GuiPinButton.WIDTH}（WIDTH=8），Mekanism 10.4.16 无 GuiPinButton，
	 * 按钮已移除但保留原缩进值以维持标题排版不变。
	 */
	@Override
	protected int getTitlePadStart() {
		return 14 + 8;
	}

	@Override
	public void renderForeground(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
		super.renderForeground(guiGraphics, mouseX, mouseY);
		drawTitleText(guiGraphics, Component.translatable("productivebeesgenesis.gui.ae_input_config.title"), 5);

		// Read-only rendering: state updates (clampCurrentPage/refreshGhostSlots/
		// updateButtonStates) happen in tick() to avoid recursive rendering.
		Ae2InputFilter filter = getFilter();
		int entryCount = filter == null ? 0 : filter.getNonEmptyEntries().size();
		renderInfoPanel(guiGraphics, filter, entryCount);
	}

	/**
	 * State refresh moved to tick(): avoids recursive rendering and keeps the
	 * ghost/output slots and buttons in sync with the server filter snapshot.
	 */
	@Override
	public void tick() {
		super.tick();
		Ae2InputFilter filter = getFilter();
		if (filter != null && filter.hasDirectEntries()) {
			if (++stockSyncTicks >= 20) {
				stockSyncTicks = 0;
				ModPayloads.CHANNEL.sendToServer(new OpenAeInputConfigPayload(pos));
			}
		} else {
			stockSyncTicks = 0;
		}
		int slotCount = filter == null ? 0 : filter.getCapacity();
		clampCurrentPage(slotCount);
		refreshGhostSlots(filter);
		updateButtonStates(filter);
	}

	private void renderInfoPanel(GuiGraphics guiGraphics, Ae2InputFilter filter, int entryCount) {
		int startX = AeInputConfigLayout.INFO_X + 4;
		int startY = AeInputConfigLayout.GRID_Y + 4;

		// Right info panel mirrors the two-row grid height (AeInputConfigLayout.INFO_HEIGHT =
				// AeInputConfigLayout.GRID_HEIGHT).
		Component modeText = filter == null
				? Component.translatable("productivebeesgenesis.gui.ae_input_config.mode")
						.copy().append(" --")
				: switch (filter.getFilterMode()) {
					case DISABLED -> Component.translatable("productivebeesgenesis.gui.ae_input_config.mode")
							.copy().append(" ").append(Component.translatable(
									"productivebeesgenesis.gui.ae_input_config.filter_mode.disabled"));
					case WHITELIST -> Component.translatable("productivebeesgenesis.gui.ae_input_config.mode")
							.copy().append(" ").append(Component.translatable(
									"productivebeesgenesis.gui.ae_input_config.filter_mode.whitelist"));
					case BLACKLIST -> Component.translatable("productivebeesgenesis.gui.ae_input_config.mode")
							.copy().append(" ").append(Component.translatable(
									"productivebeesgenesis.gui.ae_input_config.filter_mode.blacklist"));
				};
		drawScaledInfoText(guiGraphics, modeText, startX, startY, screenTextColor());

		boolean inputEnabled = host.productivebeesgenesis$isAeItemInputEnabled();
		Component inputText = Component.translatable(inputEnabled
				? "productivebeesgenesis.gui.ae_input_config.info.input_on"
				: "productivebeesgenesis.gui.ae_input_config.info.input_off");
		drawScaledInfoText(guiGraphics, inputText, startX, startY + 9, screenTextColor());

		boolean nbtIgnore = host.productivebeesgenesis$isAeInputNbtIgnore();
		Component nbtText = Component.translatable(nbtIgnore
				? "productivebeesgenesis.gui.ae_input_config.info.nbt_ignore"
				: "productivebeesgenesis.gui.ae_input_config.info.nbt_match");
		drawScaledInfoText(guiGraphics, nbtText, startX, startY + 18, screenTextColor());

		boolean precise = filter != null && filter.isPreciseMode();
		Component preciseText = Component.translatable(precise
				? "productivebeesgenesis.gui.ae_input_config.info.precise_on"
				: "productivebeesgenesis.gui.ae_input_config.info.precise_off");
		drawScaledInfoText(guiGraphics, preciseText, startX, startY + 27, screenTextColor());

		int slotCount = filter == null ? 0 : filter.getCapacity();
		int total = AeInputConfigLayout.computeTotalPages(slotCount, minPages);
		Component pageText = Component.translatable("productivebeesgenesis.gui.ae_input_config.page")
				.copy().append(" ").append((currentPage + 1) + "/" + total);
		drawScaledInfoText(guiGraphics, pageText, startX, startY + 36, screenTextColor());

		Component countText = Component.translatable("productivebeesgenesis.gui.ae_input_config.entries")
			.copy().append(" ").append(Integer.toString(entryCount));
		drawScaledInfoText(guiGraphics, countText, startX, startY + 45, screenTextColor());

		Ae2OutputStateHolder holder = host.productivebeesgenesis$getAe2StateHolder();
		if (holder != null) {
			Component rateText = Component.translatable("productivebeesgenesis.gui.ae_input_config.info.rate",
					Integer.toString(holder.getCachedInputRatePerTick()));
			drawScaledInfoText(guiGraphics, rateText, startX, startY + 54, screenTextColor());

			Component intervalText = Component.translatable("productivebeesgenesis.gui.ae_input_config.info.interval",
					Integer.toString(holder.getCachedInputIntervalTicks()));
			drawScaledInfoText(guiGraphics, intervalText, startX, startY + 63, screenTextColor());

			Component cooldownText = Component.translatable(
					"productivebeesgenesis.gui.ae_input_config.info.cooldown",
					Integer.toString(holder.getInputPullCooldownTicks()));
			drawScaledInfoText(guiGraphics, cooldownText, startX, startY + 72, screenTextColor());
		}

		int unlimitedCount = 0;
		if (filter != null) {
			for (Ae2InputFilter.IndexedEntry ie : filter.getNonEmptyEntries()) {
				if (filter.isDirectUnlimitedAt(ie.index())) unlimitedCount++;
			}
		}
		Component unlimitedText = Component.translatable(
				"productivebeesgenesis.gui.ae_input_config.info.unlimited_count",
				Integer.toString(unlimitedCount));
		drawScaledInfoText(guiGraphics, unlimitedText, startX, startY + 81, screenTextColor());

		boolean unlimitedAll = filter != null && filter.isUnlimitedAllFallback();
		Component unlimitedAllText = Component.translatable(
				"productivebeesgenesis.gui.ae_input_config.info.unlimited_all",
				stateLabel(unlimitedAll));
		drawScaledInfoText(guiGraphics, unlimitedAllText, startX, startY + 90,
				unlimitedAll ? 0x55FF55 : screenTextColor());

		boolean globalStock = filter != null && filter.isGlobalNetworkStock();
		Component reserveText = Component.translatable(
				"productivebeesgenesis.gui.ae_input_config.info.reserve_status",
				stateLabel(globalStock),
				filter == null ? "0" : AeInputConfigText.formatCompactAmount(filter.getGlobalReserveAmount()));
		drawScaledInfoText(guiGraphics, reserveText, startX, startY + 99,
				globalStock ? 0x55FF55 : screenTextColor());
	}

	/** 信息面板文本缩放系数（与原 {@code drawScaledScrollingString} 的 0.7F 保持一致） */
	private static final float INFO_TEXT_SCALE = 0.7F;

	/**
	 * TODO 1.20.1: Mekanism 10.4.x 无 {@code drawScaledScrollingString}（1.21 API），
	 * 改为静态左对齐缩放绘制（横向滚动动画省略，超宽部分不做裁剪）。
	 */
	private void drawScaledInfoText(GuiGraphics guiGraphics, Component text, int x, int y, int color) {
		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(x, y, 0);
		guiGraphics.pose().scale(INFO_TEXT_SCALE, INFO_TEXT_SCALE, INFO_TEXT_SCALE);
		drawString(guiGraphics, text, 0, 0, color);
		guiGraphics.pose().popPose();
	}

	@Override
	public void drawBackground(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.drawBackground(guiGraphics, mouseX, mouseY, partialTick);
		guiGraphics.setColor(1, 1, 1, 1);
	}

	private Ae2InputFilter getFilter() {
		return host.productivebeesgenesis$getAeInputFilter();
	}

	private static Component stateLabel(boolean on) {
		return Component.translatable(on
				? "productivebeesgenesis.gui.ae_input_config.state.on"
				: "productivebeesgenesis.gui.ae_input_config.state.off");
	}

	/**
	 * Fills the current page's ghost slots from the fixed-position filter array.
	 * <br/>
	 * Position-fixed mode: entry index = currentPage * AeInputConfigLayout.SLOTS_PER_PAGE + page index.
	 */
	private void refreshGhostSlots(Ae2InputFilter filter) {
		int start = currentPage * AeInputConfigLayout.SLOTS_PER_PAGE;
		for (int i = 0; i < AeInputConfigLayout.SLOTS_PER_PAGE; i++) {
			int globalIdx = start + i;
			outputSlots[i].clear();
			outputSlots[i].setTooltip((Tooltip) null);
			stockButtons[i].visible = false;
			stockButtons[i].setTooltip((Tooltip) null);
			if (filter != null) {
				EntryInfo info = filter.getEntryAt(globalIdx);
				if (info != null && info.directFingerprint != null) {
					AEItemKey key = filter.getResolvedDirectKey(globalIdx);
					if (key == null && Minecraft.getInstance().level != null) {
						key = Ae2ItemFingerprint.decode(info.directFingerprint);
					}
					if (key != null) filter.resolveDirectKey(globalIdx, key);
					long amount = filter.getDirectAmountAt(globalIdx);
					long reserveAmount = filter.getDirectReserveAmountAt(globalIdx);
					long visibleAmount = filter.getDirectVisibleAmountAt(globalIdx);
					boolean networkStock = filter.isDirectNetworkStockAt(globalIdx);
					boolean unlimited = filter.isDirectUnlimitedAt(globalIdx);
					boolean effectiveNetworkStock = networkStock || filter.isGlobalNetworkStock();
					long effectiveReserve = networkStock ? reserveAmount
							: (filter.isGlobalNetworkStock() ? filter.getGlobalReserveAmount() : 0L);
					if (key != null) {
						ItemStack icon = key.toStack(1);
						ghostSlots[i].setDirectEntry(icon, info.directFingerprint);
						ghostSlots[i].setTooltip(Tooltip.create(icon.getHoverName()));
						outputSlots[i].setDirectEntry(icon, visibleAmount, networkStock, globalIdx);
						outputSlots[i].setTooltip(Tooltip.create(Component.translatable(
								"productivebeesgenesis.gui.ae_input_config.output_slot.tooltip",
								AeInputConfigText.formatCompactAmount(visibleAmount))));
					} else {
						ghostSlots[i].setDirectFingerprint(info.directFingerprint);
						ghostSlots[i].setTooltip(Tooltip.create(Component.literal(info.directFingerprint)));
					}
					stockButtons[i].visible = true;
					stockButtons[i].active = true;
					stockButtons[i].setNetworkStock(effectiveNetworkStock);
					stockButtons[i].setUnlimited(unlimited);
					Component stockTooltip = Component.translatable(
							"productivebeesgenesis.gui.ae_input_config.stock_button.tooltip",
							Component.translatable((networkStock || filter.isGlobalNetworkStock())
									? "productivebeesgenesis.gui.ae_input_config.state.on"
									: "productivebeesgenesis.gui.ae_input_config.state.off"),
							Component.translatable(unlimited
									? "productivebeesgenesis.gui.ae_input_config.state.on"
									: "productivebeesgenesis.gui.ae_input_config.state.off"),
							AeInputConfigText.formatCompactAmount(amount),
							AeInputConfigText.formatCompactAmount(visibleAmount),
							AeInputConfigText.formatCompactAmount(effectiveReserve));
					stockButtons[i].setTooltip(Tooltip.create(stockTooltip));
					continue;
				}
				if (info != null && info.beeType != null) {
					ghostSlots[i].setEntry(info.beeType, info.isBlock);
					ItemStack icon = BeeInfoHelper.resolveBeeIcon(
							Minecraft.getInstance().level, info.beeType, info.isBlock);
					if (!icon.isEmpty()) {
						ghostSlots[i].setTooltip(Tooltip.create(icon.getHoverName()));
					}
					continue;
				}
			}
			ghostSlots[i].clear();
			ghostSlots[i].setTooltip((Tooltip) null);
		}
	}

	private void updateButtonStates(Ae2InputFilter filter) {
		toggleBtn.setMessage(Component.translatable(host.productivebeesgenesis$isAeItemInputEnabled()
				? "productivebeesgenesis.gui.ae_input_config.status.input_on"
				: "productivebeesgenesis.gui.ae_input_config.status.input_off"));
		nbtBtn.setMessage(Component.translatable(host.productivebeesgenesis$isAeInputNbtIgnore()
				? "productivebeesgenesis.gui.ae_input_config.status.nbt_on"
				: "productivebeesgenesis.gui.ae_input_config.status.nbt_off"));
		String modeKey = filter == null ? "productivebeesgenesis.gui.ae_input_config.status.filter_none"
				: switch (filter.getFilterMode()) {
					case DISABLED -> "productivebeesgenesis.gui.ae_input_config.status.filter_off";
					case WHITELIST -> "productivebeesgenesis.gui.ae_input_config.status.filter_wht";
					case BLACKLIST -> "productivebeesgenesis.gui.ae_input_config.status.filter_blk";
				};
		filterModeBtn.setMessage(Component.translatable(modeKey));
		boolean precise = filter != null && filter.isPreciseMode();
		preciseBtn.setMessage(Component.translatable(precise
				? "productivebeesgenesis.gui.ae_input_config.status.precise_on"
				: "productivebeesgenesis.gui.ae_input_config.status.precise_off"));
		globalGearBtn.setUnlimitedAllFallback(filter != null && filter.isUnlimitedAllFallback());
		reserveBtn.active = filter != null;
		reserveBtn.setGlobalStock(filter != null && filter.isGlobalNetworkStock());
	}

	private void changePage(int delta) {
		Ae2InputFilter filter = getFilter();
		int slotCount = filter == null ? 0 : filter.getCapacity();
		int total = AeInputConfigLayout.computeTotalPages(slotCount, minPages);
		currentPage = (currentPage + delta + total) % total;
	}

	private void clampCurrentPage(int slotCount) {
		int total = AeInputConfigLayout.computeTotalPages(slotCount, minPages);
		if (currentPage >= total) currentPage = total - 1;
		if (currentPage < 0) currentPage = 0;
	}

	public void setMinPages(int minPages) {
		this.minPages = Math.max(1, minPages);
	}

	/** Sends an ADD operation (with isBlock and global slot index) for a placed item. */
	private void onSlotPlaced(int pageSlotIndex, ItemStack stack) {
		if (stack == null || stack.isEmpty()) return;
		ResourceLocation beeType = CombFuzzyMatcher.getBeeType(stack);
		if (beeType == null) return;
		boolean isBlock = CombFuzzyMatcher.isCombBlock(stack);
		int globalSlotIndex = currentPage * AeInputConfigLayout.SLOTS_PER_PAGE + pageSlotIndex;
		Optional<String> directKey = Optional.empty();
		try {
			if (Minecraft.getInstance().level != null) {
				String fingerprint = Ae2ItemFingerprint.encode(AEItemKey.of(stack));
				if (!fingerprint.isBlank()) directKey = Optional.of(fingerprint);
			}
		} catch (RuntimeException ignored) {
			// Keep the legacy bee-type entry when an AE key cannot be created.
		}
		ModPayloads.CHANNEL.sendToServer(new SetAeInputFilterEntryPayload(
				pos, Optional.of(beeType), directKey, isBlock, globalSlotIndex, OperationType.ADD));
		if (directKey.isPresent()) {
			ghostSlots[pageSlotIndex].setDirectEntry(stack, directKey.get());
		}
	}

	/** Sends a REMOVE operation for the given page-local slot. */
	private void onSlotRemoved(int pageSlotIndex) {
		int globalSlotIndex = currentPage * AeInputConfigLayout.SLOTS_PER_PAGE + pageSlotIndex;
		ModPayloads.CHANNEL.sendToServer(new SetAeInputFilterEntryPayload(
				pos, Optional.empty(), false, globalSlotIndex, OperationType.REMOVE));
	}

	/** Opens the MEK amount editor for one exact AE entry. */
	private void onSlotConfigureAmount(int pageSlotIndex) {
		Ae2InputFilter filter = getFilter();
		int globalSlotIndex = currentPage * AeInputConfigLayout.SLOTS_PER_PAGE + pageSlotIndex;
		if (filter == null || !filter.isDirectEntry(globalSlotIndex)) return;

		AEItemKey key = filter.getResolvedDirectKey(globalSlotIndex);
		EntryInfo info = filter.getEntryAt(globalSlotIndex);
		if (key == null && info != null && info.directFingerprint != null
				&& Minecraft.getInstance().level != null) {
			key = Ae2ItemFingerprint.decode(info.directFingerprint);
			if (key != null) filter.resolveDirectKey(globalSlotIndex, key);
		}
		ItemStack icon = key == null ? ItemStack.EMPTY : key.toStack(1);
		gui().addWindow(new GuiAeInputAmountConfig(gui(), relativeX + 38, relativeY + 18,
				pos, globalSlotIndex, icon, filter.getDirectAmountAt(globalSlotIndex)));
	}

	/** Opens the global amount editor applied to all direct entries (no marker required). */
	private void onOpenGlobalAmount() {
		Ae2InputFilter filter = getFilter();
		long initial = 0L;
		if (filter != null) {
			for (int i = 0; i < filter.getCapacity(); i++) {
				if (filter.isDirectEntry(i)) {
					initial = filter.getDirectAmountAt(i);
					break;
				}
			}
		}
		gui().addWindow(new GuiAeInputAmountConfig(gui(), relativeX + 38, relativeY + 18,
				pos, ItemStack.EMPTY, initial));
	}

	/** Opens the explicit global network-stock reserve editor. */
	private void onOpenGlobalReserve() {
		Ae2InputFilter filter = getFilter();
		if (filter == null) return;
		long initial = filter.getGlobalReserveAmount();
		gui().addWindow(new GuiAeInputAmountConfig(gui(), relativeX + 38, relativeY + 18,
				pos, ItemStack.EMPTY, initial, true));
	}

	/** Toggle the unlimited-provide marker for a direct AE2 entry. */
	private void onSlotToggleUnlimited(int pageSlotIndex) {
		int globalSlotIndex = currentPage * AeInputConfigLayout.SLOTS_PER_PAGE + pageSlotIndex;
		ModPayloads.CHANNEL.sendToServer(new SetAeInputFilterEntryPayload(
				pos, Optional.empty(), false, globalSlotIndex, OperationType.TOGGLE_UNLIMITED));
	}

	private void onSlotToggleNetworkStock(int pageSlotIndex) {
		int globalSlotIndex = currentPage * AeInputConfigLayout.SLOTS_PER_PAGE + pageSlotIndex;
		ModPayloads.CHANNEL.sendToServer(new SetAeInputFilterEntryPayload(
				pos, Optional.empty(), false, globalSlotIndex, OperationType.TOGGLE_NETWORK_STOCK));
	}

	/** Opens the per-entry reserve editor (Shift+right-click on the gear). */
	private void onSlotConfigureReserve(int pageSlotIndex) {
		Ae2InputFilter filter = getFilter();
		int globalSlotIndex = currentPage * AeInputConfigLayout.SLOTS_PER_PAGE + pageSlotIndex;
		if (filter == null || !filter.isDirectEntry(globalSlotIndex)) return;
		AEItemKey key = filter.getResolvedDirectKey(globalSlotIndex);
		ItemStack icon = key == null ? ItemStack.EMPTY : key.toStack(1);
		gui().addWindow(new GuiAeInputAmountConfig(gui(), relativeX + 38, relativeY + 18,
				pos, globalSlotIndex, icon, filter.getDirectReserveAmountAt(globalSlotIndex), true));
	}

	private void sendClear() {
		ModPayloads.CHANNEL.sendToServer(new SetAeInputFilterEntryPayload(
				pos, Optional.empty(), false, 0, OperationType.CLEAR));
		currentPage = 0;
	}

	/** Routes ghost ingredients (e.g. JEI) to the marker slot under the cursor. */
	public boolean acceptGhostIngredient(ItemStack stack, double mouseX, double mouseY) {
		for (GhostItemWidget slot : ghostSlots) {
			if (slot.contains(mouseX, mouseY)) {
				slot.acceptGhostIngredient(stack);
				return true;
			}
		}
		return false;
	}

}
