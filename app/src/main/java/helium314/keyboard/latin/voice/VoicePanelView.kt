// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ToolbarKey

/** Gboard-like voice panel shown on top of the keys while dictating. */
@SuppressLint("ViewConstructor", "SetTextI18n")
class VoicePanelView(context: Context, private val controller: VoiceInputController) : FrameLayout(context) {

    enum class State { STARTING, LISTENING, PROCESSING, IDLE, ERROR }

    private val textColor: Int
    private val accentColor: Int
    private val accentIconColor: Int
    private val chipColor: Int

    private val statusView: TextView
    private val hintView: TextView
    private val micHalo: View
    private val micButton: FrameLayout
    private val micIcon: ImageView
    private val languageChip: TextView
    private val punctuationChip: TextView
    private var state = State.IDLE
    private var smoothedLevel = 0f

    init {
        val colors = Settings.getValues().mColors
        val panelBackground = ColorUtils.setAlphaComponent(colors.get(ColorType.MAIN_BACKGROUND), 255)
        textColor = colors.get(ColorType.KEY_TEXT)
        accentColor = colors.get(ColorType.ACTION_KEY_BACKGROUND)
        accentIconColor = colors.get(ColorType.ACTION_KEY_ICON)
        chipColor = colors.get(ColorType.KEY_BACKGROUND)

        setBackgroundColor(panelBackground)
        isClickable = true // don't let touches through to the keys below
        isFocusable = true

        // ----- top bar: keyboard button (left), chips (right)
        val keyboardButton = chip("⌨  Keyboard").apply {
            contentDescription = "Kembali ke keyboard"
            setOnClickListener { controller.onKeyboardTapped() }
        }
        addView(keyboardButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START).apply { setMargins(dp(10), dp(8), 0, 0) })

        languageChip = chip("").apply {
            contentDescription = "Ganti bahasa suara"
            setOnClickListener { controller.onLanguageTapped() }
        }
        punctuationChip = chip("").apply {
            contentDescription = "Tanda baca lewat suara"
            setOnClickListener { controller.onPunctuationToggled() }
        }
        val chips = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(punctuationChip, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                .apply { marginEnd = dp(6) })
            addView(languageChip)
        }
        addView(chips, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END).apply { setMargins(0, dp(8), dp(10), 0) })

        // ----- center column: recognized text, mic, hint
        statusView = TextView(context).apply {
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.START
        }
        val micSize = dp(66)
        micHalo = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ColorUtils.setAlphaComponent(accentColor, 70))
            }
        }
        micIcon = ImageView(context).apply {
            val icon = KeyboardIconsSet.instance.getNewDrawable(ToolbarKey.VOICE.name, context)
            setImageDrawable(icon?.mutate())
            setColorFilter(accentIconColor)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        micButton = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accentColor)
            }
            contentDescription = "Mikrofon"
            addView(micIcon, LayoutParams(dp(30), dp(30), Gravity.CENTER))
            setOnClickListener { controller.onMicTapped() }
        }
        val micArea = FrameLayout(context).apply {
            addView(micHalo, LayoutParams(micSize, micSize, Gravity.CENTER))
            addView(micButton, LayoutParams(micSize, micSize, Gravity.CENTER))
            clipChildren = false
            clipToPadding = false
        }
        hintView = TextView(context).apply {
            setTextColor(ColorUtils.setAlphaComponent(textColor, 170))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            maxLines = 3
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            addView(statusView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(24), 0, dp(24), dp(4)) })
            addView(micArea, LinearLayout.LayoutParams((micSize * 1.35f).toInt(), (micSize * 1.35f).toInt()))
            addView(hintView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(72), dp(2), dp(72), 0) })
        }
        clipChildren = false
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            .apply { topMargin = dp(16) })

        // ----- bottom right: delete
        val deleteButton = ImageView(context).apply {
            val icon = KeyboardIconsSet.instance.getNewDrawable(KeyboardIconsSet.NAME_DELETE_KEY, context)
            setImageDrawable(icon?.mutate())
            setColorFilter(textColor)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundRect(chipColor)
            contentDescription = "Hapus"
            setOnClickListener { controller.onDeleteTapped() }
        }
        addView(deleteButton, LayoutParams(dp(64), dp(44), Gravity.BOTTOM or Gravity.END)
            .apply { setMargins(0, 0, dp(10), dp(10)) })

        setState(State.STARTING, "")
    }

    fun setLanguage(label: String) {
        languageChip.text = "🎙 $label"
    }

    fun setPunctuation(enabled: Boolean) {
        punctuationChip.text = if (enabled) "Tanda baca: ON" else "Tanda baca: OFF"
        punctuationChip.alpha = if (enabled) 1f else 0.6f
    }

    fun setState(newState: State, text: String) {
        state = newState
        when (newState) {
            State.STARTING -> {
                statusView.text = ""
                hintView.text = "Menyiapkan mikrofon…"
            }
            State.LISTENING -> {
                statusView.text = text
                hintView.text = if (text.isEmpty()) "Silakan bicara…" else "Ketuk mic untuk berhenti"
            }
            State.PROCESSING -> {
                statusView.text = text
                hintView.text = "Memproses…"
            }
            State.IDLE -> {
                statusView.text = ""
                hintView.text = "Ketuk mic untuk bicara\nUcapkan \"koma\", \"titik\", \"tanda tanya\", \"baris baru\""
            }
            State.ERROR -> {
                statusView.text = ""
                hintView.text = text
            }
        }
        val active = newState == State.LISTENING || newState == State.STARTING
        micButton.alpha = if (active || newState == State.PROCESSING) 1f else 0.75f
        if (!active) setLevel(-2f)
        micHalo.visibility = if (active) View.VISIBLE else View.INVISIBLE
    }

    /** Animate the halo around the mic with the input volume (rms in dB, roughly -2..10). */
    fun setLevel(rmsDb: Float) {
        val normalized = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)
        smoothedLevel = smoothedLevel * 0.6f + normalized * 0.4f
        val scale = 1f + smoothedLevel * 0.5f
        micHalo.scaleX = scale
        micHalo.scaleY = scale
    }

    private fun chip(label: String) = TextView(context).apply {
        text = label
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(dp(12), dp(6), dp(12), dp(6))
        background = roundRect(chipColor)
        gravity = Gravity.CENTER
        setSingleLine(true)
    }

    private fun roundRect(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(18).toFloat()
        setColor(if (Color.alpha(color) < 40) ColorUtils.setAlphaComponent(textColor, 30) else color)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
