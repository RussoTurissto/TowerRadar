package com.russoturissto.towerradar

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.min

/**
 * One consistent Khaki Modern dialog for every app menu.
 * System permission dialogs remain owned by Android.
 */
class KhakiDialogBuilder(private val activity: Activity, private val palette: KhakiPalette) {
    private var heading = ""
    private var description: String? = null
    private var choices: Array<String>? = null
    private var chosen = -1
    private var choiceCallback: ((DialogInterface, Int) -> Unit)? = null
    private var embedded: View? = null
    private var maxEmbeddedHeightDp: Int? = null
    private var positive: Pair<String, ((DialogInterface, Int) -> Unit)?>? = null
    private var negative: Pair<String, ((DialogInterface, Int) -> Unit)?>? = null
    private var neutral: Pair<String, ((DialogInterface, Int) -> Unit)?>? = null

    fun setTitle(value: CharSequence): KhakiDialogBuilder = apply { heading = value.toString() }
    fun setMessage(value: CharSequence): KhakiDialogBuilder = apply { description = value.toString() }
    fun setView(view: View): KhakiDialogBuilder = apply { embedded = view }
    fun setMaxContentHeightDp(dp: Int): KhakiDialogBuilder = apply {
        maxEmbeddedHeightDp = dp.coerceIn(140, 660)
    }
    fun setItems(values: Array<String>, action: (DialogInterface, Int) -> Unit): KhakiDialogBuilder =
        apply { choices = values; chosen = -1; choiceCallback = action }
    fun setSingleChoiceItems(values: Array<String>, selected: Int,
        action: (DialogInterface, Int) -> Unit): KhakiDialogBuilder =
        apply { choices = values; chosen = selected; choiceCallback = action }
    fun setPositiveButton(value: String, action: ((DialogInterface, Int) -> Unit)?): KhakiDialogBuilder =
        apply { positive = value to action }
    fun setNegativeButton(value: String, action: ((DialogInterface, Int) -> Unit)?): KhakiDialogBuilder =
        apply { negative = value to action }
    fun setNeutralButton(value: String, action: ((DialogInterface, Int) -> Unit)?): KhakiDialogBuilder =
        apply { neutral = value to action }

    private fun dp(v: Int): Int = (activity.resources.displayMetrics.density * v + .5f).toInt()
    private fun shape(color: Int, corner: Int = 14) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(corner).toFloat()
    }
    private fun label(text: String, sp: Float, color: Int): TextView = TextView(activity).apply {
        this.text = text
        textSize = sp
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = true
    }

    private fun iconFor(text: String): String = when {
        text.contains("Тема") || text.contains("оформлен") -> "◐"
        text.contains("GPS") || text.contains("геопозици", true) ||
            text.contains("координат", true) || text.contains("карте") -> "⌖"
        text.contains("SIM") || text.contains("сеть", true) -> "▥"
        text.contains("Яндекс", true) || text.contains("2ГИС") -> "▣"
        text.contains("соты", true) || text.contains("OpenCellID") -> "◉"
        text.contains("Сектор") || text.contains("Компас", true) -> "➤"
        text.contains("Отмена") -> "×"
        else -> "›"
    }

    fun show(): Dialog {
        val dialog = Dialog(activity)
        val outer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(17), dp(17), dp(12))
            background = shape(palette.surface, 24)
        }

        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = label(heading, 19f, palette.text).apply {
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), 0, dp(6), 0)
        }
        top.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val close = label("×", 29f, palette.secondary).apply {
            gravity = Gravity.CENTER
            contentDescription = "Закрыть"
            setOnClickListener { dialog.dismiss() }
        }
        top.addView(close, LinearLayout.LayoutParams(dp(38), dp(38)))
        outer.addView(top)
        val divider = View(activity).apply { setBackgroundColor(palette.divider) }
        outer.addView(divider, LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(8)
            bottomMargin = dp(8)
        })

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        description?.let { message ->
            content.addView(label(message, 14f, palette.secondary).apply {
                setPadding(dp(6), dp(4), dp(6), dp(12))
                gravity = Gravity.START
            })
        }
        choices?.forEachIndexed { index, value ->
            val line = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = shape(palette.elevated, 12)
                setPadding(dp(10), dp(5), dp(9), dp(5))
                isClickable = true
                isFocusable = true
                contentDescription = value
            }
            val mark = label(iconFor(value), 22f, palette.olive).apply {
                gravity = Gravity.CENTER
            }
            line.addView(mark, LinearLayout.LayoutParams(dp(32), dp(46)))
            val before = value.substringBefore(": ")
            val after = value.substringAfter(": ", "")
            val texts = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(label(before, 14f, palette.text).apply {
                setTypeface(null, Typeface.BOLD)
                maxLines = 2
            })
            if (after.isNotEmpty()) {
                texts.addView(label(after, 12f, palette.secondary))
            }
            line.addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(5)
            })
            line.addView(label(if (chosen == index) "✓" else "›", 19f, palette.olive).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(28), dp(35)))
            line.setOnClickListener {
                dialog.dismiss()
                choiceCallback?.invoke(dialog, index)
            }
            content.addView(line, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(6)
            })
        }
        embedded?.let { extra ->
            (extra.parent as? ViewGroup)?.removeView(extra)
            if (extra is EditText) {
                extra.setTextColor(palette.text)
                extra.setHintTextColor(palette.secondary)
                extra.background = shape(palette.elevated, 12)
                extra.setPadding(dp(12), dp(13), dp(12), dp(13))
            }
            content.addView(extra, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(6)
                bottomMargin = dp(8)
            })
        }

        val scroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = true
            addView(content, FrameLayout.LayoutParams(-1, -2))
        }
        val displayHeight = activity.resources.displayMetrics.heightPixels
        val estimate = dp(58) * (choices?.size ?: 0) +
            (description?.length ?: 0) / 45 * dp(24) +
            if (embedded != null) dp(95) else 0
        val maximumHeight = (displayHeight * 0.66f).toInt()
        outer.addView(scroll, LinearLayout.LayoutParams(-1,
            if (maxEmbeddedHeightDp != null) min(maximumHeight, dp(maxEmbeddedHeightDp!!))
            else if (estimate > maximumHeight) maximumHeight else -2))

        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        fun addAction(action: Pair<String, ((DialogInterface, Int) -> Unit)?>?,
            which: Int, isPrimary: Boolean) {
            if (action == null) return
            val button = label(action.first, 13f,
                if (isPrimary) palette.oliveText else palette.olive).apply {
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(12), 0, dp(12), 0)
                background = shape(if (isPrimary) palette.olive else palette.accentSoft, 11)
                minHeight = dp(42)
                setOnClickListener {
                    dialog.dismiss()
                    action.second?.invoke(dialog, which)
                }
            }
            actions.addView(button, LinearLayout.LayoutParams(-2, dp(42)).apply {
                marginStart = dp(5)
            })
        }
        addAction(neutral, DialogInterface.BUTTON_NEUTRAL, false)
        addAction(negative, DialogInterface.BUTTON_NEGATIVE, false)
        addAction(positive, DialogInterface.BUTTON_POSITIVE, true)
        if (actions.childCount > 0) outer.addView(actions)
        dialog.setContentView(outer)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.55f }
            setLayout((activity.resources.displayMetrics.widthPixels * 0.93f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
        dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * 0.93f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT)
        return dialog
    }
}
