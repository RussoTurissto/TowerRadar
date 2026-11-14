package com.russoturissto.towerradar

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import java.util.concurrent.Executor

/** A complete, local-only user tower form, styled with the existing Khaki dialog. */
class UserTowerEditor(
    private val activity: Activity,
    private val palette: KhakiPalette,
    private val store: UserTowerStore,
    private val executor: Executor,
    private val initialLat: Double,
    private val initialLon: Double,
    private val onSaved: (TowerSite) -> Unit,
    private val onClosed: () -> Unit
) {
    private var photoUri: Uri? = null
    private var photoHint: TextView? = null
    private var dialog: Dialog? = null
    val showing: Boolean get() = dialog?.isShowing == true
    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density + .5f).toInt()
    private fun rounded(color: Int) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color); cornerRadius = dp(11).toFloat()
    }
    fun onImagePicked(uri: Uri?) {
        if (uri == null || !showing) return
        photoUri = uri
        photoHint?.text = "Фотография выбрана · будет сохранена вместе с вышкой"
    }
    fun show() {
        if (showing) return
        photoUri = null
        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        fun field(title: String, initial: String = "", lines: Int = 1): EditText {
            form.addView(TextView(activity).apply {
                text = title; textSize = 12f; setTextColor(palette.secondary)
                setPadding(dp(3), dp(9), dp(3), dp(4))
            })
            val input = EditText(activity).apply {
                setText(initial)
                textSize = 14f; setTextColor(palette.text)
                setHintTextColor(palette.secondary)
                background = rounded(palette.elevated)
                setPadding(dp(11), dp(10), dp(11), dp(10))
                if (lines == 1) setSingleLine(true)
                else { setSingleLine(false); minLines = 2; maxLines = lines }
            }
            form.addView(input, LinearLayout.LayoutParams(-1, -2))
            return input
        }
        val name = field("Название вышки", "Моя вышка")
        val lat = field("Широта", String.format(Locale.US, "%.6f", initialLat))
        val lon = field("Долгота", String.format(Locale.US, "%.6f", initialLon))
        val operator = field("Оператор (если известен)")
        val technology = field("Технологии / стандарт")
        val height = field("Высота конструкции, м (если известна)")
        val equipment = field("Дополнительное оборудование", lines = 3)
        val notes = field("Примечания", lines = 3)
        photoHint = TextView(activity).apply {
            text = "Фотография не прикреплена"
            textSize = 12f; setTextColor(palette.secondary)
            setPadding(dp(4), dp(8), dp(4), dp(6))
        }
        form.addView(photoHint)
        val choose = Button(activity).apply {
            text = "▧  Добавить фотографию"; textSize = 12f
            isAllCaps = false; setTextColor(palette.text)
            background = rounded(palette.elevated)
        }
        form.addView(choose, LinearLayout.LayoutParams(-1, dp(44)))
        choose.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                @Suppress("DEPRECATION")
                activity.startActivityForResult(intent, 6201)
            } catch (_: Exception) {
                photoHint?.text = "Не удалось открыть выбор фотографии"
            }
        }
        val error = TextView(activity).apply {
            textSize = 12f
            setTextColor(Color.rgb(211, 112, 91))
            setPadding(dp(3), dp(7), dp(3), dp(4))
        }
        form.addView(error)
        val save = Button(activity).apply {
            text = "Сохранить вышку"
            isAllCaps = false; textSize = 13f
            setTextColor(palette.oliveText); background = rounded(palette.olive)
        }
        form.addView(save, LinearLayout.LayoutParams(-1, dp(46)).apply {
            topMargin = dp(4)
        })
        dialog = KhakiDialogBuilder(activity, palette).setTitle("Добавить вышку")
            .setMessage("Локальная запись: координаты, оборудование и фото " +
                "не отправляются на внешние серверы.")
            .setView(form).setMaxContentHeightDp(460)
            .setNegativeButton("Закрыть", null).show().also { shown ->
                shown.setOnDismissListener { dialog = null; photoHint = null; onClosed() }
            }
        save.setOnClickListener {
            fun number(raw: String): Double? =
                raw.trim().replace(',', '.').toDoubleOrNull()
            val rawHeight = height.text.toString()
            val parsedHeight = if (rawHeight.isBlank()) null else number(rawHeight)
            if (rawHeight.isNotBlank() && parsedHeight == null) {
                error.text = "Высота должна быть числом"
                return@setOnClickListener
            }
            val draft = UserTowerDraft(
                number(lat.text.toString()) ?: Double.NaN,
                number(lon.text.toString()) ?: Double.NaN,
                name.text.toString(), operator.text.toString(), technology.text.toString(),
                parsedHeight, equipment.text.toString(), notes.text.toString()
            )
            UserTowerStore.validate(draft)?.let {
                error.text = it
                return@setOnClickListener
            }
            save.isEnabled = false
            error.setTextColor(palette.secondary)
            error.text = "Сохраняем вышку и фотографию…"
            val selectedPhoto = photoUri
            executor.execute {
                val result = try { store.add(draft, selectedPhoto) to null }
                catch (e: Exception) { null to (e.message ?: "Не удалось сохранить запись") }
                activity.runOnUiThread {
                    val tower = result.first
                    if (tower != null) {
                        dialog?.dismiss()
                        onSaved(tower)
                    } else if (showing) {
                        error.setTextColor(Color.rgb(211, 112, 91))
                        error.text = result.second
                        save.isEnabled = true
                    }
                }
            }
        }
    }
}
