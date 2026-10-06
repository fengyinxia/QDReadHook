package cn.xihan.qdds.hooks

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import cn.xihan.qdds.FeatureOptions
import de.robv.android.xposed.XposedHelpers
import java.io.File
import java.util.concurrent.Executors

internal object ChapterCommentHook {
    private const val IMAGE = "read_chapter_comment_image_long_click_save_original_image"
    private const val IMAGE_DIALOG = "read_chapter_comment_image_long_click_save_original_image_dialog"
    private const val AUDIO = "read_chapter_comment_audio_export_dialog"
    private const val COPY = "read_chapter_comment_comment_long_click_copy"
    private val exporter by lazy { Executors.newSingleThreadExecutor { r ->
        Thread(r, "QD-Audio-Export").apply { isDaemon = true }
    } }

    fun install(scope: OptionHookScope) {
        val selected = scope.selected(FeatureOptions.COMMENTS)
        if (IMAGE in selected) scope.before("章评保存原图", scope.methods(
            "com.qd.ui.component.modules.imagepreivew.QDUIGalleryActivity", "initView"
        )) { XposedHelpers.setObjectField(it.thisObject, "mMoreIconStyle", 1) }
        if (IMAGE_DIALOG in selected || COPY in selected) {
            val bean = "${OptionHookScope.HOST}.repository.entity.chaptercomment.NewParagraphCommentListBean"
            val binders = scope.find(classStrings = listOf("%s楼 · %s"), methodStrings = listOf(" · %s"),
                params = listOf("$bean\$DataListBean", "$bean\$BookInfoBean"), returns = "void")
            scope.after("章评图片及复制", binders) { param ->
                val views = holderViews(param.thisObject)
                if (IMAGE_DIALOG in selected) {
                    val data = param.args[0]
                    val address = runCatching { XposedHelpers.callMethod(data, "getImageDetail") as? String }.getOrNull()
                        ?: OptionHookScope.field(data, "imageDetail") as? String
                    if (!address.isNullOrBlank()) views.filterIsInstance<ImageView>().firstOrNull {
                        runCatching { it.resources.getResourceEntryName(it.id) == "image" }.getOrDefault(false)
                    }?.setOnLongClickListener { view ->
                        AlertDialog.Builder(view.context).setTitle("图片地址").setMessage(address)
                            .setPositiveButton("复制") { _, _ -> copy(view.context, address) }
                            .setNegativeButton("取消", null).show()
                        true
                    }
                }
                if (COPY in selected) {
                    val messageClass = scope.clazz("com.qd.ui.component.widget.textview.MessageTextView")
                    views.filterIsInstance<TextView>().filter { messageClass?.isInstance(it) == true }.forEach { text ->
                        text.setOnLongClickListener {
                            AlertDialog.Builder(text.context).setTitle("复制评论").setMessage(text.text)
                                .setPositiveButton("复制") { _, _ -> copy(text.context, text.text.toString()) }
                                .setNegativeButton("取消", null).show()
                            true
                        }
                    }
                }
            }
        }
        if (AUDIO in selected) {
            val methods = scope.find(classStrings = listOf("temp_audio", "temp", "audio"),
                methodStrings = listOf("temp"), count = 6, returns = "void")
            scope.after("章评音频导出", methods) { param ->
                val holder = param.thisObject
                holderViews(holder).firstOrNull {
                    it.javaClass.name == "com.qd.ui.component.widget.roundwidget.QDUIRoundLinearLayout"
                }?.setOnLongClickListener { view ->
                    val values = HookSupport.fieldsOfType(holder, String::class.java).filterIsInstance<String>()
                    val file = values.firstNotNullOfOrNull { candidate ->
                        runCatching { File(candidate).takeIf { cachedAudioPath(view.context, it) } }.getOrNull()
                    }
                    val url = values.firstOrNull { it.startsWith("https://") || it.startsWith("http://") }
                    if (file == null || !file.isFile) {
                        Toast.makeText(view.context, "音频文件不存在，请先播放或下载", Toast.LENGTH_SHORT).show()
                    } else {
                        val input = EditText(view.context).apply {
                            hint = "文件名（不含扩展名）"
                            setSingleLine(true)
                            setText("QD_audio_${System.currentTimeMillis()}")
                        }
                        val dialog = AlertDialog.Builder(view.context).setTitle("导出音频").setView(input)
                            .setPositiveButton("本地导出") { _, _ ->
                                val name = input.text.toString().trim()
                                if (validName(name)) export(view, file, name)
                                else Toast.makeText(view.context, "文件名不可用", Toast.LENGTH_SHORT).show()
                            }.setNeutralButton("取消", null)
                        if (url != null) dialog.setNegativeButton("复制网络地址") { _, _ -> copy(view.context, url) }
                        dialog.show()
                    }
                    true
                }
            }
        }
    }

