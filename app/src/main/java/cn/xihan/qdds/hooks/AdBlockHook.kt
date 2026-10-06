package cn.xihan.qdds.hooks

import cn.xihan.qdds.FeatureOptions

internal object AdBlockHook {
    fun install(scope: OptionHookScope) {
        val selected = scope.selected(FeatureOptions.ADS)
        val host = OptionHookScope.HOST
        selected.forEach { option -> HookSupport.attempt("广告/$option") {
            when (option) {
                "splash_ad" -> {
                    scope.skip(option, scope.find(methodStrings = listOf("localLabels="),
                        params = listOf("android.content.Context"), returns = "void"))
                    val keys = listOf("SettingSplashEnableGDT", "SettingSplashGDTShowMaxCountOnDay",
                        "SettingSplashGDTShowBeginTime", "SettingSplashGDTShowEndTime")
                    // 四个闪屏专属配置键共同限定策略类，不匹配通用配置中心。
                    scope.skip(option, scope.find(classStrings = keys, returns = "boolean"), false)
                    scope.skip(option, scope.find(classStrings = keys, returns = "void"))
                }
                "gdt_ad" -> {
                    scope.skip(option, scope.methods("com.qq.e.comm.managers.GDTADManager", "initPlugin"))
                    scope.skip(option, scope.methods("com.qq.e.comm.managers.GDTADManager", "initWith"))
                    val markers = listOf("videolast-ad", "video-redpopup-ad")
                    markers.forEach { marker ->
                        scope.skip(option, scope.find(methodStrings = listOf(marker),
                            classStrings = markers, returns = "boolean"), false)
                    }
                }
                "home_daily_read_ad" -> scope.skip(option,
                    scope.methods("$host.ui.activity.DailyReadingActivity", "getADInfo"))
                "home_book_shelf_top_ad" -> scope.skip(option,
                    scope.methods("$host.ui.activity.MainGroupActivity", "doBKTAction"))
                "home_book_shelf_activity_ad" -> scope.skip(option,
                    scope.methods("$host.ui.modules.bookshelf.BookShelfOperationManager", "getBookShelfOperationRes"))
                "home_book_shelf_float_window_ad" -> {
                    val current = scope.methods("$host.ui.modules.bookshelf.QDBookShelfRebornFragment", "updateFloatingAd", 0)
                    val methods = current.ifEmpty {
                        scope.methods("$host.ui.fragment.main_group.QDBookShelfRebornFragment", "updateFloatingAd", 0)
                    }
                    scope.skip(option, methods.filter { it.returnType == Void.TYPE })
                }
                "me_middle_ad" -> scope.skip(option,
                    scope.methods("$host.ui.fragment.main_group.QDUserAccountRebornFragment", "loadADData"))
                "read_float_window_ad" -> {
                    // 当前宿主用 adv 承载浮窗广告；保留相册、角色、订阅等其它菜单数据。
                    scope.skip(option, scope.methods("$host.repository.entity.ReadMenuData", "getAdv", 0))
                }
                "read_reward_theater_ad" -> {
                    val legacy = scope.methods("$host.ui.activity.chapter.list.NewParagraphCommentListActivity", "getParagraphTip", 0)
                        .filter { it.returnType == Void.TYPE }
                    if (legacy.isNotEmpty()) {
                        scope.skip(option, legacy)
                    } else {
                        // 7.9.428 在章节刷新后发布提示事件，只拦截这一事件，不跳过刷新或支付入口。
                        val publishers = scope.methods("$host.readerengine.view.QDSuperEngineView", "postEvent", 3)
                            .filter { method ->
                                method.returnType == Void.TYPE && method.parameterTypes.map { it.name } ==
                                    listOf("java.lang.String", "long", "[Ljava.lang.Object;")
                            }
                        scope.before(option, publishers) { param ->
                            if (param.args[0] == "EVENT_PARAGRAPH_TIP") {
                                param.result = null
                                HookSupport.log(option, "已拦截小剧场提示事件")
                            }
                        }
                    }
                }
                "read_last_page_middle_ad" -> {
                    val legacy = scope.methods("$host.ui.activity.BookLastPageNewActivity", "updateADView", 2)
                        .filter { it.returnType == Void.TYPE }
                    val methods = legacy.ifEmpty {
                        // 混淆方法 q：末页专属广告位 + 完整绑定签名，保留书籍信息/推荐等其它头部内容。
                        scope.find(methodStrings = listOf("newlastpage"),
                            params = listOf("$host.databinding.ItemLastPageHeadBinding", "$host.repository.entity.BookLastPage"),
                            returns = "void").filter {
                                it.declaringClass.name == "$host.ui.viewholder.lastpage.HeadViewHolder"
                            }
                    }
                    scope.before(option, methods) { param ->
                        if (param.method.declaringClass.name == "$host.ui.viewholder.lastpage.HeadViewHolder") {
                            (OptionHookScope.field(param.args[0], "judian") as? android.view.View)?.visibility = android.view.View.GONE
                        }
                        param.result = null
                        HookSupport.log(option, "已拦截末页中间广告加载")
                    }
                }
                "read_last_page_dialog_ad" -> scope.skip(option,
                    scope.clazz("$host.bll.manager.QDBKTManager")?.declaredMethods?.filter {
                        it.parameterTypes.size == 5 &&
                            (it.returnType == Void.TYPE || it.returnType.name == "kotlin.Unit")
                    }.orEmpty())
            }
        } }
    }
}
