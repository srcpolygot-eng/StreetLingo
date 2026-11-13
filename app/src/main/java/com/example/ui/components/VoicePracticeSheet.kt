package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.PronunciationResult
import com.example.data.world.CityCatalog
import com.example.ui.viewmodel.GrammarAiUiState
import com.example.ui.viewmodel.VoicePracticeState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VoicePracticeSheet(
  state: VoicePracticeState,
  tts: TtsController,
  coachAiState: GrammarAiUiState,
  onPreviousPrompt: () -> Unit,
  onNextPrompt: () -> Unit,
  onSpokenResult: (String) -> Unit,
  onRequestAiFeedback: () -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val context = LocalContext.current
  val city = CityCatalog.cityById(state.cityId)
  val voiceController = rememberVoicePracticeController()

  var permissionDenied by remember { mutableStateOf(false) }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    if (granted) {
      permissionDenied = false
      voiceController.startListening(city.languageTag) { transcript ->
        onSpokenResult(transcript)
      }
    } else {
      permissionDenied = true
    }
  }

  val animatedRms by animateFloatAsState(
    targetValue = if (voiceController.isListening) voiceController.rmsDb else 0.08f,
    label = "voiceRms",
  )

  ModalBottomSheet(
    onDismissRequest = {
      voiceController.stopListening()
      onDismiss()
    },
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.background,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .heightIn(max = 650.dp)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp)
        .padding(bottom = 18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Voice Speaking Studio",
            style = MaterialTheme.typography.titleLarge,
          )
          Text(
            text = "${city.name} (${city.languageName})" +
              if (state.npcName.isNotBlank()) " · ${state.npcName}" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(
          onClick = {
            voiceController.stopListening()
            onDismiss()
          },
          modifier = Modifier.size(48.dp).testTag("close_voice_practice_button"),
        ) {
          Icon(Icons.Rounded.Close, contentDescription = "Close voice practice")
        }
      }

      // Target Phrase Card with Normal + Slow listening controls
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (state.hasPlaylist) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "PROMPT ${state.promptIndex + 1} OF ${state.totalPrompts}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
              Row {
                IconButton(
                  onClick = onPreviousPrompt,
                  modifier = Modifier.size(48.dp).testTag("voice_prev_prompt_button"),
                ) {
                  Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous phrase")
                }
                IconButton(
                  onClick = onNextPrompt,
                  modifier = Modifier.size(48.dp).testTag("voice_next_prompt_button"),
                ) {
                  Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next phrase")
                }
              }
            }
          }

          Text(
            text = state.targetPhrase,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          if (state.phonetic.isNotBlank()) {
            Text(
              text = state.phonetic,
              style = MaterialTheme.typography.bodyMedium,
              fontStyle = FontStyle.Italic,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          if (state.englishMeaning.isNotBlank()) {
            Text(
              text = state.englishMeaning,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FilledTonalButton(
              onClick = { tts.speak(state.targetPhrase, city.languageTag, speechRate = 1.0f) },
              modifier = Modifier.weight(1f).testTag("voice_listen_normal_button"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Listen 1.0x")
            }
            OutlinedButton(
              onClick = { tts.speak(state.targetPhrase, city.languageTag, speechRate = 0.72f) },
              modifier = Modifier.weight(1f).testTag("voice_listen_slow_button"),
            ) {
              Icon(Icons.Rounded.SlowMotionVideo, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Slow 0.72x")
            }
          }
        }
      }

      // Live Waveform & Mic Trigger
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          VoiceWaveformBar(
            level = animatedRms,
            isListening = voiceController.isListening,
            modifier = Modifier
              .fillMaxWidth()
              .height(36.dp),
          )

          Button(
            onClick = {
              if (voiceController.isListening) {
                voiceController.stopListening()
              } else {
                val granted = ContextCompat.checkSelfPermission(
                  context,
                  Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                  permissionDenied = false
                  voiceController.startListening(city.languageTag) { transcript ->
                    onSpokenResult(transcript)
                  }
                } else {
                  permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
              }
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (voiceController.isListening) {
                MaterialTheme.colorScheme.secondary
              } else {
                MaterialTheme.colorScheme.primary
              },
            ),
            shape = CircleShape,
            modifier = Modifier
              .height(54.dp)
              .fillMaxWidth()
              .testTag("voice_practice_mic_button"),
          ) {
            Icon(
              imageVector = if (voiceController.isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
              contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = if (voiceController.isListening) {
                "Listening in ${city.languageName}... Tap to Stop"
              } else {
                "Hold or Tap to Speak ${city.languageName}"
              },
            )
          }

          if (voiceController.isListening && voiceController.partialText.isNotBlank()) {
            Text(
              text = "Hearing: \"${voiceController.partialText}\"",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }

          if (permissionDenied) {
            Text(
              text = "Microphone permission was denied. Allow RECORD_AUDIO in system prompts or App Settings to practice speaking.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
            )
          }

          voiceController.errorMessage?.let { err ->
            Text(
              text = err,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      }

      // Pronunciation Accuracy Score & Token Breakdown
      val eval: PronunciationResult? = state.evaluation
      if (state.lastSpokenTranscript.isNotBlank() && eval != null) {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text(
                  text = "You said: \"${state.lastSpokenTranscript}\"",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = eval.feedbackSummary,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Surface(
                shape = CircleShape,
                color = if (eval.score >= 70) {
                  MaterialTheme.colorScheme.primaryContainer
                } else {
                  MaterialTheme.colorScheme.secondaryContainer
                },
              ) {
                Text(
                  text = "${eval.score}%",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = if (eval.score >= 70) {
                    MaterialTheme.colorScheme.primary
                  } else {
                    MaterialTheme.colorScheme.secondary
                  },
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
              }
            }

            LinearProgressIndicator(
              progress = { eval.score / 100f },
              modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp)),
            )

            if (eval.wordMatches.isNotEmpty()) {
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                eval.wordMatches.forEach { wm ->
                  Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (wm.matched) {
                      MaterialTheme.colorScheme.primaryContainer
                    } else {
                      MaterialTheme.colorScheme.secondaryContainer
                    },
                  ) {
                    Text(
                      text = wm.text,
                      style = MaterialTheme.typography.labelMedium,
                      color = if (wm.matched) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                      } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                      },
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                  }
                }
              }
            }

            when (coachAiState) {
              is GrammarAiUiState.Idle -> {
                FilledTonalButton(
                  onClick = onRequestAiFeedback,
                  modifier = Modifier.fillMaxWidth().testTag("voice_gemini_coach_button"),
                ) {
                  Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(8.dp))
                  Text("Get Gemini 4 Pronunciation Feedback")
                }
              }
              is GrammarAiUiState.Streaming -> {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                  Text("Analyzing your spoken phrasing...", style = MaterialTheme.typography.labelMedium)
                }
                if (coachAiState.text.isNotBlank()) {
                  Text(coachAiState.text, style = MaterialTheme.typography.bodyMedium)
                }
              }
              is GrammarAiUiState.Success -> {
                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                ) {
                  Text(
                    text = coachAiState.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                  )
                }
              }
              is GrammarAiUiState.Error -> {
                Text(
                  text = coachAiState.message,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.error,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun VoiceWaveformBar(
  level: Float,
  isListening: Boolean,
  modifier: Modifier = Modifier,
) {
  val activeColor = MaterialTheme.colorScheme.secondary
  val idleColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
  Canvas(modifier = modifier) {
    val barCount = 24
    val spacing = size.width / barCount
    val barWidth = spacing * 0.54f
    val centerY = size.height / 2f
    for (i in 0 until barCount) {
      val distFromCenter = kotlin.math.abs(i - (barCount - 1) / 2f) / (barCount / 2f)
      val envelope = (1f - distFromCenter * 0.65f).coerceIn(0.25f, 1f)
      val wavePhase = if (isListening) (0.55f + 0.45f * kotlin.math.sin(i * 1.1f + level * 14f)) else 0.2f
      val h = (size.height * level * envelope * wavePhase).coerceIn(size.height * 0.14f, size.height)
      val x = i * spacing + (spacing - barWidth) / 2f
      drawRoundRect(
        color = if (isListening) activeColor else idleColor,
        topLeft = Offset(x, centerY - h / 2f),
        size = Size(barWidth, h),
        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
      )
    }
  }
}
