package com.example.ui.components

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Stable
class VoicePracticeController(
  private val isAvailable: Boolean,
  private val startFn: (String, (String) -> Unit) -> Unit,
  private val stopFn: () -> Unit,
  private val clearErrorFn: () -> Unit,
  val isListeningProvider: () -> Boolean,
  val partialTextProvider: () -> String,
  val rmsDbProvider: () -> Float,
  val errorProvider: () -> String?,
) {
  val isRecognizerAvailable: Boolean get() = isAvailable
  val isListening: Boolean get() = isListeningProvider()
  val partialText: String get() = partialTextProvider()
  val rmsDb: Float get() = rmsDbProvider()
  val errorMessage: String? get() = errorProvider()

  fun startListening(languageTag: String, onFinalResult: (String) -> Unit) {
    startFn(languageTag, onFinalResult)
  }

  fun stopListening() {
    stopFn()
  }

  fun clearError() {
    clearErrorFn()
  }
}

@Composable
fun rememberVoicePracticeController(): VoicePracticeController {
  val context = LocalContext.current
  val appContext = context.applicationContext
  val available = remember(appContext) {
    SpeechRecognizer.isRecognitionAvailable(appContext)
  }

  var isListening by remember { mutableStateOf(false) }
  var partialText by remember { mutableStateOf("") }
  var rmsDb by remember { mutableFloatStateOf(0f) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var activeCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
  val callbackState by rememberUpdatedState(activeCallback)

  val recognizer = remember(appContext, available) {
    if (available) SpeechRecognizer.createSpeechRecognizer(appContext) else null
  }

  DisposableEffect(recognizer) {
    if (recognizer != null) {
      recognizer.setRecognitionListener(
        object : RecognitionListener {
          override fun onReadyForSpeech(params: Bundle?) {
            errorMessage = null
            isListening = true
          }

          override fun onBeginningOfSpeech() {
            isListening = true
          }

          override fun onRmsChanged(rmsdB: Float) {
            rmsDb = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
          }

          override fun onBufferReceived(buffer: ByteArray?) = Unit

          override fun onEndOfSpeech() {
            isListening = false
            rmsDb = 0f
          }

          override fun onError(error: Int) {
            isListening = false
            rmsDb = 0f
            errorMessage = when (error) {
              SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Check microphone access."
              SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice practice."
              SpeechRecognizer.ERROR_NETWORK,
              SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs an internet connection on this device."
              SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that clearly — speak closer to the mic and try again."
              SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap the microphone and speak right away."
              SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Wait a moment and try again."
              SpeechRecognizer.ERROR_CLIENT -> null
              else -> "Speech recognition error ($error). Please try again."
            }
          }

          override fun onResults(results: Bundle?) {
            isListening = false
            rmsDb = 0f
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val best = matches.firstOrNull()?.trim().orEmpty()
            if (best.isNotEmpty()) {
              partialText = best
              callbackState?.invoke(best)
            } else {
              errorMessage = "Could not transcribe speech. Please try again."
            }
          }

          override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val latest = matches.firstOrNull()?.trim().orEmpty()
            if (latest.isNotEmpty()) {
              partialText = latest
            }
          }

          override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
      )
    }

    onDispose {
      runCatching {
        recognizer?.stopListening()
        recognizer?.cancel()
        recognizer?.destroy()
      }
    }
  }

  return remember(recognizer, available) {
    VoicePracticeController(
      isAvailable = available,
      startFn = { languageTag, onFinal ->
        if (recognizer == null) {
          errorMessage = "On-device or Google Speech Recognition is not installed on this device."
          return@VoicePracticeController
        }
        errorMessage = null
        partialText = ""
        activeCallback = onFinal
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
          putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
          putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
          putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
          putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
          putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        runCatching {
          isListening = true
          recognizer.startListening(intent)
        }.onFailure { err ->
          isListening = false
          errorMessage = err.localizedMessage ?: "Failed to start microphone."
        }
      },
      stopFn = {
        isListening = false
        rmsDb = 0f
        runCatching { recognizer?.stopListening() }
      },
      clearErrorFn = { errorMessage = null },
      isListeningProvider = { isListening },
      partialTextProvider = { partialText },
      rmsDbProvider = { rmsDb },
      errorProvider = { errorMessage },
    )
  }
}
