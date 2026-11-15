// SPDX-License-Identifier: GPL-3.0-only
// HeliBoard Indo: built-in voice typing (Bahasa Indonesia by default), Gboard / SwiftKey style.
package helium314.keyboard.latin.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.ViewGroup
import android.widget.FrameLayout
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import java.util.Locale

/**
 * Speech-to-text inside the keyboard, without switching to another input method.
 * Uses the system speech recognition service (e.g. Google speech services) through
 * [SpeechRecognizer], so the keyboard itself still does not need internet permission.
 */
class VoiceInputController(private val ime: LatinIME) : RecognitionListener {

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var panel: VoicePanelView? = null
    private var listening = false
    /** true while the user wants continuous dictation (restart after each phrase) */
    private var continuous = false
    private var lastPartial = ""

    val isShowing get() = panel?.parent != null

    // ---------- public entry points ----------

    /** Called when the mic key / toolbar mic is pressed. */
    fun onVoiceKey() {
        if (!hasMicPermission(ime)) {
            VoicePermissionActivity.start(ime)
            return
        }
        showPanel()
        startListening()
    }

    /** Close the panel and stop listening (e.g. when the input field is left). */
    fun close() {
        continuous = false
        handler.removeCallbacksAndMessages(null)
        destroyRecognizer()
        panel?.let { (it.parent as? ViewGroup)?.removeView(it) }
        panel = null
    }

    fun onDestroy() = close()

    // ---------- called from the panel ----------

    internal fun onMicTapped() {
        if (listening) {
            continuous = false
            recognizer?.stopListening()
            panel?.setState(VoicePanelView.State.PROCESSING, lastPartial)
        } else {
            startListening()
        }
    }

    internal fun onLanguageTapped() {
        val current = currentLanguage(ime)
        val idx = LANGUAGES.indexOfFirst { it.first == current }
        val next = LANGUAGES[(idx + 1) % LANGUAGES.size].first
        ime.prefs().edit().putString(PREF_LANGUAGE, next).apply()
        panel?.setLanguage(languageLabel(next))
        if (listening) {
            // restart with the new language
            destroyRecognizer()
            startListening()
        }
    }

    internal fun onPunctuationToggled() {
        val newValue = !isSpokenPunctuationEnabled(ime)
        ime.prefs().edit().putBoolean(PREF_PUNCTUATION, newValue).apply()
        panel?.setPunctuation(newValue)
    }

