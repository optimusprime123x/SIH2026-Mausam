package dev.mausam.home.ui.common

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import dev.mausam.home.domain.i18n.L10n
import dev.mausam.home.domain.i18n.Lang
import java.util.Locale

/**
 * The device's own text-to-speech engine, warmed lazily. [speaking] is Compose state so a
 * speaker button can flip to pause while an utterance plays and back when it ends or fails.
 * Speech requested before the engine is ready plays as soon as it is.
 */
class Speaker(context: Context) {
    var speaking by mutableStateOf(false)
        private set
    var available by mutableStateOf(true)
        private set
    private var ready = false
    private var pending: String? = null
    private val main = Handler(Looper.getMainLooper())
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post {
            if (status != TextToSpeech.SUCCESS) { available = false; pending = null; return@post }
            ready = true
            pending?.let { pending = null; speak(it) }
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { main.post { speaking = true } }
            override fun onDone(utteranceId: String?) { main.post { speaking = false } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { speaking = false } }
            override fun onError(utteranceId: String?, errorCode: Int) { main.post { speaking = false } }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { main.post { speaking = false } }
        })
    }

    private var current: Lang? = null

    /** The app language when the engine has a voice for it (hi-IN, en-IN), otherwise the engine default. */
    private fun pickLanguage(lang: Lang) {
        if (current == lang) return
        current = lang
        val r = tts.isLanguageAvailable(lang.locale)
        if (r == TextToSpeech.LANG_AVAILABLE || r == TextToSpeech.LANG_COUNTRY_AVAILABLE || r == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE) tts.language = lang.locale
        else if (lang == Lang.HI) tts.isLanguageAvailable(Locale("hi")).takeIf { it >= 0 }?.let { tts.language = Locale("hi") }
    }

    fun speak(text: String, lang: Lang = L10n.lang) {
        if (!available) return
        if (!ready) { pending = text; return }
        pickLanguage(lang)
        speaking = true
        val r = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mausam-brief")
        if (r != TextToSpeech.SUCCESS) speaking = false
    }

    fun stop() {
        pending = null
        if (ready) tts.stop()
        speaking = false
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }
}

/** A speaker tied to the composition; null in previews, where no engine exists. */
@Composable
fun rememberSpeaker(): Speaker? {
    if (LocalInspectionMode.current) return null
    val context = LocalContext.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }
    return speaker
}
