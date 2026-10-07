package cn.xihan.qdds.hooks

import android.content.Context
import android.util.AtomicFile
import cn.xihan.qdds.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Method

/** 只存方法描述符；不存账号、偏好、ClassLoader 或 DexKit 对象。仅启动安装线程访问。 */
internal class HookMethodCache(context: Context, private val loader: ClassLoader) {
    private val file = AtomicFile(File(context.codeCacheDir, "qdreadhook/methods.json"))
    private val identity: String = run {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val paths = listOf(context.applicationInfo.sourceDir) +
            context.applicationInfo.splitSourceDirs.orEmpty().toList()
        JSONArray().put(CACHE_REVISION).put(BuildConfig.VERSION_CODE).put(context.packageName)
            .put(info.versionCode).put(info.lastUpdateTime)
            .put(JSONArray(paths.map { path ->
                val apk = File(path)
                JSONArray().put(path).put(apk.length()).put(apk.lastModified())
            })).toString()
    }
    private val entries: JSONObject = load()
    private var dirty = false

    private fun load(): JSONObject = try {
        // 损坏/过大/宿主升级的缓存都按未命中处理，不拖慢启动，不读取其他宿主文件。
        if (file.baseFile.length() > MAX_BYTES) JSONObject()
        else {
            val root = JSONObject(file.openRead().bufferedReader().use { it.readText() })
            if (root.optString("identity") == identity) root.getJSONObject("queries") else JSONObject()
        }
    } catch (_: Exception) {
        JSONObject()
    }

    /** null 为未命中；空列表为本版本确实没有匹配目标，避免每次启动重复查零结果。 */
    fun get(key: String): List<Method>? {
        if (!entries.has(key)) return null
        return try {
            val descriptors = entries.getJSONArray(key)
            List(descriptors.length()) { index ->
                val descriptor = descriptors.getJSONObject(index)
                val parameters = descriptor.getJSONArray("parameters")
                val declaring = Class.forName(descriptor.getString("class"), false, loader)
                val method = declaring.getDeclaredMethod(descriptor.getString("name"),
                    *Array(parameters.length()) { type(parameters.getString(it)) })
                check(method.returnType.name == descriptor.getString("returns"))
                check(method.modifiers == descriptor.getInt("modifiers"))
                method
            }
        } catch (_: Exception) {
            invalidate(key)
            null
        } catch (_: LinkageError) {
            invalidate(key)
            null
        }
    }

    private fun invalidate(key: String) {
        entries.remove(key)
        dirty = true
    }

    fun put(key: String, methods: List<Method>) {
        val descriptors = JSONArray()
        methods.forEach { method ->
            descriptors.put(JSONObject().put("class", method.declaringClass.name)
                .put("name", method.name).put("returns", method.returnType.name)
                .put("modifiers", method.modifiers)
                .put("parameters", JSONArray(method.parameterTypes.map { it.name })))
        }
        entries.put(key, descriptors)
        dirty = true
    }

    /** 只在后台补查结束后写入；原子替换，进程中断不会留下半份缓存。 */
    fun save() {
        if (!dirty) return
        HookSupport.attempt("定位缓存") {
            val bytes = JSONObject().put("identity", identity).put("queries", entries)
                .toString().toByteArray(Charsets.UTF_8)
            check(bytes.size <= MAX_BYTES)
            val directory = requireNotNull(file.baseFile.parentFile)
            check(directory.isDirectory || directory.mkdirs())
            val output = file.startWrite()
            try {
                output.write(bytes)
                file.finishWrite(output)
                dirty = false
            } catch (error: Throwable) {
                file.failWrite(output)
                throw error
            }
        }
    }

    private fun type(name: String): Class<*> = when (name) {
        "boolean" -> java.lang.Boolean.TYPE
        "byte" -> java.lang.Byte.TYPE
        "char" -> java.lang.Character.TYPE
        "short" -> java.lang.Short.TYPE
        "int" -> java.lang.Integer.TYPE
        "long" -> java.lang.Long.TYPE
        "float" -> java.lang.Float.TYPE
        "double" -> java.lang.Double.TYPE
        "void" -> Void.TYPE
        else -> Class.forName(name, false, loader)
    }

    companion object {
        // 修改自定义定位规则语义时递增；普通查询条件本身也属于缓存键。
        private const val CACHE_REVISION = 2
        private const val MAX_BYTES = 128 * 1024
    }
}
