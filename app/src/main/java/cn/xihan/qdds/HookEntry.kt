package cn.xihan.qdds

import android.app.Application
import android.app.Instrumentation
import cn.xihan.qdds.hooks.AdditionalFeatureHooks
import cn.xihan.qdds.hooks.AutoSignHook
import cn.xihan.qdds.hooks.HookSupport
import cn.xihan.qdds.hooks.MeTabHook
import cn.xihan.qdds.hooks.RewardAdHook
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @项目名 : BaseHook
 * @作者 : MissYang
 * @创建时间 : 2022/7/4 16:32
 * @介绍 : 起点签到、奖励与界面选项定制
 */
@InjectYukiHookWithXposed(modulePackageName = "cn.xihan.qdds", entryClassName = "HookEntryInit")
class HookEntry : IYukiHookXposedInit {

    override fun onInit() {
        YukiHookAPI.configs {
            debugTag = "yuki"
            isDebug = BuildConfig.DEBUG
        }
    }

    override fun onHook() = YukiHookAPI.encase {
        loadApp(name = prefs.getString("packageName").ifBlank { "com.qidian.QDReader" }) {
            // 与新版模块一致：此时 Application 已 attach，宿主 ClassLoader 已就绪。
            XposedHelpers.findAndHookMethod(
                Instrumentation::class.java, "callApplicationOnCreate", Application::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val application = param.args[0] as? Application ?: return
                        if (!hookInstallationStarted.compareAndSet(false, true)) return
                        HookSupport.attempt("初始化") {
                            val settings = XSharedPreferences(
                                BuildConfig.APPLICATION_ID,
                                "${BuildConfig.APPLICATION_ID}_preferences"
                            ).apply { reload() }
                            val autoSign = settings.getBoolean("isEnableAutoSign", false)
                            val rewardAd = settings.getBoolean("isEnableRewardWithoutAd", false)
                            val versionCode = application.packageManager.getPackageInfo(
                                application.packageName, 0
                            ).versionCode
                            HookSupport.log(
                                "初始化", "宿主版本=$versionCode，自动签到=$autoSign，免广告奖励=$rewardAd"
                            )
                            if (autoSign) AutoSignHook.install(application.classLoader)
                            if (rewardAd) RewardAdHook.install(application)
                            MeTabHook.install(application, settings)
                            AdditionalFeatureHooks.install(application, settings)
                        }
                    }
                }
            )
        }
    }

    companion object {
        private val hookInstallationStarted = AtomicBoolean(false)
    }
}
