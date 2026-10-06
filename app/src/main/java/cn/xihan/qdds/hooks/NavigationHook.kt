package cn.xihan.qdds.hooks

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import cn.xihan.qdds.FeatureOptions
import cn.xihan.qdds.MeTabOptions
import java.util.concurrent.Executors

internal object NavigationHook {
    private var reported: Set<String> = emptySet()
    private val reporter by lazy { Executors.newSingleThreadExecutor { r ->
        Thread(r, "QD-Navigation-Catalog").apply { isDaemon = true }
    } }

    fun install(scope: OptionHookScope) {
        val dots = scope.selected(FeatureOptions.RED_DOTS)
        val dotName = "${OptionHookScope.HOST}.framework.widget.customerview.SmallDotsView"
        val account = "${OptionHookScope.HOST}.ui.fragment.main_group.QDUserAccountRebornFragment"
        if ("part_red_dot" in dots) scope.skip("部分小红点", scope.methods(dotName, "onDraw", 1))
        if ("me_red_dot" in dots) {
            scope.skip("我消息红点开关", scope.methods(
                "${OptionHookScope.HOST}.component.config.QDAppConfigHelper\$Companion", "isEnableUniteMessage"
            ), false)
            scope.after("我消息红点", scope.methods(account, "initView")) {
                (OptionHookScope.field(it.thisObject, "msgDotView") as? View)?.visibility = View.GONE
            }
        }
        if (!scope.enabled(FeatureOptions.NAV_ENABLED) && "bottom_navigation_bar" !in dots) return
        val markers = listOf(
            "Icon tab provider return null when index in [0, tab count).", "tabLayout",
            "BOTTOM_TAB_OPERATION_RED_DOT_"
        )
        val methods = scope.find(methodStrings = markers)
        scope.after("底部导航", methods) { param ->
            val root = HookSupport.fieldsOfType(param.thisObject, LinearLayout::class.java)
                .filterIsInstance<LinearLayout>().firstOrNull() ?: return@after
            val views = OptionHookScope.views(root)
            if ("bottom_navigation_bar" in dots) {
                val dotClass = scope.clazz(dotName)
                if (dotClass != null) views.filter { dotClass.isInstance(it) }.forEach { it.visibility = View.GONE }
            }
            val labels = views.filterIsInstance<TextView>().filter {
                val title = it.text.toString().trim()
                val slot = (it.parent as? View)?.parent as? View
                title.isNotBlank() && title.any(Char::isLetter) && !title.contains("回到顶部") &&
                    title.length <= 20 && slot?.parent === root
            }
            val names = labels.map { it.text.toString().trim() }.filter(MeTabOptions::validName).toSet()
            if (scope.enabled(FeatureOptions.NAV_ENABLED)) {
                report(scope, names)
                val hidden = scope.selected(FeatureOptions.NAV_HIDDEN)
                labels.filter { it.text.toString().trim() in hidden }.forEach {
                    ((it.parent as? View)?.parent as? View)?.visibility = View.GONE
                }
            }
        }
    }

    private fun report(scope: OptionHookScope, names: Set<String>) {
        if (names.isEmpty()) return
        synchronized(this) {
            if (reported == names) return
            reported = names
        }
        reporter.execute {
            if (!HookSupport.attempt("底部导航目录") {
                val bundle = Bundle().apply { putStringArrayList(MeTabOptions.ITEMS_KEY, ArrayList(names)) }
                val result = scope.context.contentResolver.call(
                    MeTabOptions.catalogUri, FeatureOptions.REPORT_NAV, null, bundle
                )
                check(result?.getBoolean("ok") == true)
            }) synchronized(this) { if (reported == names) reported = emptySet() }
        }
    }
}
