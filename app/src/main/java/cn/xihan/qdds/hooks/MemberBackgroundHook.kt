package cn.xihan.qdds.hooks

import cn.xihan.qdds.FeatureOptions
import de.robv.android.xposed.XposedHelpers

/** 仅改阅读背景列表的本地展示字段，不改会员身份、余额或服务端响应。 */
internal object MemberBackgroundHook {
    fun install(scope: OptionHookScope) {
        if (!scope.enabled(FeatureOptions.MEMBER_BACKGROUND)) return
        val entity = "${OptionHookScope.HOST}.repository.dal.store.ReaderThemeEntity"
        scope.before("会员卡背景", scope.methods(
            "${OptionHookScope.HOST}.ui.activity.QDReaderThemeDetailActivity", "updateViews", 1
        ).filter { it.parameterTypes[0] == java.util.List::class.java }) { param ->
            val items = param.args[0] as? List<*> ?: return@before
            var changed = 0
            items.filterNotNull().filter { it.javaClass.name == entity }.forEach { item ->
                if (XposedHelpers.getLongField(item, "themeType") == 102L) {
                    XposedHelpers.setLongField(item, "themeType", 101L)
                    XposedHelpers.setIntField(item, "haveStatus", 1)
                    changed++
                }
            }
            if (changed > 0) HookSupport.log("会员卡背景", "本次调整 $changed 个背景展示项")
        }
    }
}
