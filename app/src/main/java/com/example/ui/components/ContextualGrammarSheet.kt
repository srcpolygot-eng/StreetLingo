package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.GrammarPoint
import com.example.data.world.CityCatalog
import com.example.ui.viewmodel.ContextualGrammarState
import com.example.ui.viewmodel.GrammarAiUiState

@Composable
fun highlightedGrammarSentence(
  sentence: String,
  segment: String,
) = buildAnnotatedString {
  val cleanSeg = segment.trim()
  val idx = if (cleanSeg.isNotEmpty()) sentence.indexOf(cleanSeg, ignoreCase = true) else -1
  if (idx >= 0) {
    append(sentence.substring(0, idx))
    withStyle(
      SpanStyle(
        color = MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.Bold,
        background = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
      ),
    ) {
      append(sentence.substring(idx, idx + cleanSeg.length))
    }
    append(sentence.substring(idx + cleanSeg.length))
  } else {
    append(sentence)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextualGrammarSheet(
  state: ContextualGrammarState,
  aiState: GrammarAiUiState,
  tts: TtsController,
  onAskGeminiBreakdown: () -> Unit,
  onOpenInFullGuide: (GrammarPoint) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val city = CityCatalog.cityById(state.cityId)
  val point = state.point

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.background,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .heightIn(max = 620.dp)
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
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Column {
            Text(
              text = "Grammar in Context",
              style = MaterialTheme.typography.titleLarge,
            )
            Text(
              text = "${city.name} (${city.languageName})" +
                if (!state.npcName.isNullOrBlank()) " · with ${state.npcName}" else "",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.size(48.dp).testTag("close_contextual_grammar_button"),
        ) {
          Icon(Icons.Rounded.Close, contentDescription = "Close grammar sheet")
        }
      }

      // Exact dialogue sentence encountered
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = "FROM DIALOGUE",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "« ${state.sourceSentence} »",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            if (state.turnNote.isNotBlank()) {
              Text(
                text = state.turnNote,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          IconButton(
            onClick = { tts.speak(state.sourceSentence, city.languageTag) },
            modifier = Modifier.size(48.dp).testTag("contextual_speak_sentence_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.VolumeUp,
              contentDescription = "Listen to dialogue sentence",
              tint = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }

      if (point != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
          ) {
            Text(
              text = point.category.label,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
          ) {
            Text(
              text = "${point.difficulty.label} · ${point.difficulty.cefr}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }
        }

        Text(
          text = point.title,
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )

        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
              text = "PATTERN FORMULA",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(4.dp))
            Text(
              text = point.patternFormula,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }

        Text(
          text = point.explanation,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onBackground,
        )

        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Lightbulb,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.tertiary,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = point.streetTip,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
          }
        }

        val firstExample = point.dialogueExamples.firstOrNull()
        if (firstExample != null) {
          Text(
            text = "How ${firstExample.npcName} uses it on ${city.streetName}",
            style = MaterialTheme.typography.titleMedium,
          )
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = "${firstExample.npcName} · ${firstExample.locationContext}",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.secondary,
                )
                IconButton(
                  onClick = { tts.speak(firstExample.targetSentence, city.languageTag) },
                  modifier = Modifier.size(48.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.VolumeUp,
                    contentDescription = "Listen to example",
                    tint = MaterialTheme.colorScheme.primary,
                  )
                }
              }
              Text(
                text = highlightedGrammarSentence(firstExample.targetSentence, firstExample.highlightedSegment),
                style = MaterialTheme.typography.bodyLarge,
              )
              Text(
                text = firstExample.phonetic,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = firstExample.englishTranslation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = firstExample.breakdownNote,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }

        if (point.commonMistakes.isNotEmpty()) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = "Watch out: ${point.commonMistakes.first()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }

      // Live Gemini 4 Hydrogen word-by-word contextual breakdown
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.AutoAwesome,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = "Live Contextual Breakdown (Gemini 4 Hydrogen)",
              style = MaterialTheme.typography.titleMedium,
            )
          }

          when (aiState) {
            is GrammarAiUiState.Idle -> {
              Text(
                text = "Get a word-by-word gloss and variation tailored to this exact line.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              FilledTonalButton(
                onClick = onAskGeminiBreakdown,
                modifier = Modifier.fillMaxWidth().testTag("explain_grammar_gemini_button"),
              ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.explain_with_gemini))
              }
            }
            is GrammarAiUiState.Streaming -> {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                  text = "Analyzing sentence structure...",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
              if (aiState.text.isNotBlank()) {
                Text(
                  text = aiState.text,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
            }
            is GrammarAiUiState.Success -> {
              Text(
                text = aiState.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            is GrammarAiUiState.Error -> {
              Text(
                text = aiState.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
              )
              OutlinedButton(
                onClick = onAskGeminiBreakdown,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text("Retry Contextual Breakdown")
              }
            }
          }
        }
      }

      if (point != null) {
        Button(
          onClick = { onOpenInFullGuide(point) },
          modifier = Modifier.fillMaxWidth().testTag("open_in_grammar_guide_button"),
        ) {
          Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(stringResource(R.string.open_in_grammar_guide))
        }
      }
    }
  }
}
