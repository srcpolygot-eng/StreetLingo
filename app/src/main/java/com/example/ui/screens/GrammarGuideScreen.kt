package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ChatTurn
import com.example.data.model.CityDistrict
import com.example.data.model.GrammarCategory
import com.example.data.model.GrammarExample
import com.example.data.model.GrammarPoint
import com.example.data.model.StreetDifficulty
import com.example.data.world.CityCatalog
import com.example.data.world.GrammarCatalog
import com.example.ui.components.highlightedGrammarSentence
import com.example.ui.components.rememberTtsController
import com.example.ui.viewmodel.GrammarAiUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GrammarGuideScreen(
  activeCityId: String,
  selectedGrammarCityId: String?,
  focusedGrammarPointId: String?,
  activeNpcName: String?,
  recentChatTurns: List<ChatTurn>,
  extraExamplesMap: Map<String, GrammarAiUiState>,
  onSelectCity: (String) -> Unit,
  onClearFocusedPoint: () -> Unit,
  onRequestMoreExamples: (GrammarPoint) -> Unit,
  onPracticeWithLocal: (cityId: String, npcId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val currentCityId = selectedGrammarCityId ?: activeCityId
  val city = CityCatalog.cityById(currentCityId)
  val tts = rememberTtsController()
  val listState = rememberLazyListState()

  var selectedCategory by remember { mutableStateOf<GrammarCategory?>(null) }
  var selectedDifficulty by remember { mutableStateOf<StreetDifficulty?>(null) }
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var expandedPointId by rememberSaveable { mutableStateOf<String?>(null) }

  val filteredPoints = remember(currentCityId, selectedCategory, selectedDifficulty, searchQuery) {
    GrammarCatalog.filterPoints(
      cityId = currentCityId,
      category = selectedCategory,
      difficulty = selectedDifficulty,
      query = searchQuery,
    )
  }

  val recentPointIds = remember(recentChatTurns, currentCityId, activeCityId) {
    if (currentCityId != activeCityId) emptyList()
    else recentChatTurns.flatMap { it.grammarPointIds }.distinct()
      .mapNotNull { GrammarCatalog.pointById(it) }
  }

  LaunchedEffect(focusedGrammarPointId, filteredPoints) {
    if (!focusedGrammarPointId.isNullOrBlank()) {
      expandedPointId = focusedGrammarPointId
      val index = filteredPoints.indexOfFirst { it.id == focusedGrammarPointId }
      if (index >= 0) {
        listState.animateScrollToItem(index)
      }
      onClearFocusedPoint()
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .statusBarsPadding()
      .padding(horizontal = 16.dp),
  ) {
    Spacer(Modifier.height(12.dp))
    Text(
      text = stringResource(R.string.grammar_guide_title),
      style = MaterialTheme.typography.headlineLarge,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      text = stringResource(R.string.grammar_guide_subtitle),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))

    // City selector
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      items(CityCatalog.cities, key = { it.id }) { district ->
        FilterChip(
          selected = district.id == currentCityId,
          onClick = { onSelectCity(district.id) },
          label = { Text("${district.name} (${district.languageName})") },
          modifier = Modifier.testTag("grammar_city_chip_${district.id}"),
        )
      }
    }

    Spacer(Modifier.height(8.dp))

    // Search input
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      placeholder = { Text("Search ${city.languageName} rules, patterns, or NPCs...") },
      leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          IconButton(onClick = { searchQuery = "" }) {
            Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
          }
        }
      },
      singleLine = true,
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("grammar_search_input"),
    )

    Spacer(Modifier.height(8.dp))

    // Category filter row
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      item {
        FilterChip(
          selected = selectedCategory == null,
          onClick = { selectedCategory = null },
          label = { Text("All Topics") },
        )
      }
      items(GrammarCategory.entries, key = { it.id }) { cat ->
        FilterChip(
          selected = selectedCategory == cat,
          onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
          label = { Text(cat.label) },
          modifier = Modifier.testTag("grammar_cat_${cat.id}"),
        )
      }
    }

    // CEFR Difficulty filter row
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      item {
        FilterChip(
          selected = selectedDifficulty == null,
          onClick = { selectedDifficulty = null },
          label = { Text("All Levels") },
        )
      }
      items(StreetDifficulty.entries, key = { it.id }) { diff ->
        FilterChip(
          selected = selectedDifficulty == diff,
          onClick = { selectedDifficulty = if (selectedDifficulty == diff) null else diff },
          label = { Text("${diff.label} · ${diff.cefr}") },
        )
      }
    }

    // Encountered in current street chat banner
    if (recentPointIds.isNotEmpty()) {
      Spacer(Modifier.height(6.dp))
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = if (!activeNpcName.isNullOrBlank()) {
              "Encountered in your walk with $activeNpcName"
            } else {
              "Encountered in your street conversation"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            recentPointIds.forEach { pt ->
              AssistChip(
                onClick = {
                  selectedCategory = null
                  selectedDifficulty = null
                  searchQuery = ""
                  expandedPointId = pt.id
                },
                leadingIcon = {
                  Icon(
                    Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                },
                label = { Text(pt.shortBadge) },
                modifier = Modifier.testTag("recent_grammar_chip_${pt.id}"),
              )
            }
          }
        }
      }
    }

    Spacer(Modifier.height(10.dp))

    if (filteredPoints.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.padding(24.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
          )
          Text(
            text = "No matching grammar lessons",
            style = MaterialTheme.typography.titleLarge,
          )
          Text(
            text = "Try clearing your search or switching topic filters.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(filteredPoints, key = { it.id }) { point ->
          val expanded = expandedPointId == point.id
          GrammarPointCard(
            point = point,
            city = city,
            expanded = expanded,
            extraExamplesState = extraExamplesMap[point.id] ?: GrammarAiUiState.Idle,
            onToggleExpand = { expandedPointId = if (expanded) null else point.id },
            onSpeak = { sentence -> tts.speak(sentence, city.languageTag) },
            onRequestMoreExamples = { onRequestMoreExamples(point) },
            onPracticeWithNpc = { npcId -> onPracticeWithLocal(city.id, npcId) },
          )
        }
      }
    }
  }
}

