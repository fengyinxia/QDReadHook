package cn.xihan.qdds

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.EditTextPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.R as MaterialR

/** 独立 MD3 设置入口。XML 仍是偏好键、默认值和静态选项的唯一来源。 */
class MainActivity : AppCompatActivity() {
    internal lateinit var settings: SharedPreferences
        private set
    private lateinit var preferences: PreferenceScreen
    private lateinit var ui: SettingsViews
    private lateinit var toolbar: MaterialToolbar
    private lateinit var content: FrameLayout
    private lateinit var root: LinearLayout
    private var editor: OptionEditor? = null
    private var home: ScrollView? = null
    private var homePosition = 0
    private var sharingAvailable = true
    private val catalogListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == MeTabOptions.CATALOG_KEY || key == FeatureOptions.NAV_CATALOG) {
            runOnUiThread { editor?.refreshCatalog() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        ui = SettingsViews(this)
        root = findViewById(R.id.settings_root)
        toolbar = findViewById(R.id.settings_toolbar)
        content = findViewById(R.id.settings)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)
        loadPreferences()
        toolbar.menu.add(R.string.action_help).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        toolbar.setOnMenuItemClickListener {
            showHelp()
            true
        }
        if (!sharingAvailable) {
            root.addView(ui.card(ui.navigationRow(getString(R.string.sharing_unavailable)) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.sharing_unavailable)
                    .setMessage(R.string.sharing_help)
                    .setPositiveButton(R.string.action_understood, null).show()
            }), 1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8))
            })
        }
        homePosition = savedInstanceState?.getInt("home_position") ?: 0
        val option = savedInstanceState?.getString("editor_key")?.let {
            preferences.findPreference<MultiSelectListPreference>(it)
        }
        if (option != null) showEditor(option, savedInstanceState?.getBundle("editor_state")) else showHome()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (editor != null) leaveEditor() else finish()
            }
        })
    }

    @Suppress("DEPRECATION", "RestrictedApi")
    private fun loadPreferences() {
        val manager = PreferenceManager(this)
        // 与原设置页一致：LSPosed 的 xposedsharedprefs 依赖 WORLD_READABLE。
        manager.sharedPreferencesName = "${BuildConfig.APPLICATION_ID}_preferences"
        try {
            manager.sharedPreferencesMode = Context.MODE_WORLD_READABLE
            settings = requireNotNull(manager.sharedPreferences)
        } catch (_: SecurityException) {
            manager.sharedPreferencesMode = Context.MODE_PRIVATE
            settings = requireNotNull(manager.sharedPreferences)
            sharingAvailable = false
        }
        preferences = manager.inflateFromResource(this, R.xml.root_preferences, null)
    }

    override fun onStart() {
        super.onStart()
        settings.registerOnSharedPreferenceChangeListener(catalogListener)
        editor?.refreshCatalog()
    }

    override fun onStop() {
        settings.unregisterOnSharedPreferenceChangeListener(catalogListener)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("home_position", home?.scrollY ?: homePosition)
        editor?.let {
            outState.putString("editor_key", it.preference.key)
            outState.putBundle("editor_state", it.saveState())
        }
        super.onSaveInstanceState(outState)
    }

    private fun showHome() {
        hideKeyboard()
        editor = null
        toolbar.setTitle(R.string.settings_title)
        toolbar.navigationIcon = null
        toolbar.setNavigationOnClickListener(null)
        val groups = ui.column().apply {
            setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(24))
        }
        for (index in 0 until preferences.preferenceCount) {
            val group = preferences.getPreference(index) as PreferenceGroup
            groups.addView(ui.heading(group.title ?: ""))
            val rows = ui.column()
            for (position in 0 until group.preferenceCount) {
                val preference = group.getPreference(position)
                val row = when (preference) {
                    is SwitchPreferenceCompat -> ui.switchRow(preference.title ?: "", preference.isChecked) { checked ->
                        preference.isChecked = checked
                        showSavedMessage()
                    }
                    is EditTextPreference -> ui.navigationRow(preference.title ?: "", preference.text) {
                        showPackageDialog(preference)
                    }
                    else -> ui.navigationRow(preference.title ?: "") { openPreference(preference) }
                }
                rows.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            groups.addView(ui.card(rows))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(groups)
        }
        content.removeAllViews()
        content.addView(scroll)
        home = scroll
        scroll.post { scroll.scrollTo(0, homePosition) }
    }

    private fun openPreference(preference: Preference) {
        val option = when (preference) {
            is MultiSelectListPreference -> preference
            is PreferenceScreen -> (0 until preference.preferenceCount)
                .map { preference.getPreference(it) }.filterIsInstance<MultiSelectListPreference>().firstOrNull()
            else -> null
        } ?: return
        homePosition = home?.scrollY ?: homePosition
        showEditor(option)
    }

    private fun showEditor(option: MultiSelectListPreference, restored: Bundle? = null) {
        val screen = option.parent as? PreferenceScreen
        val master = screen?.let { parent ->
            (0 until parent.preferenceCount).map { parent.getPreference(it) }
                .filterIsInstance<SwitchPreferenceCompat>().firstOrNull()
        }
        toolbar.title = screen?.title ?: option.title
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.navigationContentDescription = getString(R.string.action_back)
        toolbar.setNavigationOnClickListener { leaveEditor() }
        home = null
        editor = OptionEditor(this, option, master, restored)
        content.removeAllViews()
        content.addView(requireNotNull(editor).view)
    }

    internal fun leaveEditor() {
        if (editor?.hasChanges == true) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.unsaved_title)
                .setMessage(R.string.unsaved_message)
                .setNegativeButton(R.string.action_continue, null)
                .setPositiveButton(R.string.action_discard) { _, _ -> showHome() }.show()
        } else {
            showHome()
        }
    }

    internal fun saveEditor() {
        val page = editor ?: return
        // 同一批编辑写入名单与总开关；不写目录、不清除未知旧值。
        settings.edit().apply {
            putStringSet(page.preference.key, page.selected.toSet())
            page.master?.let { putBoolean(it.key, page.enabled) }
        }.apply()
        // 同步 Preference 对象缓存；这些值已写入同一存储，不产生配置迁移。
        page.preference.values = page.selected.toSet()
        page.master?.isChecked = page.enabled
        showHome()
        showSavedMessage()
    }

    internal fun showMessage(message: Int) {
        Snackbar.make(root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showSavedMessage() {
        showMessage(if (sharingAvailable) R.string.saved_restart else R.string.saved_local)
    }

    private fun showHelp() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.help_title)
            .setMessage(R.string.help_message)
            .setPositiveButton(R.string.action_understood, null).show()
    }

    private fun showPackageDialog(preference: EditTextPreference) {
        val input = TextInputEditText(this).apply {
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(preference.text)
        }
        val field = TextInputLayout(this, null, MaterialR.attr.textInputOutlinedStyle).apply {
            hint = getString(R.string.package_hint)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val container = ui.column().apply {
            setPadding(ui.dp(24), ui.dp(8), ui.dp(24), 0)
            addView(field)
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(preference.title)
            .setView(container)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save, null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text?.toString().orEmpty().trim()
                if (!value.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))) {
                    field.error = getString(R.string.package_error)
                } else {
                    preference.text = value
                    homePosition = home?.scrollY ?: homePosition
                    dialog.dismiss()
                    showHome()
                    showSavedMessage()
                }
            }
        }
        dialog.show()
    }

    private fun hideKeyboard() {
        WindowCompat.getInsetsController(window, root).hide(WindowInsetsCompat.Type.ime())
        currentFocus?.clearFocus()
    }
}
