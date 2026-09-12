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
            pickLanguage()
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

    /** Indian English when the engine has it, otherwise whatever it defaults to. */
    private fun pickLanguage() {
        val indian = Locale("en", "IN")
        val r = tts.isLanguageAvailable(indian)
        if (r == TextToSpeech.LANG_AVAILABLE || r == TextToSpeech.LANG_COUNTRY_AVAILABLE || r == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE) tts.language = indian
    }

    fun speak(text: String) {
        if (!available) return
        if (!ready) { pending = text; return }
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
