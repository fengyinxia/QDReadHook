package cn.xihan.qdds.hooks

import cn.xihan.qdds.FeatureOptions
import java.io.File

internal object InterceptHook {
    fun install(scope: OptionHookScope) {
        val host = OptionHookScope.HOST
        val main = "$host.ui.activity.MainGroupActivity"
        val selected = scope.selected(FeatureOptions.INTERCEPT)
        selected.forEach { option -> HookSupport.attempt("拦截/$option") {
            when (option) {
                "websocket" -> scope.skip(option, scope.find(
                    methodStrings = listOf("handleOpen WebSocket isOpen"), returns = "void"))
                "post_image_watermark" -> scope.before(option,
                    scope.methods("$host.ui.activity.CirclePostEditActivity", "addInk2BitmapFile", 2)
                        .filter { it.parameterTypes.map { type -> type.name } == listOf("java.lang.String", "java.io.File") }
                ) { param ->
                    val source = File(param.args[0] as String)
                    val destination = param.args[1] as File
                    if (source.isFile && OptionHookScope.safeMediaPath(source) &&
                        OptionHookScope.safeMediaPath(destination) && source.canonicalFile != destination.canonicalFile) {
                        // 拷贝成功后才跳过原流程，失败仍交给宿主处理。
                        source.inputStream().use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
                        param.result = null
                    }
                }
                "agree_privacy_policy_dialog" -> scope.skip(option, scope.find(
                    classStrings = listOf("first_install_app_for_privacy", "https://acts.qidian.com/pact/qd_pact.html"),
                    params = listOf("android.app.Activity", "boolean", "java.lang.String", null), returns = "void"))
                "privacy_policy_update_dialog" -> scope.skip(option, scope.methods(main, "checkPrivacyVersion"))
                "check_update" -> {
                    scope.skip(option, scope.methods(main, "checkUpdate"))
                    scope.skip(option, scope.methods("$host.ui.activity.AboutActivity", "getVersionNew"))
                    scope.skip(option, scope.find(classStrings = listOf("SettingUpdateVersionNotifyTime", "0"),
                        params = listOf("android.app.Activity", null, "android.os.Handler", "boolean", "boolean"),
                        returns = "void"))
                    scope.skip(option, scope.find(classStrings = listOf("QDReader", "isPad", "versionCode",
                        "apiVersion", "UpgradeCommon"), params = listOf("android.content.Context"), returns = "void"))
                }
                "auto_jump_daily_recommend" -> {
                    scope.skip(option, scope.methods(main, "checkOpenView", 1))
                    scope.skip(option, scope.methods(main, "checkMainGroupPositionTab", 1))
                    scope.skip(option, scope.methods(main, "getIntentData"))
                }
                "notification_permission_dialog" -> {
                    scope.skip(option, scope.methods(main, "checkPushPermission"))
                    scope.skip(option, scope.clazz("$host.util.NotificationPermissionUtil")?.declaredMethods?.filter {
                        it.parameterTypes.map { type -> type.name } == listOf("android.app.Activity", "java.lang.String",
                            "$host.ui.view.PersonalRecommendPushView", "$host.ui.view.PersonalRecommendPushView\$search")
                    }.orEmpty())
                }
                "environment_check" -> {
                    scope.skip(option, scope.methods("a.b", "c", 1).filter {
                        it.returnType == java.lang.Boolean.TYPE && it.parameterTypes[0].name == "android.content.Context"
                    }, false)
                    scope.skip(option, scope.methods("$host.component.util.FockUtil", "reportDeviceInfo", 2))
                }
                "read_page_watermark" -> scope.skip(option, scope.methods("$host.ui.activity.QDReaderActivity", "setWaterMark"))
                "teenager_mode_dialog" -> scope.skip(option, scope.find(
                    classStrings = listOf("SettingTeenagerModeOpen", "TeenagerMode", "teenagerUnopenedDesc"),
                    params = listOf("$host.ui.activity.BaseActivity"), returns = "void"))
                "teenager_mode_request" -> scope.skip(option, scope.methods("$host.bll.manager.QDTeenagerManager", "init", 1))
                "first_install_analysis" -> scope.skip(option, scope.methods(main, "firstInstallAnalytics"))
                "track_device_info" -> scope.skip(option, scope.methods(main, "trackDeviceInfo"))
                "download_game" -> scope.skip(option, scope.methods(main, "downloadGame"))
                "splash_report" -> {
                    // 当前宿主已将这两个上报方法移到启动 Activity，保留旧版别名回退。
                    val splash = "$host.ui.activity.SplashActivity"
                    val methods = listOf("reportUserRom", "reportUserCurrentTimeZone").flatMap { name ->
                        scope.methods(splash, name, 0).ifEmpty { scope.methods("a.c", name, 0) }
                    }.filter { it.returnType == Void.TYPE }.distinct()
                    scope.skip(option, methods)
                }
            }
        } }
    }
}
