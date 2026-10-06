package cn.xihan.qdds

import android.net.Uri

/** 独立设置页与宿主仅交换菜单标题，不交换账户信息。 */
internal object MeTabOptions {
    const val ENABLED_KEY = "isEnableCustomizeMeTab"
    const val HIDDEN_KEY = "hiddenMeTabItems"
    const val CATALOG_KEY = "meTabAvailableItems"
    const val REPORT_METHOD = "reportMeTabItems"
    const val ITEMS_KEY = "items"
    const val MAX_ITEMS = 200
    const val MAX_NAME_LENGTH = 80

    val catalogUri: Uri = Uri.parse("content://${BuildConfig.APPLICATION_ID}.me_tab_catalog")

    fun validName(name: String): Boolean = name.isNotBlank() &&
        name.length <= MAX_NAME_LENGTH && name.none { Character.isISOControl(it) }
}