@Composable
private fun GrammarPointCard(
  point: GrammarPoint,
  city: CityDistrict,
  expanded: Boolean,
  extraExamplesState: GrammarAiUiState,
  onToggleExpand: () -> Unit,
  onSpeak: (String) -> Unit,
  onRequestMoreExamples: () -> Unit,
  onPracticeWithNpc: (String) -> Unit,
) {
  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = if (expanded) 4.dp else 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("grammar_card_${point.id}"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable(role = Role.Button, onClick = onToggleExpand),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = MaterialTheme.colorScheme.primaryContainer,
            ) {
              Text(
                text = point.category.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
              )
            }
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
              Text(
                text = point.difficulty.cefr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
              )
            }
          }
          Text(
            text = point.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
        IconButton(
          onClick = onToggleExpand,
          modifier = Modifier.size(48.dp).testTag("toggle_grammar_${point.id}"),
        ) {
          Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = if (expanded) "Collapse lesson" else "Expand lesson",
          )
        }
      }

      // Formula pill always visible for quick scanning
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
      ) {
        Text(
          text = point.patternFormula,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )
      }

      AnimatedVisibility(visible = expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = point.explanation,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )

          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(10.dp),
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

          Text(
            text = "Examples from ${city.name} Dialogues",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
          )

          point.dialogueExamples.forEach { ex ->
            GrammarDialogueExampleCard(
              example = ex,
              onSpeak = { onSpeak(ex.targetSentence) },
              onPractice = { onPracticeWithNpc(ex.npcId) },
            )
          }

          if (point.commonMistakes.isNotEmpty()) {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                  )
                  Text(
                    text = "Common Street Pitfalls",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                  )
                }
                point.commonMistakes.forEach { mistake ->
                  Text(
                    text = "• $mistake",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                }
              }
            }
          }

          // Gemini 4 Hydrogen extra street examples
          when (extraExamplesState) {
            is GrammarAiUiState.Idle -> {
              FilledTonalButton(
                onClick = onRequestMoreExamples,
                modifier = Modifier.fillMaxWidth().testTag("more_examples_${point.id}"),
              ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Generate Fresh Street Examples (Gemini 4)")
              }
            }
            is GrammarAiUiState.Streaming -> {
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(10.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Generating ${city.streetName} dialogue variations...", style = MaterialTheme.typography.labelMedium)
                  }
                  if (extraExamplesState.text.isNotBlank()) {
                    Text(extraExamplesState.text, style = MaterialTheme.typography.bodyMedium)
                  }
                }
              }
            }
            is GrammarAiUiState.Success -> {
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(10.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                    text = "Fresh Street Variations (Gemini 4 Hydrogen)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                  )
                  Text(extraExamplesState.text, style = MaterialTheme.typography.bodyMedium)
                }
              }
            }
            is GrammarAiUiState.Error -> {
              Text(
                text = extraExamplesState.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
              )
              OutlinedButton(
                onClick = onRequestMoreExamples,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text("Retry Example Generation")
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun GrammarDialogueExampleCard(
  example: GrammarExample,
  onSpeak: () -> Unit,
  onPractice: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "${example.npcName} · ${example.locationContext}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
        IconButton(onClick = onSpeak, modifier = Modifier.size(48.dp)) {
          Icon(
            imageVector = Icons.Rounded.VolumeUp,
            contentDescription = "Listen to dialogue example",
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }
      Text(
        text = highlightedGrammarSentence(example.targetSentence, example.highlightedSegment),
        style = MaterialTheme.typography.bodyLarge,
      )
      Text(
        text = example.phonetic,
        style = MaterialTheme.typography.bodyMedium,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.primary,
      )
      Text(
        text = example.englishTranslation,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = example.breakdownNote,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      OutlinedButton(
        onClick = onPractice,
        modifier = Modifier.padding(top = 4.dp),
      ) {
        Icon(Icons.Rounded.Forum, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Practice with ${example.npcName}")
      }
    }
  }
}
