package cn.xihan.qdds.hooks

import android.content.Context
import android.os.Bundle
import cn.xihan.qdds.MeTabOptions
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.Executors

/** 移植 hide_me：按菜单名称过滤三组按钮，禁用时只发现可选项目。 */
internal object MeTabHook {
    private const val FEATURE = "我Tab选项"
    private val groups = listOf("benefitButtonList", "functionButtonList", "bottomButtonList")
    private val reportLock = Any()
    private var reportedNames: Set<String> = emptySet()
    private val reporter by lazy {
        Executors.newSingleThreadExecutor { action ->
            Thread(action, "QD-MeTab-Catalog").apply { isDaemon = true }
        }
    }

    fun install(context: Context, settings: XSharedPreferences) {
        HookSupport.attempt(FEATURE) {
            val target = XposedHelpers.findClassIfExists(
                "com.qidian.QDReader.ui.fragment.main_group.QDUserAccountRebornFragment",
                context.classLoader
            )
            val methods = target?.declaredMethods?.filter {
                it.name == "processAccountItem" && it.parameterTypes.size == 1 &&
                    it.parameterTypes[0].name ==
                    "com.qidian.QDReader.repository.entity.user_account.UserAccountItemBean"
            }.orEmpty()
            methods.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        HookSupport.attempt(FEATURE) menuAction@{
                            val bean = param.args[0] ?: return@menuAction
                            settings.reload()
                            val enabled = settings.getBoolean(MeTabOptions.ENABLED_KEY, false)
                            val hidden = if (enabled) {
                                settings.getStringSet(MeTabOptions.HIDDEN_KEY, emptySet()).orEmpty().toSet()
                            } else emptySet()
                            val names = linkedSetOf<String>()
                            var removed = 0
                            for (group in groups) {
                                // 仅读取菜单列表，不读取账户、余额、个人信息等字段。
                                val items = runCatching {
                                    XposedHelpers.getObjectField(bean, group) as? List<*>
                                }.getOrNull() ?: continue
                                val titled = items.map { item -> item to menuName(item) }
                                names.addAll(titled.mapNotNull { it.second })
                                if (hidden.isNotEmpty()) {
                                    val filtered = titled.filterNot { it.second != null && it.second in hidden }.map { it.first }
                                    if (filtered.size != items.size) {
                                        XposedHelpers.setObjectField(bean, group, ArrayList(filtered))
                                        removed += items.size - filtered.size
                                    }
                                }
                            }
                            reportCatalog(context, names)
                            if (removed > 0) HookSupport.log(FEATURE, "本次隐藏 $removed 个选项")
                        }
                    }
                })
            }
            HookSupport.log(FEATURE, "已安装 ${methods.size} 个选项 Hook")
        }
    }

    private fun menuName(item: Any?): String? {
        if (item == null) return null
        val value = runCatching { XposedHelpers.callMethod(item, "getName") }.getOrNull()
            ?: runCatching { XposedHelpers.getObjectField(item, "name") }.getOrNull()
            ?: runCatching { XposedHelpers.getObjectField(item, "Name") }.getOrNull()
        return (value as? String)?.trim()?.takeIf(MeTabOptions::validName)
    }

    private fun reportCatalog(context: Context, names: Set<String>) {
        if (names.isEmpty()) return
        val catalog = names.take(MeTabOptions.MAX_ITEMS).toSet()
        synchronized(reportLock) {
            if (catalog == reportedNames) return
            reportedNames = catalog
        }
        reporter.execute {
            val success = HookSupport.attempt("我Tab选项目录") {
                val extras = Bundle().apply {
                    putStringArrayList(MeTabOptions.ITEMS_KEY, ArrayList(catalog))
                }
                val result = context.contentResolver.call(
                    MeTabOptions.catalogUri, MeTabOptions.REPORT_METHOD, null, extras
                )
                check(result?.getBoolean("ok") == true)
                HookSupport.log(FEATURE, "已收集 ${catalog.size} 个选项")
            }
            if (!success) synchronized(reportLock) {
                if (reportedNames == catalog) reportedNames = emptySet()
            }
        }
    }
}
