package com.ayoshiko.productivebeesgenesis.client.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * 自定义配置屏幕工厂（Forge 1.20.1 适配）
 * <br/>
 * 原 NeoForge 版本基于 {@code net.neoforged.neoforge.client.ConfigurationScreen} 框架
 * （按 ModConfig.Type 分发到通用/客户端/服务端配置节页面），该框架类在 Forge 1.20.1 中不存在。
 * Forge 1.20.1 的对应机制是扩展点 {@link ConfigScreenHandler.ConfigScreenFactory}，
 * 故此处直接以自定义 {@link ServerConfigScreen} 作为配置入口：
 * <ul>
 *   <li>“万象创世过滤” — 打开 {@link FilterListScreen}，支持搜索、多选、滚动、全选</li>
 *   <li>“其他服务端配置” — 打开 {@link BalanceConfigurationScreen}
 *       （平衡性预设列表选择器 + 过滤模式切换）</li>
 * </ul>
 * 其余细粒度配置项（CLIENT/COMMON/服务端其余键）请直接编辑对应的 config.toml 文件。
 */
public final class CustomConfigScreenFactory {

	private CustomConfigScreenFactory() {}

	/**
	 * 注册“模组列表 → 配置屏幕”扩展点。
	 * <br/>
	 * 仅供主类在 {@code dist == CLIENT} 时调用一次；注册本身不实例化任何客户端 GUI 类，
	 * lambda 体中的屏幕类仅在玩家实际点击配置按钮时才会被解析执行。
	 */
	@SuppressWarnings("removal")
	public static void register() {
		ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory(
						parent -> new ServerConfigScreen(parent)));
	}
}
