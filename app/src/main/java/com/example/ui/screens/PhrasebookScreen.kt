package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.example.data.model.GrammarPoint
import com.example.data.model.IllustrationDslParser
import com.example.data.model.SavedPhrase
import com.example.data.world.CityCatalog
import com.example.data.world.GrammarCatalog
import com.example.ui.components.InstantIllustrationCanvas
import com.example.ui.components.rememberTtsController
import com.example.ui.viewmodel.DataUiState

@Composable
fun PhrasebookScreen(
  phrasesState: DataUiState<List<SavedPhrase>>,
  onUpdateMastery: (String, Long) -> Unit,
  onDeletePhrase: (String) -> Unit,
  onInspectGrammar: (GrammarPoint, SavedPhrase) -> Unit,
  onPracticeVoice: (SavedPhrase) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedCityFilter by remember { mutableStateOf("all") }
  val tts = rememberTtsController()

  Column(
    modifier = modifier
      .fillMaxSize()
      .statusBarsPadding()
      .padding(horizontal = 16.dp),
  ) {
    Spacer(Modifier.height(12.dp))
    Text(
      text = "Street Phrasebook",
      style = MaterialTheme.typography.headlineLarge,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      text = "Saved local expressions & streamed illustrations synced to Firestore",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      item {
        FilterChip(
          selected = selectedCityFilter == "all",
          onClick = { selectedCityFilter = "all" },
          label = { Text("All Cities") },
        )
      }
      items(CityCatalog.cities, key = { it.id }) { city ->
        FilterChip(
          selected = selectedCityFilter == city.id,
          onClick = { selectedCityFilter = city.id },
          label = { Text("${city.name} (${city.languageName})") },
        )
      }
    }

    Spacer(Modifier.height(10.dp))

    when (phrasesState) {
      is DataUiState.Loading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }
      is DataUiState.Error -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
            text = phrasesState.message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
      is DataUiState.Success -> {
        val filtered = if (selectedCityFilter == "all") {
          phrasesState.data
        } else {
          phrasesState.data.filter { it.cityId == selectedCityFilter }
        }

        if (filtered.isEmpty()) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.padding(24.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.AutoStories,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(54.dp),
              )
              Text(
                text = "Your phrasebook is empty",
                style = MaterialTheme.typography.titleLarge,
              )
              Text(
                text = "Walk up to any local on the 2D or 3D streets, chat, and tap Save Card on any reply or streamed illustration.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            items(filtered, key = { it.id }) { phrase ->
              SavedPhraseCard(
                phrase = phrase,
                onSpeak = {
                  val city = CityCatalog.cityById(phrase.cityId)
                  tts.speak(phrase.targetText, city.languageTag)
                },
                onUpdateMastery = { level -> onUpdateMastery(phrase.id, level) },
                onDelete = { onDeletePhrase(phrase.id) },
                onInspectGrammar = { pt -> onInspectGrammar(pt, phrase) },
                onPracticeVoice = { onPracticeVoice(phrase) },
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedPhraseCard(
  phrase: SavedPhrase,
  onSpeak: () -> Unit,
  onUpdateMastery: (Long) -> Unit,
  onDelete: () -> Unit,
  onInspectGrammar: (GrammarPoint) -> Unit,
  onPracticeVoice: () -> Unit,
) {
  val city = CityCatalog.cityById(phrase.cityId)
  val parsed = remember(phrase.illustrationCommands) {
    IllustrationDslParser.parseLines(phrase.illustrationCommands)
  }
  val grammarPoints = remember(phrase.cityId, phrase.targetText) {
    GrammarCatalog.detectGrammarPoints(phrase.cityId, phrase.targetText)
  }

  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("phrase_card_${phrase.id}"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Surface(
          shape = RoundedCornerShape(999.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
        ) {
          Text(
            text = "${city.name} · ${phrase.npcName}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onSpeak,
            modifier = Modifier.size(48.dp).testTag("speak_phrase_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
              contentDescription = "Speak phrase",
              tint = MaterialTheme.colorScheme.primary,
            )
          }
          IconButton(
            onClick = onPracticeVoice,
            modifier = Modifier.size(48.dp).testTag("practice_phrase_voice_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Mic,
              contentDescription = "Practice speaking phrase",
              tint = MaterialTheme.colorScheme.secondary,
            )
          }
          IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
            Icon(
              imageVector = Icons.Rounded.DeleteOutline,
              contentDescription = "Remove phrase",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      if (parsed.primitives.isNotEmpty()) {
        InstantIllustrationCanvas(
          parsed = parsed,
          isStreaming = false,
          modelName = "Saved Scene",
          modifier = Modifier
            .fillMaxWidth()
            .height(145.dp),
        )
      }

      Text(
        text = phrase.targetText,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )

      if (phrase.phonetic.isNotBlank()) {
        Text(
          text = phrase.phonetic,
          style = MaterialTheme.typography.bodyMedium,
          fontStyle = FontStyle.Italic,
          color = MaterialTheme.colorScheme.primary,
        )
      }

      Text(
        text = phrase.translation,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (phrase.culturalNote.isNotBlank()) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
        ) {
          Text(
            text = phrase.culturalNote,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(10.dp),
          )
        }
      }

      if (grammarPoints.isNotEmpty()) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          grammarPoints.forEach { pt ->
            AssistChip(
              onClick = { onInspectGrammar(pt) },
              leadingIcon = {
                Icon(
                  imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              label = { Text("Grammar: ${pt.shortBadge}") },
              modifier = Modifier.testTag("phrase_grammar_chip_${pt.id}"),
            )
          }
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = "Mastery:",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        for (star in 1..5) {
          val filled = star <= phrase.masteryLevel
          IconButton(
            onClick = { onUpdateMastery(star.toLong()) },
            modifier = Modifier.size(40.dp),
          ) {
            Icon(
              imageVector = if (filled) Icons.Rounded.Star else Icons.Rounded.StarBorder,
              contentDescription = "Set mastery $star",
              tint = if (filled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
            )
          }
        }
      }
    }
  }
}
