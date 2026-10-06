package cn.xihan.qdds

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.R as MaterialR

/** 只负责界面样式，不读取或更改宿主配置。颜色从当前 MD3 主题获取，支持动态取色。 */
internal class SettingsViews(private val context: Context) {
    fun dp(value: Int): Int = (value * context.resources.displayMetrics.density + 0.5f).toInt()

    fun color(attribute: Int): Int = MaterialColors.getColor(context, attribute, "QDSettings")

    fun column(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    fun label(text: CharSequence, appearance: Int = MaterialR.style.TextAppearance_Material3_BodyLarge): TextView =
        TextView(context).apply {
            this.text = text
            setTextAppearance(appearance)
            setTextColor(color(MaterialR.attr.colorOnSurface))
        }

    fun heading(text: CharSequence): TextView = label(text, MaterialR.style.TextAppearance_Material3_TitleSmall).apply {
        setTextColor(color(MaterialR.attr.colorPrimary))
        setPadding(dp(8), dp(24), dp(8), dp(12))
        ViewCompat.setAccessibilityHeading(this, true)
    }

    fun card(content: View): MaterialCardView = MaterialCardView(context).apply {
        radius = dp(20).toFloat()
        cardElevation = 0f
        strokeWidth = 0
        setCardBackgroundColor(MaterialColors.layer(
            color(MaterialR.attr.colorSurface), color(MaterialR.attr.colorPrimary), 0.05f
        ))
        preventCornerOverlap = false
        useCompatPadding = false
        clipToOutline = true
        addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun ripple(view: View) {
        val attributes = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        try {
            view.background = attributes.getDrawable(0)
        } finally {
            attributes.recycle()
        }
    }

    fun switchRow(title: CharSequence, checked: Boolean, changed: (Boolean) -> Unit): MaterialSwitch =
        MaterialSwitch(context).apply {
            text = title
            isChecked = checked
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(64)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyLarge)
            setTextColor(color(MaterialR.attr.colorOnSurface))
            setOnCheckedChangeListener { _, value -> changed(value) }
        }

    fun navigationRow(title: CharSequence, value: CharSequence? = null, clicked: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            val texts = column()
            texts.addView(label(title))
            if (!value.isNullOrEmpty()) {
                texts.addView(label(value, MaterialR.style.TextAppearance_Material3_BodyMedium).apply {
                    setTextColor(color(MaterialR.attr.colorOnSurfaceVariant))
                    setPadding(0, dp(4), 0, 0)
                })
            }
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(label("›", MaterialR.style.TextAppearance_Material3_TitleLarge).apply {
                setTextColor(color(MaterialR.attr.colorOnSurfaceVariant))
                setPadding(dp(16), 0, 0, 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            // 整行是一个无障碍目标，包名值随标题一起读出。
            contentDescription = listOfNotNull(title, value).joinToString("，")
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            texts.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            isFocusable = true
            ripple(this)
            setOnClickListener { clicked() }
        }
}
