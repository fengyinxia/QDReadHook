package cn.xihan.qdds.hooks

import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Modifier

internal object HookSupport {
    fun log(feature: String, message: String) {
        XposedBridge.log("[QDReadHook][$feature] $message")
    }

    fun attempt(feature: String, action: () -> Unit): Boolean {
        return try {
            action()
            true
        } catch (error: Throwable) {
            // 不记录回调参数、JSON 或账号数据。
            log(feature, "执行失败：${error.javaClass.simpleName}")
            false
        }
    }

    fun fieldsOfType(instance: Any, type: Class<*>): List<Any> {
        val values = mutableListOf<Any>()
        var current: Class<*>? = instance.javaClass
        while (current != null && current != Any::class.java) {
            current.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.forEach { field ->
                runCatching {
                    field.isAccessible = true
                    field.get(instance)?.takeIf { type.isInstance(it) }?.let(values::add)
                }
            }
            current = current.superclass
        }
        return values
    }
}