    private fun holderViews(holder: Any): List<View> =
        HookSupport.fieldsOfType(holder, View::class.java).filterIsInstance<View>()
            .flatMap(OptionHookScope::views).distinct()

    private fun copy(context: Context, text: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("QD", text))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }

    private fun cachedAudioPath(context: Context, file: File): Boolean {
        if (!file.isAbsolute || !OptionHookScope.safeMediaPath(file)) return false
        val extensions = setOf("mp3", "m4a", "aac", "ogg", "wav", "amr", "opus")
        if (file.extension.lowercase() !in extensions && !file.name.contains("audio", true)) return false
        val path = file.canonicalPath
        val roots = listOfNotNull(context.cacheDir, context.externalCacheDir, context.getExternalFilesDir(null)) +
            listOf(File(context.filesDir, "temp_audio"), File(Environment.getExternalStorageDirectory(), "QDReader"))
        return roots.any { path.startsWith(it.canonicalPath + File.separator) }
    }

    private fun validName(name: String): Boolean = name.isNotBlank() && name.length <= 80 &&
        name != "." && name != ".." && name.none { it in "/\\:*?\"<>|" || Character.isISOControl(it) } &&
        !name.equals("auth.json", true) && !name.equals("trust.json", true) &&
        !Regex("^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])([.].*)?$", RegexOption.IGNORE_CASE).matches(name)

    private fun export(view: View, file: File, stem: String) {
        val context = view.context.applicationContext
        exporter.execute {
            val success = HookSupport.attempt("章评音频导出") {
                check(validName(stem) && cachedAudioPath(context, file) && file.isFile)
                val header = ByteArray(16)
                val count = file.inputStream().use { it.read(header) }
                val prefix = String(header, 0, maxOf(count, 0), Charsets.ISO_8859_1)
                val ext = when {
                    prefix.startsWith("#!AMR") -> "amr"
                    prefix.startsWith("OggS") -> "ogg"
                    prefix.startsWith("RIFF") && prefix.contains("WAVE") -> "wav"
                    prefix.length >= 8 && prefix.substring(4, 8) == "ftyp" -> "m4a"
                    prefix.startsWith("ID3") -> "mp3"
                    count >= 2 && (header[0].toInt() and 255) == 255 && (header[1].toInt() and 224) == 224 ->
                        if ((header[1].toInt() and 246) == 240) "aac" else "mp3"
                    else -> error("Unknown audio format")
                }
                val name = "$stem.$ext"
                if (Build.VERSION.SDK_INT >= 29) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, when (ext) {
                            "m4a" -> "audio/mp4"
                            "mp3" -> "audio/mpeg"
                            else -> "audio/$ext"
                        })
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/QDReader")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = requireNotNull(resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values))
                    try {
                        requireNotNull(resolver.openOutputStream(uri)).use { output ->
                            file.inputStream().use { it.copyTo(output) }
                        }
                        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                    } catch (error: Throwable) {
                        resolver.delete(uri, null, null)
                        throw error
                    }
                } else {
                    val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "QDReader")
                    check(directory.isDirectory || directory.mkdirs())
                    file.copyTo(File(directory, name), overwrite = false)
                }
            }
            view.post { Toast.makeText(view.context,
                if (success) "已导出到 Music/QDReader" else "导出失败，请检查音频格式和存储权限", Toast.LENGTH_LONG).show() }
        }
    }
}
