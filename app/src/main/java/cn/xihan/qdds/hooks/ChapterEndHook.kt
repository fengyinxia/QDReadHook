package cn.xihan.qdds.hooks

import cn.xihan.qdds.FeatureOptions

internal object ChapterEndHook {
    fun install(scope: OptionHookScope) {
        if (!scope.enabled(FeatureOptions.END_ENABLED)) return
        val hidden = scope.selected(FeatureOptions.END_OPTIONS)
        if (hidden.isEmpty()) return
        val prefix = "${OptionHookScope.HOST}.readerengine.specialline.module."
        val mapping = mapOf(
            "QDAuthorCommentModule" to "read_chapter_end_author_say",
            "QDChapterCommentModule" to "read_chapter_end_book_say",
            "c" to "read_chapter_end_reward_video",
            "search" to "read_chapter_end_fantastic_update",
            "judian" to "read_chapter_end_new_user_dialog",
            "a" to "read_chapter_end_bottom_recommend",
            "b" to "read_chapter_end_bottom_recommend",
            "d" to "read_chapter_end_bottom_recommend",
            "cihai" to "read_chapter_end_bottom_recommend"
        )
        scope.after("章末选项", scope.methods(
            "${OptionHookScope.HOST}.readerengine.specialline.QDChapterEndDivideProcessing",
            "buildChapterEndData", 1
        ).filter { java.util.List::class.java.isAssignableFrom(it.returnType) }) { param ->
            val original = param.result as? List<*> ?: return@after
            val filtered = original.filterNot { item ->
                val name = item?.javaClass?.name ?: return@filterNot false
                name.startsWith(prefix) && mapping[name.removePrefix(prefix)] in hidden
            }
            if (filtered.size != original.size) {
                param.result = ArrayList(filtered)
                HookSupport.log("章末选项", "本次隐藏 ${original.size - filtered.size} 个模块")
            }
        }
    }
}
