package cn.xihan.qdds.hooks

import android.app.Activity
import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.json.JSONObject
import java.lang.reflect.Method
import java.util.HashMap

/** 迁移新版 unlock_ad；仅处理明确定位到的奖励流程，不修改任意 WebView 回调。 */
internal object RewardAdHook {
    private const val FEATURE = "免广告领取奖励"
    private const val HOST_PACKAGE = "com.qidian.QDReader"
    private const val WEB_PACKAGE = "$HOST_PACKAGE.framework.webview"
    private const val REWARD_CONFIG = "$HOST_PACKAGE.repository.entity.config.RewardVideoConfig"
    @Volatile private var bypassHealthy = false

    fun install(scope: OptionHookScope) {
        HookSupport.attempt(FEATURE) {
            val loader = scope.loader
            if (!installRewardCallback(scope)) {
                HookSupport.log(FEATURE, "奖励入口定位失败，保留原有广告和领取流程")
                return@attempt
            }
            // 只有核心奖励回调安装成功，才启用广告窗口及相关状态处理。
            HookSupport.attempt("奖励广告记录") { installAdRecords(loader) }
            HookSupport.attempt("奖励标识") { installRewardId(loader) }
            HookSupport.attempt("互动奖励") { installInteractReward(scope) }
            HookSupport.attempt("奖励广告窗口") { installAdActivities(loader) }
        }
    }

    private fun installRewardCallback(scope: OptionHookScope): Boolean {
        val callbacks = scope.find(
            classStrings = listOf("WebViewPlugin：webview ready to call js...func="),
            params = listOf("java.lang.String", "org.json.JSONObject", "int"),
            returns = "void", searchPackage = WEB_PACKAGE
        )
        HookSupport.log(FEATURE, "Web 回调候选=${callbacks.size}")
        val callback = callbacks.singleOrNull() ?: return false

        // R8 会将奖励插件重打包到 bf 等包，不能按宿主包名限制搜索。
        // 字符串、完整签名及父类关系共同限定匹配范围。
        val rewards = scope.find(
            classStrings = listOf("execCallback", "callbackId", "status"),
            params = listOf("int", "java.lang.String", REWARD_CONFIG, "java.lang.String"),
            returns = "void"
        ).filter { callback.declaringClass.isAssignableFrom(it.declaringClass) }
        HookSupport.log(FEATURE, "奖励入口候选=${rewards.size}")
        if (rewards.isEmpty()) return false

        callback.isAccessible = true
        var installed = 0
        rewards.forEach { method ->
            if (HookSupport.attempt(FEATURE) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        var delivered = false
                        HookSupport.attempt("奖励回调") callbackAction@{
                            val config = param.args[2] ?: return@callbackAction
                            val key = XposedHelpers.getObjectField(config, "adCodeId") ?: return@callbackAction
                            val payload = JSONObject().put("status", 2).put("key", key)
                            callback.invoke(param.thisObject, "execCallback", payload, param.args[0])
                            delivered = true
                            HookSupport.log(FEATURE, "奖励回调已分发（不代表服务端已发奖）")
                        }
                        // 失败时保留原方法，并暂停辅助广告处理，下一次成功分发后恢复。
                        bypassHealthy = delivered
                        if (delivered) param.result = null
                    }
                })
            }) installed++
        }
        bypassHealthy = installed > 0
        if (installed > 0) HookSupport.log(FEATURE, "已安装 $installed 个奖励入口 Hook")
        return installed > 0
    }

    private fun installAdRecords(loader: ClassLoader) {
        val target = XposedHelpers.findClassIfExists("$HOST_PACKAGE.extras.GDTForQD", loader) ?: return
        target.declaredMethods.filter {
            it.parameterTypes.contentEquals(arrayOf(String::class.java))
        }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.hasThrowable() || !bypassHealthy) return
                    HookSupport.attempt("奖励广告记录") {
                        val id = param.args[0] as? String ?: return@attempt
                        val instance = param.thisObject ?: return@attempt
                        HookSupport.fieldsOfType(instance, HashMap::class.java).forEach { value ->
                            @Suppress("UNCHECKED_CAST")
                            val map = value as MutableMap<String, String>
                            map[id] = id
                        }
                    }
                }
            })
        }
    }

    private fun installRewardId(loader: ClassLoader) {
        val target = XposedHelpers.findClassIfExists("$HOST_PACKAGE.repository.entity.Reward", loader) ?: return
        target.declaredMethods.filter {
            it.name == "getRewardId" && it.parameterTypes.isEmpty() &&
                it.returnType == java.lang.Long.TYPE
        }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (bypassHealthy && !param.hasThrowable()) param.result = 1L
                }
            })
        }
    }

    private fun installInteractReward(scope: OptionHookScope) {
        val methods = scope.find(params = listOf(
            "$HOST_PACKAGE.ui.modules.interact.InteractHBContainerView",
            "kotlin.jvm.internal.Ref\$ObjectRef", "java.lang.Integer"
        ))
        methods.forEach { method: Method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (bypassHealthy) param.args[2] = 5
                }
            })
        }
    }

    private fun installAdActivities(loader: ClassLoader) {
        listOf(
            "com.qq.e.tg.ADActivity", "$HOST_PACKAGE.ui.activity.QDVideoActivity",
            "$HOST_PACKAGE.ui.activity.QDRareVideoActivity"
        ).forEach { name ->
            HookSupport.attempt("奖励广告窗口") {
                val target = XposedHelpers.findClassIfExists(name, loader) ?: return@attempt
                XposedHelpers.findAndHookMethod(target, "onCreate", Bundle::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!bypassHealthy) return
                            HookSupport.attempt("奖励广告窗口") {
                                (param.thisObject as? Activity)?.finish()
                            }
                        }
                    })
            }
        }
    }
}
