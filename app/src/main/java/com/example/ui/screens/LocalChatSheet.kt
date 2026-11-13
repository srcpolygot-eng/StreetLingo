package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.data.model.ChatTurn
import com.example.data.model.CityDistrict
import com.example.data.model.GrammarPoint
import com.example.data.model.StreetLocal
import com.example.data.world.GrammarCatalog
import com.example.ui.components.TtsController
import com.example.ui.viewmodel.HdPaintUiState
import com.example.ui.viewmodel.IllustrationUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalChatSheet(
  city: CityDistrict,
  local: StreetLocal,
  turns: List<ChatTurn>,
  isChatStreaming: Boolean,
  chatError: String?,
  illustrationState: IllustrationUiState,
  hdPaintState: HdPaintUiState,
  isQuestCompleted: Boolean,
  tts: TtsController,
  onSendMessage: (String) -> Unit,
  onIllustrateSubject: (String, String) -> Unit,
  onRequestHdPaint: (String) -> Unit,
  onSaveTurn: (ChatTurn) -> Unit,
  onSaveIllustrationCard: () -> Unit,
  onInspectGrammar: (GrammarPoint?, ChatTurn) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var inputText by remember { mutableStateOf("") }
  val listState = rememberLazyListState()

  LaunchedEffect(turns.size, turns.lastOrNull()?.text?.length) {
    if (turns.isNotEmpty()) {
      listState.animateScrollToItem(turns.lastIndex)
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.background,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 16.dp)
        .padding(bottom = 14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(local.outfitColor),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = local.name.take(1),
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onPrimary,
            )
          }
          Column {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = local.name,
                style = MaterialTheme.typography.titleLarge,
              )
              if (isQuestCompleted) {
                Icon(
                  imageVector = Icons.Rounded.CheckCircle,
                  contentDescription = "Quest completed",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(18.dp),
                )
              }
            }
            Text(
              text = "${local.roleTitle} · ${city.streetName}",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        IconButton(
          onClick = onDismiss,
          modifier = Modifier.size(48.dp).testTag("close_chat_sheet_button"),
        ) {
          Icon(Icons.Rounded.Close, contentDescription = "Close conversation")
        }
      }

      StreamedIllustrationPanel(
        illustrationState = illustrationState,
        hdPaintState = hdPaintState,
        onRefresh = {
          onIllustrateSubject(local.illustrationSubject, turns.lastOrNull()?.text.orEmpty())
        },
        onRequestHdPaint = { subject -> onRequestHdPaint(subject) },
        onSaveCard = onSaveIllustrationCard,
      )

      LazyColumn(
        state = listState,
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 160.dp, max = 260.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
      ) {
        items(turns, key = { it.id }) { turn ->
          ChatTurnCard(
            turn = turn,
            localName = local.name,
            languageTag = city.languageTag,
            tts = tts,
            onIllustrate = {
              onIllustrateSubject(
                "${local.roleTitle} in ${city.name}: ${turn.text}",
                turn.text,
              )
            },
            onSave = { onSaveTurn(turn) },
            onInspectGrammar = { point -> onInspectGrammar(point, turn) },
          )
        }
      }

      if (!chatError.isNullOrBlank()) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        ) {
          Text(
            text = chatError,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(10.dp),
          )
        }
      }

      val latestSuggestions = turns.lastOrNull { !it.isUser }?.suggestedReplies.orEmpty()
      if (latestSuggestions.isNotEmpty() && !isChatStreaming) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(latestSuggestions) { suggestion ->
            AssistChip(
              onClick = { onSendMessage(suggestion) },
              label = { Text(suggestion, maxLines = 1) },
              modifier = Modifier.testTag("quick_reply_chip"),
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = inputText,
          onValueChange = { inputText = it },
          placeholder = { Text("Reply in ${city.languageName} or English...") },
          singleLine = true,
          enabled = !isChatStreaming,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(
            onSend = {
              if (inputText.isNotBlank() && !isChatStreaming) {
                onSendMessage(inputText)
                inputText = ""
              }
            },
          ),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .weight(1f)
            .testTag("chat_message_input"),
        )
        FilledTonalIconButton(
          onClick = {
            if (inputText.isNotBlank()) {
              onSendMessage(inputText)
              inputText = ""
            }
          },
          enabled = inputText.isNotBlank() && !isChatStreaming,
          modifier = Modifier
            .size(52.dp)
            .testTag("send_chat_button"),
        ) {
          if (isChatStreaming) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
          } else {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send reply")
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatTurnCard(
  turn: ChatTurn,
  localName: String,
  languageTag: String,
  tts: TtsController,
  onIllustrate: () -> Unit,
  onSave: () -> Unit,
  onInspectGrammar: (GrammarPoint?) -> Unit,
) {
  val isUser = turn.isUser
  val containerColor = if (isUser) {
    MaterialTheme.colorScheme.primary
  } else {
    MaterialTheme.colorScheme.surface
  }
  val contentColor = if (isUser) {
    MaterialTheme.colorScheme.onPrimary
  } else {
    MaterialTheme.colorScheme.onSurface
  }
  val matchedPoints = remember(turn.grammarPointIds) {
    turn.grammarPointIds.mapNotNull { GrammarCatalog.pointById(it) }
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
  ) {
    Card(
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(containerColor = containerColor),
      modifier = Modifier.fillMaxWidth(0.92f),
    ) {
      Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        if (!isUser) {
          Text(
            text = localName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
        Text(
          text = turn.text.ifBlank { if (turn.isStreaming) "..." else "" },
          style = MaterialTheme.typography.bodyLarge,
          color = contentColor,
        )

        if (!isUser && turn.phonetic.isNotBlank()) {
          Text(
            text = turn.phonetic,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        if (!isUser && turn.translation.isNotBlank()) {
          Text(
            text = turn.translation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (!isUser && turn.coachTip.isNotBlank()) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
          ) {
            Text(
              text = turn.coachTip,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            )
          }
        }
        if (turn.questCompleted) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Text(
              text = "Street Quest Complete! +75 XP",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }

        // Contextual Grammar Point chips surfaced right where the user encounters them
        if (!turn.isStreaming && matchedPoints.isNotEmpty()) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            matchedPoints.forEach { point ->
              AssistChip(
                onClick = { onInspectGrammar(point) },
                leadingIcon = {
                  Icon(
                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                  )
                },
                label = { Text(point.shortBadge, maxLines = 1) },
                colors = AssistChipDefaults.assistChipColors(
                  containerColor = if (isUser) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)
                  } else {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                  },
                  labelColor = if (isUser) {
                    MaterialTheme.colorScheme.onPrimary
                  } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                  },
                  leadingIconContentColor = if (isUser) {
                    MaterialTheme.colorScheme.onPrimary
                  } else {
                    MaterialTheme.colorScheme.secondary
                  },
                ),
                modifier = Modifier.testTag("grammar_chip_${point.id}"),
              )
            }
          }
        }

        if (!isUser && !turn.isStreaming && turn.text.isNotBlank()) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconButton(
              onClick = { tts.speak(turn.text, languageTag) },
              modifier = Modifier.size(48.dp).testTag("speak_turn_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.VolumeUp,
                contentDescription = "Listen to pronunciation",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
              )
            }
            IconButton(
              onClick = onIllustrate,
              modifier = Modifier.size(48.dp).testTag("illustrate_turn_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = "Stream instant illustration",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp),
              )
            }
            IconButton(
              onClick = { onInspectGrammar(matchedPoints.firstOrNull()) },
              modifier = Modifier.size(48.dp).testTag("turn_grammar_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                contentDescription = "Explain grammar in context",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
              )
            }
            IconButton(
              onClick = onSave,
              modifier = Modifier.size(48.dp).testTag("save_turn_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.BookmarkAdd,
                contentDescription = "Save to Phrasebook",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }
    }
  }
}
