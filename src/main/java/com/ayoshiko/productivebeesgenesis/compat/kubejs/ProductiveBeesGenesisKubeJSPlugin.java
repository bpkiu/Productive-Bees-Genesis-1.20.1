package com.ayoshiko.productivebeesgenesis.compat.kubejs;

import com.ayoshiko.productivebeesgenesis.util.PBConstants;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.RecipesEventJS;
import dev.latvian.mods.kubejs.recipe.schema.RegisterRecipeSchemasEvent;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ClassFilter;
import dev.latvian.mods.kubejs.util.ConsoleJS;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
	 * 资源蜜蜂：创世 KubeJS 插件
	 * <br/>
	 * 通过 {@link KubeJSPlugin} 接口注册自定义事件和全局绑定，使整合包作者能够
	 * 通过 KubeJS 脚本动态添加 ProductiveBees 的蜜蜂配方。
	 * <p>
	 * <b>类加载安全</b>：本类仅在被 KubeJS 框架通过 kubejs.plugins.txt 发现时加载，
	 * 即 KubeJS 已安装且初始化。未安装 KubeJS 时，本类不会被加载，模组正常工作。
	 * <p>
	 * <b>职责</b>：
	 * <ul>
	 *   <li>注册自定义事件组 {@link MyriadBeeEvents}，供脚本监听蜜蜂配方注册事件</li>
	 *   <li>注册全局绑定，暴露万象创世蜜蜂类型常量</li>
	 *   <li>在配方构建阶段（injectRuntimeRecipes）触发事件，允许脚本注入配方</li>
	 *   <li>允许脚本访问 compat.kubejs 包下的所有类</li>
	 * </ul>
	 */
public class ProductiveBeesGenesisKubeJSPlugin extends KubeJSPlugin {

	/**
	 * 注册脚本可访问的类
	 * <br/>
	 * 允许 KubeJS 脚本直接引用本模组 compat.kubejs 包下的类，
	 * 例如通过 MyriadBeeEvents.REGISTER 注册事件监听器。
	 */
	@Override
	public void registerClasses(ScriptType type, ClassFilter filter) {
		filter.allow("com.ayoshiko.productivebeesgenesis.compat.kubejs");
	}

	/**
	 * 注册全局绑定
	 * <br/>
	 * 暴露以下常量供脚本直接使用：
	 * <ul>
	 *   <li><b>MYRIAD_CREATIONS</b> — 万象创世蜜蜂类型字符串（"productivebees:myriadcreations"）</li>
	 *   <li><b>PB_MOD_ID</b> — Productive Bees 模组 ID（"productivebees"）</li>
	 * </ul>
	 * 用法示例：
	 * <pre>{@code
	 * // 直接使用全局常量
	 * console.log(MYRIAD_CREATIONS) // "productivebees:myriadcreations"
	 * }</pre>
	 */
	@Override
	public void registerBindings(BindingsEvent event) {
		event.add("MYRIAD_CREATIONS", PBConstants.MYRIADCREATIONS_TYPE_STRING);
		event.add("PB_MOD_ID", PBConstants.PRODUCTIVE_BEES_MOD_ID);
	}

	/**
	 * 注册自定义事件组
	 * <br/>
	 * 将 {@link MyriadBeeEvents#GROUP} 注册到 KubeJS 事件系统，
	 * 使脚本能够通过 MyriadBeeEvents.REGISTER.register(...) 监听蜜蜂配方注册事件。
	 */
	@Override
	public void registerEvents() {
		MyriadBeeEvents.GROUP.register();
	}

