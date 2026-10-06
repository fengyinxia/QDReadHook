package cn.xihan.qdds.hooks

import android.content.Context
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Method

/** 一次初始化复用 DexKit；回调不持有已关闭的 Bridge。 */
internal class OptionHookScope(val context: Context, val prefs: XSharedPreferences) : AutoCloseable {
    val loader: ClassLoader = context.classLoader
    private val bridgeHolder = lazy {
        System.loadLibrary("dexkit")
        DexKitBridge.create(context.applicationInfo.sourceDir)
    }
    val bridge: DexKitBridge get() = bridgeHolder.value

    fun enabled(key: String): Boolean = prefs.getBoolean(key, false)
    fun selected(key: String): Set<String> = prefs.getStringSet(key, emptySet()).orEmpty().toSet()
    fun clazz(name: String): Class<*>? = XposedHelpers.findClassIfExists(name, loader)
    fun methods(name: String, method: String, count: Int? = null): List<Method> =
        clazz(name)?.declaredMethods?.filter {
            it.name == method && (count == null || it.parameterTypes.size == count)
        }.orEmpty()

    fun find(
        methodStrings: List<String> = emptyList(), classStrings: List<String> = emptyList(),
        params: List<String?>? = null, count: Int? = null, returns: String? = null
    ): List<Method> {
        val matcher = MethodMatcher.create()
        if (methodStrings.isNotEmpty()) matcher.usingStrings(methodStrings)
        if (classStrings.isNotEmpty()) matcher.declaredClass(ClassMatcher.create().usingStrings(classStrings))
        if (params != null) matcher.paramTypes(params)
        if (count != null) matcher.paramCount(count)
        if (returns != null) matcher.returnType(returns)
        return bridge.findMethod(FindMethod.create().matcher(matcher))
            .mapNotNull { runCatching { it.getMethodInstance(loader) }.getOrNull() }.distinct()
    }

    fun before(feature: String, methods: List<Method>, action: (XC_MethodHook.MethodHookParam) -> Unit) {
        var installed = 0
        methods.forEach { method ->
            if (HookSupport.attempt(feature) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        HookSupport.attempt(feature) { action(param) }
                    }
                })
            }) installed++
        }
        HookSupport.log(feature, "已安装 $installed 个方法")
    }

    fun after(feature: String, methods: List<Method>, action: (XC_MethodHook.MethodHookParam) -> Unit) {
        var installed = 0
        methods.forEach { method ->
            if (HookSupport.attempt(feature) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!param.hasThrowable()) HookSupport.attempt(feature) { action(param) }
                    }
                })
            }) installed++
        }
        HookSupport.log(feature, "已安装 $installed 个方法")
    }

    fun skip(feature: String, methods: List<Method>, value: Any? = null) {
        before(feature, methods) { param ->
            val type = (param.method as Method).returnType
            param.result = value ?: when (type) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Float.TYPE -> 0f
                java.lang.Double.TYPE -> 0.0
                java.lang.Short.TYPE -> 0.toShort()
                java.lang.Byte.TYPE -> 0.toByte()
                java.lang.Character.TYPE -> '\u0000'
                else -> null
            }
        }
    }

    override fun close() {
        if (bridgeHolder.isInitialized()) bridgeHolder.value.close()
    }

    companion object {
        const val HOST = "com.qidian.QDReader"
        fun safeMediaPath(file: java.io.File): Boolean {
            val parts = file.canonicalPath.split('/')
            if (parts.any { it.equals("secrets", true) }) return false
            val name = file.canonicalFile.name
            if (name.equals("auth.json", true) || name.equals("trust.json", true)) return false
            return !Regex("^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])([.].*)?$", RegexOption.IGNORE_CASE).matches(name)
        }
        fun field(instance: Any?, name: String): Any? = instance?.let {
            runCatching { XposedHelpers.getObjectField(it, name) }.getOrNull()
        }
        fun views(root: View): List<View> {
            val result = mutableListOf<View>()
            val queue = java.util.ArrayDeque<View>()
            queue.add(root)
            while (!queue.isEmpty()) {
                val view = queue.removeFirst()
                result.add(view)
                if (view is ViewGroup) for (i in 0 until view.childCount) queue.add(view.getChildAt(i))
            }
            return result
        }
    }
}
