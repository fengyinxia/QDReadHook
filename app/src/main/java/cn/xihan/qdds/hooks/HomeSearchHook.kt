package cn.xihan.qdds.hooks

import cn.xihan.qdds.FeatureOptions
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.query.matchers.MethodsMatcher

internal object HomeSearchHook {
    fun install(scope: OptionHookScope) {
        val host = OptionHookScope.HOST
        val home = scope.selected(FeatureOptions.HOME)
        if ("home_top_box_tip" in home) {
            scope.skip("主页宝箱提示", scope.methods("$host.ui.activity.MainGroupActivity", "getGlobalMsg"))
        }
        if ("book_shelf_daily_recommend" in home) {
            val required = MethodsMatcher.create()
                .add(MethodMatcher.create().paramTypes(listOf("int")).returnType("$host.repository.entity.BookItem"))
                .add(MethodMatcher.create().paramTypes(listOf("$host.repository.entity.BookShelfItem")).returnType("void"))
            val matcher = MethodMatcher.create().name("getHeaderItemCount").paramCount(0).returnType("int")
                .declaredClass(ClassMatcher.create().methods(required))
            val methods = scope.bridge.findMethod(FindMethod.create().matcher(matcher))
                .mapNotNull { runCatching { it.getMethodInstance(scope.loader) }.getOrNull() }
            scope.skip("书架每日导读", methods, 0)
            scope.skip("每日导读数据", scope.methods("$host.ui.modules.bookshelf.BookShelfViewModel", "fetchDailyReading", 2))
        }
        if ("book_shelf_top_title" in home) scope.skip("书架顶部标题", scope.methods(
            "$host.ui.modules.bookshelf.adapter.BaseBooksAdapter", "getHeaderItemCount", 0
        ), 0)
        val hidden = scope.selected(FeatureOptions.SEARCH)
        if (hidden.isNotEmpty()) {
            val methods = scope.find(classStrings = listOf("combineBean", "dataList"),
                methodStrings = listOf("dataList"), params = listOf("java.util.List"), returns = "void")
            scope.before("搜索选项", methods) { param ->
                val original = param.args[0] as? List<*> ?: return@before
                val filtered = original.filterNot { item ->
                    val type = OptionHookScope.field(item, "type")
                    type != null && type.toString() in hidden
                }
                if (filtered.size != original.size) {
                    param.args[0] = ArrayList(filtered)
                    HookSupport.log("搜索选项", "本次隐藏 ${original.size - filtered.size} 个分组")
                }
            }
        }
    }
}