	/**
	 * 注册配方 Schema
	 * <br/>
	 * <b>设计决策</b>：对于 productivebees:centrifuge 和 productivebees:advanced_beehive，
	 * 不注册自定义 RecipeSchema。原因：
	 * <ul>
	 *   <li>这些配方的 ingredient 字段使用 PB 特有的 component 类型（嵌套 bee_type 组件），
	 *       无法用标准 RecipeComponent（IngredientComponent 等）表示</li>
	 *   <li>ChancedOutput 的嵌套结构（item 可以是 tag 或 item，外加 chance/min/max）也难以映射</li>
	 *   <li>KubeJS 会自动为所有注册的 RecipeSerializer 创建 UnknownRecipeSchemaType，
	 *       脚本已可通过 event.recipes.productivebees.centrifuge(json) 使用通用 JSON 处理</li>
	 *   <li>事件 API（addCentrifuge/addBeeProduce）提供更可靠的 JSON 注入方式</li>
	 * </ul>
	 * 对于 mekanism:mek_data，Mekanism 可能已有自己的 KubeJS 集成，此处不重复注册避免冲突。
	 * 若 Mekanism 未注册且脚本需要 typed API，可通过事件 API 的 addMekData 方法实现。
	 * <p>
	 * // TODO 1.20.1: KubeJS 2001.6.5 的 schema 注册钩子参数为 RegisterRecipeSchemasEvent
	 * （与 1.21 的 RecipeSchemaRegistry 不同）。本方法刻意保持空实现：本插件依赖上述
	 * "UnknownRecipeSchema 自动兜底 + 事件 JSON 注入" 策略，无需显式注册任何 RecipeSchema，
	 * 因此不存在行为丢失风险（该方法只为 KJS 用户扩展 typed API 预留）。
	 */
	@Override
	public void registerRecipeSchemas(RegisterRecipeSchemasEvent registry) {
		// PB 的 centrifuge 和 advanced_beehive 由 KubeJS 自动识别为 UnknownRecipeSchema
		// mek_data 由 Mekanism 自身或 KubeJS 自动处理
		// 详细的 typed API 通过事件 API（addCentrifuge/addBeeProduce/addMekData）提供
	}

	/**
	 * 配方运行时注入回调（KubeJS 2001.6.5 对应 1.21 的 beforeRecipeLoading）
	 * <br/>
	 * 在 RecipesEventJS 完成脚本处理后、最终配方表写入 RecipeManager 之前触发。
	 * runtimeRecipes 映射包含由数据包 JSON 与脚本配方解析出的全部
	 * {@link Recipe} 实例（key 为配方 ID），可直接增删条目。
	 * <p>
	 * 原理：构建 {@link MyriadBeeRegisterEventJS} 事件实例，通过
	 * {@link MyriadBeeEvents#REGISTER} 事件处理器分发给所有已注册的 KubeJS 脚本。
	 * 脚本调用 event.addBreeding() 等方法时，配方 JSON 先被收集；随后在此处按
	 * "type" 字段查找 RecipeSerializer 并解析为 Recipe 实例写入 runtimeRecipes；
	 * remove(id) 则直接从映射中移除目标配方。
	 */
	@Override
	public void injectRuntimeRecipes(RecipesEventJS event, RecipeManager manager,
			Map<ResourceLocation, Recipe<?>> runtimeRecipes) {
		Map<ResourceLocation, JsonElement> generatedJsons = new LinkedHashMap<>();
		MyriadBeeRegisterEventJS registerEvent = new MyriadBeeRegisterEventJS(generatedJsons);
		MyriadBeeEvents.REGISTER.post(ScriptType.SERVER, registerEvent);

		for (Map.Entry<ResourceLocation, JsonElement> entry : generatedJsons.entrySet()) {
			ResourceLocation id = entry.getKey();
			try {
				JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "recipe");
				String typeId = GsonHelper.getAsString(json, "type");
				RecipeSerializer<?> serializer = BuiltInRegistries.RECIPE_SERIALIZER
						.getOptional(ResourceLocation.parse(typeId)).orElse(null);
				if (serializer == null) {
					ConsoleJS.SERVER.error("[PBG-KubeJS] 配方 " + id + " 引用了未知的配方类型 '" + typeId + "'");
					continue;
				}
				Recipe<?> recipe = serializer.fromJson(id, json);
				if (recipe == null) {
					ConsoleJS.SERVER.error("[PBG-KubeJS] 配方 " + id + " 解析结果为空（类型 " + typeId + "）");
					continue;
				}
				runtimeRecipes.put(id, recipe);
			} catch (Exception ex) {
				ConsoleJS.SERVER.error("[PBG-KubeJS] 加载脚本生成的配方 " + id + " 失败", ex);
			}
		}
	}
}
