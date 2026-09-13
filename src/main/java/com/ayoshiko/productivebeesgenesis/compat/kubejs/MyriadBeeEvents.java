package com.ayoshiko.productivebeesgenesis.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

/**
	 * KubeJS 蜜蜂配方事件组
	 * <br/>
	 * 定义万象创世蜜蜂配方相关的自定义事件，供整合包作者通过 KubeJS 脚本注册。
	 * <p>
	 * 事件在配方注入阶段触发（KubeJSPlugin#injectRuntimeRecipes），此时脚本构建的 recipeJsons
	 * 收集映射完成填充，经序列化器解析为 Recipe 实例后由原版 RecipeManager 统一应用。
	 * <p>
	 * 用法示例（server_scripts）：
	 * <pre>{@code
	 * MyriadBeeEvents.REGISTER.register(event => {
	 *     event.addBreeding('mymod:custom_breeding', 'productivebees:iron_bee', 'productivebees:gold_bee',
	 * 'productivebees:myriadcreations')
	 * })
	 * }</pre>
	 */
public interface MyriadBeeEvents {

	/** 事件组 — 在 KubeJS 脚本中通过 MyriadBeeEvents.REGISTER 访问 */
	EventGroup GROUP = EventGroup.of("MyriadBeeEvents");

	/** 蜜蜂配方注册事件 — 服务端脚本阶段触发 */
	EventHandler REGISTER = GROUP.server("register", () -> MyriadBeeRegisterEventJS.class);
}
