package cn.xihan.qdds

import android.os.Bundle
import android.os.Parcelable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.preference.MultiSelectListPreference
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.R as MaterialR

/** 多选子页只编辑副本。搜索、目录刷新、返回和旋转都不会直接写入隐藏名单。 */
internal class OptionEditor(
    private val activity: MainActivity,
    val preference: MultiSelectListPreference,
    val master: SwitchPreferenceCompat?,
    restored: Bundle? = null
) {
    private val ui = SettingsViews(activity)
    private val initialSelection = preference.values.toSet()
    private val initialEnabled = master?.isChecked ?: true
    val selected = (restored?.getStringArrayList("selection")?.toSet() ?: initialSelection).toMutableSet()
    var enabled = restored?.getBoolean("enabled", initialEnabled) ?: initialEnabled
        private set
    private var query = restored?.getString("query").orEmpty()
    private val catalogKey = when (preference.key) {
        MeTabOptions.HIDDEN_KEY -> MeTabOptions.CATALOG_KEY
        FeatureOptions.NAV_HIDDEN -> FeatureOptions.NAV_CATALOG
        else -> null
    }
    private val list = RecyclerView(activity).apply {
        layoutManager = LinearLayoutManager(activity)
        clipToPadding = false
        setPadding(0, 0, 0, ui.dp(16))
        itemAnimator = null
    }
    private val rows = mutableListOf<Row>()
    private val adapter = OptionsAdapter()
    val view: LinearLayout = ui.column()

    val hasChanges: Boolean
        get() = selected != initialSelection || (master != null && enabled != initialEnabled)

    init {
        view.setPadding(ui.dp(16), 0, ui.dp(16), 0)
        master?.let { toggle ->
            view.addView(ui.card(ui.switchRow(toggle.title ?: "", enabled) { value ->
                enabled = value
                adapter.notifyDataSetChanged()
            }), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = ui.dp(8)
            })
        }
        val instruction = when (preference.key) {
            FeatureOptions.COMMENTS -> R.string.selection_enabled
            FeatureOptions.ADS, FeatureOptions.INTERCEPT -> R.string.selection_intercepted
            else -> R.string.selection_hidden
        }
        view.addView(ui.label(activity.getString(instruction), MaterialR.style.TextAppearance_Material3_BodyMedium).apply {
            setTextColor(ui.color(MaterialR.attr.colorOnSurfaceVariant))
            setPadding(ui.dp(8), ui.dp(16), ui.dp(8), ui.dp(12))
        })
        if (catalogKey != null || preference.entries.orEmpty().size > 8) {
            val input = TextInputEditText(activity).apply {
                setSingleLine(true)
                setText(query)
                inputType = android.text.InputType.TYPE_CLASS_TEXT
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            }
            val search = TextInputLayout(activity, null, MaterialR.attr.textInputOutlinedStyle).apply {
                hint = activity.getString(R.string.search_options_hint)
                endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
                // TextInputLayout 会直接复用参数给内部 LinearLayout，不能传通用 LayoutParams。
                addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            view.addView(search)
            input.doAfterTextChanged {
                query = it?.toString().orEmpty()
                refreshRows()
            }
        }
        list.adapter = adapter
        view.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        view.addView(LinearLayout(activity).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, ui.dp(8), 0, ui.dp(8))
            addView(MaterialButton(activity, null, MaterialR.attr.borderlessButtonStyle).apply {
                setText(R.string.action_cancel)
                setOnClickListener { activity.leaveEditor() }
            })
            addView(MaterialButton(activity).apply {
                setText(R.string.action_save)
                setOnClickListener { activity.saveEditor() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = ui.dp(8)
            })
        })
        refreshRows()
        @Suppress("DEPRECATION")
        val position = restored?.getParcelable<Parcelable>("list_position")
        list.layoutManager?.onRestoreInstanceState(position)
    }

    fun saveState(): Bundle = Bundle().apply {
        putStringArrayList("selection", ArrayList(selected))
        putBoolean("enabled", enabled)
        putString("query", query)
        putParcelable("list_position", list.layoutManager?.onSaveInstanceState())
    }

    fun refreshCatalog() {
        val position = list.layoutManager?.onSaveInstanceState()
        refreshRows()
        list.layoutManager?.onRestoreInstanceState(position)
    }

    private fun refreshRows() {
        val catalog = catalogKey?.let { activity.settings.getStringSet(it, emptySet()).orEmpty().toSet() }
        val choices = if (catalog != null) {
            catalog.sorted().map { Choice(it, it, R.string.section_options) } +
                (selected - catalog).sorted().map { Choice(it, it, R.string.section_saved) }
        } else {
            preference.entryValues.orEmpty().zip(preference.entries.orEmpty()).map { (value, title) ->
                Choice(value.toString(), displayTitle(title.toString()), groupFor(value.toString()))
            } + (selected - preference.entryValues.orEmpty().map { it.toString() }.toSet()).sorted().map {
                // 未知旧值保留并提供显式取消入口，不在保存时悄悄丢弃。
                Choice(it, it, R.string.section_saved)
            }
        }
        rows.clear()
        if (catalog != null && catalog.isEmpty()) rows += Row.CatalogEmpty
        val filtered = choices.filter { it.title.contains(query.trim(), ignoreCase = true) }
        for ((group, options) in filtered.groupBy { it.group }) {
            rows += Row.Heading(activity.getString(group))
            rows += options.map { Row.Option(it) }
        }
        if (filtered.isEmpty() && (query.isNotBlank() || catalog == null || choices.isNotEmpty())) {
            rows += Row.NoResults
        }
        adapter.notifyDataSetChanged()
    }

    private fun displayTitle(title: String): String = when (preference.key) {
        FeatureOptions.COMMENTS -> title.removePrefix("阅读页-章评")
        FeatureOptions.END_OPTIONS -> title.removePrefix("阅读页-章末")
        FeatureOptions.ADS -> title.removePrefix("主页-").removePrefix("我-").removePrefix("阅读页-")
        else -> title
    }

    private fun groupFor(value: String): Int = when (preference.key) {
        FeatureOptions.ADS -> when {
            value.startsWith("home_") -> R.string.section_home
            value.startsWith("me_") -> R.string.section_me
            value.startsWith("read_") -> R.string.section_reader
            else -> R.string.section_general
        }
        FeatureOptions.INTERCEPT -> when (value) {
            "agree_privacy_policy_dialog", "privacy_policy_update_dialog", "notification_permission_dialog",
            "teenager_mode_dialog" -> R.string.section_dialogs
            "environment_check", "first_install_analysis", "track_device_info", "splash_report" -> R.string.section_device
            else -> R.string.section_behavior
        }
        else -> R.string.section_options
    }

    private data class Choice(val value: String, val title: String, val group: Int)
    private sealed class Row {
        data class Heading(val title: String) : Row()
        data class Option(val choice: Choice) : Row()
        object CatalogEmpty : Row()
        object NoResults : Row()
    }

    private class Holder(view: View) : RecyclerView.ViewHolder(view)

    private inner class OptionsAdapter : RecyclerView.Adapter<Holder>() {
        override fun getItemCount(): Int = rows.size
        override fun getItemViewType(position: Int): Int = when (rows[position]) {
            is Row.Heading -> 0
            is Row.Option -> 1
            Row.CatalogEmpty -> 2
            Row.NoResults -> 3
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val content = when (viewType) {
                0 -> ui.heading("")
                1 -> MaterialCheckBox(activity).apply {
                    minHeight = ui.dp(56)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(ui.dp(8), ui.dp(12), ui.dp(8), ui.dp(12))
                    setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyLarge)
                    ui.ripple(this)
                }
                2 -> emptyCatalog()
                else -> ui.label(activity.getString(R.string.empty_search)).apply {
                    setPadding(ui.dp(8), ui.dp(24), ui.dp(8), ui.dp(24))
                    setTextColor(ui.color(MaterialR.attr.colorOnSurfaceVariant))
                }
            }
            content.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            return Holder(content)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            when (val row = rows[position]) {
                is Row.Heading -> (holder.itemView as TextView).text = row.title
                is Row.Option -> (holder.itemView as MaterialCheckBox).apply {
                    text = row.choice.title
                    isChecked = row.choice.value in selected
                    isEnabled = enabled
                    setOnClickListener {
                        if (isChecked && row.choice.value == "gdt_ad") {
                            // 确认前恢复复选框，取消、旋转或返回不会留下临时勾选。
                            isChecked = false
                            MaterialAlertDialogBuilder(activity)
                                .setTitle(R.string.gdt_warning_title)
                                .setMessage(R.string.gdt_warning_message)
                                .setNegativeButton(R.string.action_cancel, null)
                                .setPositiveButton(R.string.action_confirm) { _, _ ->
                                    selected += row.choice.value
                                    adapter.notifyDataSetChanged()
                                }.show()
                        } else if (isChecked) {
                            selected += row.choice.value
                        } else {
                            selected -= row.choice.value
                        }
                    }
                }
                else -> Unit
            }
        }

        private fun emptyCatalog(): View = ui.card(ui.column().apply {
            setPadding(ui.dp(16), ui.dp(20), ui.dp(16), ui.dp(12))
            addView(ui.label(activity.getString(R.string.empty_catalog_title), MaterialR.style.TextAppearance_Material3_TitleMedium))
            val guidance = if (preference.key == MeTabOptions.HIDDEN_KEY) R.string.empty_me_catalog else R.string.empty_navigation_catalog
            addView(ui.label(activity.getString(guidance), MaterialR.style.TextAppearance_Material3_BodyMedium).apply {
                setPadding(0, ui.dp(8), 0, ui.dp(12))
                setTextColor(ui.color(MaterialR.attr.colorOnSurfaceVariant))
            })
            addView(MaterialButton(activity, null, MaterialR.attr.borderlessButtonStyle).apply {
                setText(R.string.action_refresh)
                setOnClickListener {
                    refreshCatalog()
                    activity.showMessage(R.string.catalog_refreshed)
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(ui.label(activity.getString(R.string.catalog_help), MaterialR.style.TextAppearance_Material3_BodySmall).apply {
                setTextColor(ui.color(MaterialR.attr.colorOnSurfaceVariant))
            })
        })
    }
}