    internal fun onDeleteTapped() {
        ime.onCodeInput(KeyCode.DELETE, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
    }

    internal fun onKeyboardTapped() = close()

    // ---------- recognition ----------

    private fun showPanel() {
        if (isShowing) return
        val wrapper = KeyboardSwitcher.getInstance().wrapperView ?: return
        val view = VoicePanelView(wrapper.context, this)
        view.setLanguage(languageLabel(currentLanguage(ime)))
        view.setPunctuation(isSpokenPunctuationEnabled(ime))
        wrapper.addView(view, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        panel = view
    }

    private fun startListening() {
        val sr = recognizer ?: try {
            SpeechRecognizer.createSpeechRecognizer(ime).also {
                it.setRecognitionListener(this)
                recognizer = it
            }
        } catch (e: Exception) {
            Log.e(TAG, "cannot create recognizer", e)
            panel?.setState(VoicePanelView.State.ERROR, "Layanan pengenal suara tidak tersedia")
            return
        }
        val lang = currentLanguage(ime)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ime.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
        lastPartial = ""
        continuous = true
        try {
            sr.startListening(intent)
            listening = true
            panel?.setState(VoicePanelView.State.STARTING, "")
        } catch (e: Exception) {
            Log.e(TAG, "startListening failed", e)
            listening = false
            panel?.setState(VoicePanelView.State.ERROR, "Gagal memulai mikrofon")
        }
    }

    private fun destroyRecognizer() {
        listening = false
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) { }
        recognizer = null
    }

    private fun commitRecognizedText(raw: String) {
        val ic = ime.currentInputConnection ?: return
        val usePunctuation = isSpokenPunctuationEnabled(ime) && currentLanguage(ime).startsWith("id")
        var text = VoiceTextFormatter.format(raw, usePunctuation)
        if (text.isEmpty()) return
        val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
        val locale = Locale.forLanguageTag(currentLanguage(ime))
        val inputType = ime.currentInputEditorInfo?.inputType ?: 0
        val capsWanted = inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
                ic.getCursorCapsMode(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0
        if (capsWanted) text = text.replaceFirstChar { it.titlecase(locale) }
        val first = text.first()
        val needsSpace = before.isNotEmpty() && !before.last().isWhitespace() &&
                first != '\n' && first !in VoiceTextFormatter.NO_SPACE_BEFORE
        if (needsSpace) text = " $text"
        ime.onTextInput(text)
    }

    override fun onReadyForSpeech(params: Bundle?) {
        panel?.setState(VoicePanelView.State.LISTENING, lastPartial)
    }

    override fun onBeginningOfSpeech() { }

    override fun onRmsChanged(rmsdB: Float) {
        panel?.setLevel(rmsdB)
    }

    override fun onBufferReceived(buffer: ByteArray?) { }

    override fun onEndOfSpeech() {
        panel?.setState(VoicePanelView.State.PROCESSING, lastPartial)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()?.takeIf { it.isNotBlank() } ?: return
        lastPartial = text
        panel?.setState(VoicePanelView.State.LISTENING, text)
    }

    override fun onResults(results: Bundle?) {
        listening = false
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            ?.takeIf { it.isNotBlank() } ?: lastPartial
        lastPartial = ""
        if (text.isNotBlank()) commitRecognizedText(text)
        if (continuous && isShowing) {
            // keep dictating like Gboard until the user stops or stays silent
            handler.postDelayed({ if (isShowing && !listening) startListening() }, 250)
        } else {
            panel?.setState(VoicePanelView.State.IDLE, "")
        }
    }

    override fun onError(error: Int) {
        val wasListening = listening
        listening = false
        // some recognizers deliver the last phrase only as partial result before an error
        if (lastPartial.isNotBlank() && error != SpeechRecognizer.ERROR_CLIENT) {
            commitRecognizedText(lastPartial)
        }
        lastPartial = ""
        continuous = false
        Log.i(TAG, "recognition error $error")
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                panel?.setState(VoicePanelView.State.IDLE, "")
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                close()
                VoicePermissionActivity.start(ime)
            }
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER, ERROR_SERVER_DISCONNECTED ->
                panel?.setState(VoicePanelView.State.ERROR,
                    "Tidak ada koneksi. Sambungkan internet, atau unduh paket suara offline Bahasa Indonesia.")
            ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE ->
                panel?.setState(VoicePanelView.State.ERROR,
                    "Bahasa ini belum tersedia di layanan suara. Unduh paket bahasanya di pengaturan pengenal suara.")
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                destroyRecognizer()
                panel?.setState(VoicePanelView.State.ERROR, "Pengenal suara sedang sibuk, ketuk mic lagi.")
            }
            ERROR_TOO_MANY_REQUESTS ->
                panel?.setState(VoicePanelView.State.ERROR, "Terlalu banyak permintaan, coba sebentar lagi.")
            SpeechRecognizer.ERROR_CLIENT -> {
                // usually caused by our own cancel/stop
                if (wasListening) panel?.setState(VoicePanelView.State.IDLE, "")
            }
            else -> panel?.setState(VoicePanelView.State.ERROR, "Terjadi kesalahan ($error). Ketuk mic untuk mencoba lagi.")
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) { }

    companion object {
        private const val TAG = "VoiceInput"
        const val PREF_LANGUAGE = "voice_input_language"
        const val PREF_PUNCTUATION = "voice_input_spoken_punctuation"

        // constants from newer APIs, as plain values
        private const val ERROR_TOO_MANY_REQUESTS = 10
        private const val ERROR_SERVER_DISCONNECTED = 11
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13

        /** language tag to label shown on the panel */
        val LANGUAGES = listOf(
            "id-ID" to "Indonesia",
            "en-US" to "English",
            "jv-ID" to "Jawa",
            "su-ID" to "Sunda",
        )

        private var availableCache: Boolean? = null

        @JvmStatic
        fun isAvailable(context: Context): Boolean {
            availableCache?.let { return it }
            val available = try {
                SpeechRecognizer.isRecognitionAvailable(context)
            } catch (e: Exception) {
                false
            }
            availableCache = available
            return available
        }

        @JvmStatic
        fun invalidateAvailability() {
            availableCache = null
        }

        fun hasMicPermission(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        fun currentLanguage(context: Context): String =
            context.prefs().getString(PREF_LANGUAGE, null)?.takeIf { tag -> LANGUAGES.any { it.first == tag } }
                ?: LANGUAGES.first().first

        fun isSpokenPunctuationEnabled(context: Context) = context.prefs().getBoolean(PREF_PUNCTUATION, true)

        fun languageLabel(tag: String) = LANGUAGES.firstOrNull { it.first == tag }?.second ?: tag
    }
}
