package cn.xihan.qdds.hooks

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.ViewTreeObserver
import de.robv.android.xposed.XSharedPreferences
import java.util.concurrent.atomic.AtomicBoolean

/** 快路径先装明确目标/缓存目标，首帧后再补查；整个启动周期仅一个扫描线程。 */
internal object HookStartup {
    fun isMainProcess(application: Application): Boolean {
        val name = if (Build.VERSION.SDK_INT >= 28) Application.getProcessName() else {
            val manager = application.getSystemService(ActivityManager::class.java)
            manager?.runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }?.processName
        }
        return name == application.packageName
    }

    fun install(application: Application, settings: XSharedPreferences, rewardAd: Boolean) {
        val started = SystemClock.uptimeMillis()
        val scope = OptionHookScope(application, settings)
        try {
            if (rewardAd) RewardAdHook.install(scope)
            AdditionalFeatureHooks.install(scope)
            HookSupport.log("启动快路径", "完成，耗时=${SystemClock.uptimeMillis() - started}ms，" +
                "缓存命中=${scope.cacheHits}，待补查任务=${scope.deferredCount}")
            if (scope.deferredCount == 0) scope.close()
            else scheduleAfterFirstDraw(application, scope)
        } catch (error: Throwable) {
            scope.close()
            throw error
        }
    }

    private fun scheduleAfterFirstDraw(application: Application, scope: OptionHookScope) {
        val started = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (started.get()) return
                val owner = this
                val observer = activity.window.decorView.viewTreeObserver
                observer.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
                    private var observed = false
                    override fun onDraw() {
                        if (observed) return
                        observed = true
                        val launch = started.compareAndSet(false, true)
                        // 不能在 onDraw 遍历监听器时删除监听器，也不能在此执行扫描。
                        handler.post {
                            if (observer.isAlive) observer.removeOnDrawListener(this)
                            if (launch) {
                                application.unregisterActivityLifecycleCallbacks(owner)
                                Thread({
                                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                                    val began = SystemClock.uptimeMillis()
                                    HookSupport.attempt("后台补查") { scope.completeDeferred() }
                                    HookSupport.log("后台补查", "完成，耗时=${SystemClock.uptimeMillis() - began}ms，" +
                                        "Dex查询=${scope.scans}")
                                }, "QD-Hook-Lookup").apply { isDaemon = true }.start()
                            }
                        }
                    }
                })
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        application.registerActivityLifecycleCallbacks(callbacks)
    }
}
