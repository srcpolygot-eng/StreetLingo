package com.example.ui.components

import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

class TtsController(
  private val tts: TextToSpeech?,
  private val isReady: Boolean,
) {
  fun speak(text: String, languageTag: String) {
    if (!isReady || tts == null || text.isBlank()) return
    val locale = Locale.forLanguageTag(languageTag)
    tts.language = locale
    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "streetlingo_${System.currentTimeMillis()}")
  }
}

@Composable
fun rememberTtsController(): TtsController {
  val context = LocalContext.current
  var ready by remember { mutableStateOf(false) }
  var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }

  DisposableEffect(context) {
    lateinit var engine: TextToSpeech
    engine = TextToSpeech(context.applicationContext) { status ->
      ready = (status == TextToSpeech.SUCCESS)
    }
    ttsInstance = engine
    onDispose {
      engine.stop()
      engine.shutdown()
      ttsInstance = null
      ready = false
    }
  }

  return remember(ttsInstance, ready) {
    TtsController(ttsInstance, ready)
  }
}
