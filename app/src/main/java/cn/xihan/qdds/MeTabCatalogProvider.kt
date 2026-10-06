package cn.xihan.qdds

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/** 宿主只能上报菜单目录，不能通过此接口读取配置或更改隐藏项目。 */
class MeTabCatalogProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    @Suppress("DEPRECATION")
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val catalogKey = when (method) {
            MeTabOptions.REPORT_METHOD -> MeTabOptions.CATALOG_KEY
            FeatureOptions.REPORT_NAV -> FeatureOptions.NAV_CATALOG
            else -> throw UnsupportedOperationException()
        }
        val ctx = requireNotNull(context)
        val prefs = ctx.getSharedPreferences(
            "${BuildConfig.APPLICATION_ID}_preferences", Context.MODE_WORLD_READABLE
        )
        val target = prefs.getString("packageName", "com.qidian.QDReader")
            .orEmpty().ifBlank { "com.qidian.QDReader" }
        // callingPackage 由 Android 验证归属，不信任 Bundle 中声称的包名。
        if (callingPackage != target) throw SecurityException("Unexpected catalog caller")

        val supplied = extras?.getStringArrayList(MeTabOptions.ITEMS_KEY).orEmpty()
        require(supplied.size <= MeTabOptions.MAX_ITEMS)
        val names = supplied.map(String::trim).filter(MeTabOptions::validName).toSet()
        val previous = prefs.getStringSet(catalogKey, emptySet()).orEmpty().toSet()
        val merged = (previous + names).sorted().take(MeTabOptions.MAX_ITEMS).toSet()
        if (merged != previous) {
            check(prefs.edit().putStringSet(catalogKey, merged).commit())
        }
        return Bundle().apply { putBoolean("ok", true) }
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? = throw UnsupportedOperationException()

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
    ): Int = throw UnsupportedOperationException()
}
