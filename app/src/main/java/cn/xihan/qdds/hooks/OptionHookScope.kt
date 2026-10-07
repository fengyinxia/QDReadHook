package cn.xihan.qdds.hooks

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.json.JSONArray
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Method

/** 主线程只读定位缓存；未命中的安装单元延后执行，后台补查共享一个 DexKit。 */
internal class OptionHookScope(val context: Context, val prefs: XSharedPreferences) : AutoCloseable {
    val loader: ClassLoader = context.classLoader
    private val cache = HookMethodCache(context, loader)
    private val deferred = mutableListOf<Pair<String, () -> Unit>>()
    private val installedHooks = mutableSetOf<Triple<String, Method, Boolean>>()
    private var scanningAllowed = false
    private var closed = false
    var cacheHits = 0
        private set
    var scans = 0
        private set
    val deferredCount: Int get() = deferred.size
    private val bridgeHolder = lazy {
        check(scanningAllowed && Looper.myLooper() != Looper.getMainLooper())
        System.loadLibrary("dexkit")
        DexKitBridge.create(context.applicationInfo.sourceDir)
    }

    fun enabled(key: String): Boolean = prefs.getBoolean(key, false)
    fun selected(key: String): Set<String> = prefs.getStringSet(key, emptySet()).orEmpty().toSet()
    fun clazz(name: String): Class<*>? = XposedHelpers.findClassIfExists(name, loader)
    fun methods(name: String, method: String, count: Int? = null): List<Method> =
        clazz(name)?.declaredMethods?.filter {
            it.name == method && (count == null || it.parameterTypes.size == count)
        }.orEmpty()

    fun find(
        methodStrings: List<String> = emptyList(), classStrings: List<String> = emptyList(),
        params: List<String?>? = null, count: Int? = null, returns: String? = null,
        searchPackage: String? = null
    ): List<Method> {
        val key = JSONArray().put("strings-and-signature-v1")
            .put(JSONArray(methodStrings)).put(JSONArray(classStrings))
            .put(params?.let { JSONArray(it) }).put(count).put(returns).put(searchPackage).toString()
        return lookup(key) {
            val matcher = MethodMatcher.create()
            if (methodStrings.isNotEmpty()) matcher.usingStrings(methodStrings)
            if (classStrings.isNotEmpty()) matcher.declaredClass(ClassMatcher.create().usingStrings(classStrings))
            if (params != null) matcher.paramTypes(params)
            if (count != null) matcher.paramCount(count)
            if (returns != null) matcher.returnType(returns)
            val query = FindMethod.create().matcher(matcher)
            if (searchPackage != null) query.searchPackages(searchPackage)
            query
        }
    }

    /** 自定义 matcher 必须给出带规则版本的稳定键，不能绕过主线程扫描保护。 */
    fun lookup(key: String, query: () -> FindMethod): List<Method> {
        check(!closed)
        cache.get(key)?.let { cacheHits++; return it }
        if (!scanningAllowed) throw DeferredHookLookup(this)
        check(Looper.myLooper() != Looper.getMainLooper())
        scans++
        // 保留原定位行为：void 查询可能包含构造器等非普通方法，不能让它中断整项安装。
        val methods = bridgeHolder.value.findMethod(query())
            .mapNotNull { runCatching { it.getMethodInstance(loader) }.getOrNull() }.distinct()
        cache.put(key, methods)
        return methods
    }

    fun defer(feature: String, action: () -> Unit) {
        check(!scanningAllowed && !closed)
        deferred.add(feature to action)
        HookSupport.log(feature, "定位未缓存，延后到首帧后补查")
    }

    fun completeDeferred() {
        check(Looper.myLooper() != Looper.getMainLooper())
        scanningAllowed = true
        val tasks = deferred.toList()
        deferred.clear()
        try {
            tasks.forEach { (feature, action) -> HookSupport.attempt(feature, action) }
        } finally {
            cache.save()
            close()
        }
    }

    fun before(feature: String, methods: List<Method>, action: (XC_MethodHook.MethodHookParam) -> Unit) {
        var installed = 0
        methods.forEach { method ->
            val key = Triple(feature, method, true)
            if (key !in installedHooks && HookSupport.attempt(feature) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        HookSupport.attempt(feature) { action(param) }
                    }
                })
                installedHooks.add(key)
            }) installed++
        }
        if (installed > 0 || methods.isEmpty()) HookSupport.log(feature, "已安装 $installed 个方法")
    }

    fun after(feature: String, methods: List<Method>, action: (XC_MethodHook.MethodHookParam) -> Unit) {
        var installed = 0
        methods.forEach { method ->
            val key = Triple(feature, method, false)
            if (key !in installedHooks && HookSupport.attempt(feature) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!param.hasThrowable()) HookSupport.attempt(feature) { action(param) }
                    }
                })
                installedHooks.add(key)
            }) installed++
        }
        if (installed > 0 || methods.isEmpty()) HookSupport.log(feature, "已安装 $installed 个方法")
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
        if (closed) return
        closed = true
        deferred.clear()
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
