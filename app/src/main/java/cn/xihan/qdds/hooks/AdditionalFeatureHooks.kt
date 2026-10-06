package cn.xihan.qdds.hooks

import android.content.Context
import de.robv.android.xposed.XSharedPreferences

internal object AdditionalFeatureHooks {
    fun install(context: Context, prefs: XSharedPreferences) {
        OptionHookScope(context, prefs).use { scope ->
            HookSupport.attempt("小红点及底部导航") { NavigationHook.install(scope) }
            HookSupport.attempt("广告拦截") { AdBlockHook.install(scope) }
            HookSupport.attempt("拦截设置") { InterceptHook.install(scope) }
            HookSupport.attempt("章评操作") { ChapterCommentHook.install(scope) }
            HookSupport.attempt("章末选项") { ChapterEndHook.install(scope) }
            HookSupport.attempt("主页及搜索") { HomeSearchHook.install(scope) }
            HookSupport.attempt("会员卡背景") { MemberBackgroundHook.install(scope) }
        }
    }
}
