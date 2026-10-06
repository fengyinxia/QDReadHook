package cn.xihan.qdds.hooks

import android.os.SystemClock
import android.view.View
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.WeakHashMap
import kotlin.random.Random

/** 新版按 binding 中的控件类型定位，旧版保留原有的两套签到布局。 */
internal object AutoSignHook {
    private const val FEATURE = "自动签到"
    private val scheduled = WeakHashMap<View, Long>()

    fun install(classLoader: ClassLoader) {
        HookSupport.attempt(FEATURE) {
            val modernClass = XposedHelpers.findClassIfExists(
                "com.qidian.QDReader.ui.modules.bookshelf.view.BookShelfCheckInView",
                classLoader
            )
            val methods = modernClass?.declaredMethods?.filter {
                it.name == "updateCheckIn" && it.parameterTypes.size == 2
            }.orEmpty()
            if (methods.isNotEmpty()) {
                val buttonClass = XposedHelpers.findClass(
                    "com.qd.ui.component.widget.QDUIButton", classLoader
                )
                methods.forEach { method ->
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (param.hasThrowable()) return
                            HookSupport.attempt(FEATURE) signAction@{
                                val binding = XposedHelpers.getObjectField(param.thisObject, "binding")
                                    ?: return@signAction
                                HookSupport.fieldsOfType(binding, buttonClass).filterIsInstance<View>()
                                    .forEach { button ->
                                        schedule(button) { hasSignText(button) }
                                    }
                            }
                        }
                    })
                }
                HookSupport.log(FEATURE, "已安装新版签到 Hook")
            } else {
                // 自动兼容两套旧签到控件，不再依赖或切换宿主布局。
                var legacyHooks = 0
                for (useOldLayout in listOf(true, false)) {
                    HookSupport.attempt(FEATURE) {
                        legacyHooks += installLegacy(classLoader, useOldLayout)
                    }
                }
                if (legacyHooks == 0) HookSupport.log(FEATURE, "未找到签到控件，未安装 Hook")
            }
        }
    }

    private fun hasSignText(button: Any): Boolean {
        return HookSupport.fieldsOfType(button, TextView::class.java)
            .filterIsInstance<TextView>().any { it.text.toString() == "签到" }
    }

    private fun schedule(button: View, isUnsigned: () -> Boolean) {
        if (!isUnsigned()) return
        synchronized(scheduled) {
            val now = SystemClock.elapsedRealtime()
            val last = scheduled[button]
            if (last != null && now - last < 10_000) return
            scheduled[button] = now
        }
        val posted = button.postDelayed({
            HookSupport.attempt(FEATURE) {
                if (button.windowToken != null && button.isShown && button.isEnabled && isUnsigned()) {
                    button.performClick()
                }
            }
        }, Random.nextLong(50, 550))
        if (!posted) synchronized(scheduled) { scheduled.remove(button) }
    }

    private fun installLegacy(classLoader: ClassLoader, useOldLayout: Boolean): Int {
        val className = "com.qidian.QDReader.ui.view.bookshelfview." +
            if (useOldLayout) "CheckInReadingTimeView" else "CheckInReadingTimeViewNew"
        val target = XposedHelpers.findClassIfExists(className, classLoader) ?: return 0
        val methodName = if (useOldLayout) "S" else "E"
        val hooks = XposedBridge.hookAllMethods(target, methodName, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.hasThrowable()) return
                HookSupport.attempt(FEATURE) {
                    if (useOldLayout) {
                        val text = XposedHelpers.getObjectField(param.thisObject, "m") as? TextView
                        val button = XposedHelpers.getObjectField(param.thisObject, "l") as? View
                        if (button != null) schedule(button) { text?.text?.toString() == "签到" }
                    } else {
                        val button = XposedHelpers.getObjectField(param.thisObject, "s") as? View
                        val text = button?.let { XposedHelpers.getObjectField(it, "e") as? TextView }
                        if (button != null) schedule(button) { text?.text?.toString() == "签到" }
                    }
                }
            }
        })
        HookSupport.log(FEATURE, "已安装旧版签到 Hook：${hooks.size} 个方法")
        return hooks.size
    }
}
