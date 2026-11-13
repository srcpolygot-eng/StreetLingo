package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CityDistrict
import com.example.data.model.StreakCalculator
import com.example.data.model.UserProfile
import com.example.data.model.ViewDimension
import com.example.data.world.CityCatalog
import com.example.ui.components.rememberTtsController
import com.example.ui.street.StreetCanvas2D
import com.example.ui.street.StreetCanvas3D
import com.example.ui.viewmodel.HdPaintUiState
import com.example.ui.viewmodel.IllustrationUiState
import com.example.ui.viewmodel.StreetLingoViewModel
import com.example.ui.viewmodel.StreetWorldUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreetExplorerScreen(
  profile: UserProfile,
  worldState: StreetWorldUiState,
  illustrationState: IllustrationUiState,
  hdPaintState: HdPaintUiState,
  viewModel: StreetLingoViewModel,
  modifier: Modifier = Modifier,
) {
  val city = CityCatalog.cityById(profile.activeCityId)
  val tts = rememberTtsController()
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(worldState.savedToastMessage) {
    val msg = worldState.savedToastMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearSavedToast()
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    // Playable 2D or 3D Street Canvas
    if (worldState.viewDimension == ViewDimension.MODE_2D) {
      StreetCanvas2D(
        city = city,
        playerX = worldState.playerX,
        playerZ = worldState.playerZ,
        playerYawRad = worldState.playerYawRad,
        completedNpcIds = profile.completedNpcIds.toSet(),
        nearbyLocalId = worldState.nearbyLocal?.id,
        onWalkToWorldPos = { x, z -> viewModel.setPlayerPosition(x, z) },
        onSelectLocal = { local -> viewModel.openChatWithLocal(local) },
        onSelectProp = { prop ->
          viewModel.openIllustrationSheetFor(
            subject = "${prop.label} (${prop.targetWord}) on ${city.streetName}",
            phraseContext = prop.targetWord,
          )
        },
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      StreetCanvas3D(
        city = city,
        playerX = worldState.playerX,
        playerZ = worldState.playerZ,
        playerYawRad = worldState.playerYawRad,
        playerPitchRad = worldState.playerPitchRad,
        completedNpcIds = profile.completedNpcIds.toSet(),
        nearbyLocalId = worldState.nearbyLocal?.id,
        onRotateCamera = { dyaw, dpitch -> viewModel.rotateCamera(dyaw, dpitch) },
        onSelectLocal = { local -> viewModel.openChatWithLocal(local) },
        onSelectProp = { prop ->
          viewModel.openIllustrationSheetFor(
            subject = "${prop.label} (${prop.targetWord}) on ${city.streetName}",
            phraseContext = prop.targetWord,
          )
        },
        modifier = Modifier.fillMaxSize(),
      )
    }

    // Top HUD Overlay
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f, fill = false),
        ) {
          // City & XP badge
          Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 4.dp,
          ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
              Text(
                text = "${city.name} · ${city.streetName}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Bolt,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.tertiary,
                  modifier = Modifier.size(15.dp),
                )
                Text(
                  text = "${profile.xp} XP · ${city.languageName}",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }

          val practicedToday = StreakCalculator.isPracticedToday(profile.lastPracticeDate)
          Surface(
            onClick = { viewModel.openStreakDialog() },
            shape = RoundedCornerShape(18.dp),
            color = if (practicedToday) {
              MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.96f)
            } else {
              MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            },
            tonalElevation = 4.dp,
            modifier = Modifier.testTag("daily_streak_pill"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.LocalFireDepartment,
                contentDescription = "Daily practice streak",
                tint = if (practicedToday) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = "${profile.streakDays}d",
                style = MaterialTheme.typography.titleMedium,
                color = if (practicedToday) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }

        // 2D / 3D Switcher
        SingleChoiceSegmentedButtonRow(
          modifier = Modifier.testTag("view_dimension_toggle"),
        ) {
          SegmentedButton(
            selected = worldState.viewDimension == ViewDimension.MODE_2D,
            onClick = { viewModel.toggleViewDimension(ViewDimension.MODE_2D) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = {
              Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            label = { Text(stringResource(R.string.mode_2d)) },
            modifier = Modifier.testTag("toggle_2d_button"),
          )
          SegmentedButton(
            selected = worldState.viewDimension == ViewDimension.MODE_3D,
            onClick = { viewModel.toggleViewDimension(ViewDimension.MODE_3D) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = {
              Icon(Icons.Rounded.ViewInAr, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            label = { Text(stringResource(R.string.mode_3d)) },
            modifier = Modifier.testTag("toggle_3d_button"),
          )
        }
      }

      // City switcher + quick jump local chips
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 8.dp),
      ) {
        items(CityCatalog.cities, key = { it.id }) { district ->
          val selected = district.id == city.id
          FilterChip(
            selected = selected,
            onClick = { viewModel.selectCity(district.id) },
            label = { Text("${district.name} (${district.languageName})") },
            colors = FilterChipDefaults.filterChipColors(
              containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            ),
            modifier = Modifier.testTag("city_chip_${district.id}"),
          )
        }
      }

      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(city.locals, key = { it.id }) { local ->
          val done = profile.completedNpcIds.contains(local.id)
          val isNear = worldState.nearbyLocal?.id == local.id
          FilterChip(
            selected = isNear,
            onClick = { viewModel.focusLocalOnStreet(local) },
            label = { Text(local.name) },
            leadingIcon = if (done) {
              {
                Icon(
                  Icons.Rounded.CheckCircle,
                  contentDescription = "Quest completed",
                  modifier = Modifier.size(16.dp),
                )
              }
            } else null,
            colors = FilterChipDefaults.filterChipColors(
              containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            ),
            modifier = Modifier.testTag("jump_local_${local.id}"),
          )
        }
      }
    }

    // Bottom HUD: Directional Walk Pad + Nearby Local Prompt Card
    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        // Tactile Walk D-Pad (works smoothly in both 2D and 3D!)
        StreetMovementPad(
          viewDimension = worldState.viewDimension,
          onStepForward = {
            if (worldState.viewDimension == ViewDimension.MODE_3D) {
              viewModel.movePlayerBy(forwardDelta = 1.6f, strafeDelta = 0f)
            } else {
              viewModel.movePlayer2D(dx = 0f, dz = 1.6f)
            }
          },
          onStepBackward = {
            if (worldState.viewDimension == ViewDimension.MODE_3D) {
              viewModel.movePlayerBy(forwardDelta = -1.6f, strafeDelta = 0f)
            } else {
              viewModel.movePlayer2D(dx = 0f, dz = -1.6f)
            }
          },
          onStepLeft = {
            if (worldState.viewDimension == ViewDimension.MODE_3D) {
              viewModel.movePlayerBy(forwardDelta = 0f, strafeDelta = -1.2f)
            } else {
              viewModel.movePlayer2D(dx = -1.2f, dz = 0f)
            }
          },
          onStepRight = {
            if (worldState.viewDimension == ViewDimension.MODE_3D) {
              viewModel.movePlayerBy(forwardDelta = 0f, strafeDelta = 1.2f)
            } else {
              viewModel.movePlayer2D(dx = 1.2f, dz = 0f)
            }
          },
        )

        // Voice Practice + Instant Scene Illustrate quick action pills
        Column(
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Surface(
            onClick = {
              val near = worldState.nearbyLocal
              if (near != null) {
                viewModel.openVoicePracticeForTarget(
                  cityId = city.id,
                  npcName = near.name,
                  targetPhrase = near.localGreeting,
                  phonetic = near.greetingPhonetic,
                  englishMeaning = near.greetingEnglish,
                )
              } else {
                viewModel.openVoicePracticeForCity(city.id)
              }
            },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 4.dp,
            modifier = Modifier.testTag("street_voice_practice_button"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = stringResource(R.string.voice_practice),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }

          Surface(
            onClick = {
              val near = worldState.nearbyLocal
              if (near != null) {
                viewModel.openIllustrationSheetFor(near.illustrationSubject, near.localGreeting)
              } else {
                viewModel.openIllustrationSheetFor(
                  subject = "Sunlit view of ${city.streetName} in ${city.name} with shop awnings",
                  phraseContext = "${city.name} · ${city.streetName}",
                )
              }
            },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 4.dp,
            modifier = Modifier.testTag("street_instant_illustrate_button"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = stringResource(R.string.instant_illustrate),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
          }
        }
      }

      AnimatedVisibility(visible = worldState.nearbyLocal != null) {
        val local = worldState.nearbyLocal
        if (local != null) {
          NearbyLocalCard(
            city = city,
            local = local,
            isCompleted = profile.completedNpcIds.contains(local.id),
            onChatClick = { viewModel.openChatWithLocal(local) },
          )
        }
      }
    }

    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
    )

    // Chat + Live Streamed Illustration Modal Sheet
    if (worldState.showChatSheet && worldState.activeLocal != null) {
      val activeLocal = worldState.activeLocal
      LocalChatSheet(
        city = city,
        local = activeLocal,
        turns = worldState.chatTurns,
        isChatStreaming = worldState.isChatStreaming,
        chatError = worldState.chatError,
        illustrationState = illustrationState,
        hdPaintState = hdPaintState,
        isQuestCompleted = profile.completedNpcIds.contains(activeLocal.id),
        tts = tts,
        onSendMessage = { msg -> viewModel.sendChatMessage(msg) },
        onIllustrateSubject = { subject, ctx ->
          viewModel.streamIllustration(subject, ctx, openSheet = false)
        },
        onRequestHdPaint = { subject -> viewModel.requestHdStudioPaint(subject) },
        onSaveTurn = { turn -> viewModel.saveTurnToPhrasebook(turn, activeLocal) },
        onSaveIllustrationCard = { viewModel.saveCurrentIllustrationToPhrasebook() },
        onInspectGrammar = { point, turn ->
          viewModel.openContextualGrammar(
            point = point,
            sourceSentence = turn.text,
            cityId = city.id,
            npcName = if (turn.isUser) "You" else activeLocal.name,
            turnNote = turn.grammarNote,
          )
        },
        onShadowTurn = { turn ->
          viewModel.openVoicePracticeForTarget(
            cityId = city.id,
            npcName = activeLocal.name,
            targetPhrase = turn.text,
            phonetic = turn.phonetic,
            englishMeaning = turn.translation,
          )
        },
        onDismiss = { viewModel.closeChatSheet() },
      )
    }

    // Standalone Instant Illustration Sheet (when tapping a street prop or Illustrate button)
    if (worldState.showIllustrationSheet) {
      ModalBottomSheet(
        onDismissRequest = { viewModel.closeIllustrationSheet() },
        containerColor = MaterialTheme.colorScheme.background,
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 18.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "Instant Street Illustration",
              style = MaterialTheme.typography.titleLarge,
            )
            IconButton(onClick = { viewModel.closeIllustrationSheet() }) {
              Icon(Icons.Rounded.Close, contentDescription = "Close illustration")
            }
          }
          StreamedIllustrationPanel(
            illustrationState = illustrationState,
            hdPaintState = hdPaintState,
            onRefresh = {
              viewModel.streamIllustration(
                subject = "Everyday street object on ${city.streetName} in ${city.name}",
                phraseContext = city.name,
                openSheet = true,
              )
            },
            onRequestHdPaint = { subject -> viewModel.requestHdStudioPaint(subject) },
            onSaveCard = { viewModel.saveCurrentIllustrationToPhrasebook() },
          )
        }
      }
    }
  }
}

@Composable
private fun StreetMovementPad(
  viewDimension: ViewDimension,
  onStepForward: () -> Unit,
  onStepBackward: () -> Unit,
  onStepLeft: () -> Unit,
  onStepRight: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
    tonalElevation = 4.dp,
  ) {
    Column(
      modifier = Modifier.padding(6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      IconButton(
        onClick = onStepForward,
        modifier = Modifier.size(44.dp).testTag("walk_forward_button"),
      ) {
        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Walk forward")
      }
      Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onStepLeft,
          modifier = Modifier.size(44.dp).testTag("walk_left_button"),
        ) {
          Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Walk left")
        }
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier = Modifier.size(32.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              text = if (viewDimension == ViewDimension.MODE_3D) "3D" else "2D",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }
        IconButton(
          onClick = onStepRight,
          modifier = Modifier.size(44.dp).testTag("walk_right_button"),
        ) {
          Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Walk right")
        }
      }
      IconButton(
        onClick = onStepBackward,
        modifier = Modifier.size(44.dp).testTag("walk_backward_button"),
      ) {
        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Walk backward")
      }
    }
  }
}

@Composable
private fun NearbyLocalCard(
  city: CityDistrict,
  local: com.example.data.model.StreetLocal,
  isCompleted: Boolean,
  onChatClick: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Text(
          text = "${local.name} · ${local.roleTitle}",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = local.localGreeting,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = if (isCompleted) "Quest complete · Keep practicing ${city.languageName}" else "Quest: ${local.questTitle}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Button(
        onClick = onChatClick,
        modifier = Modifier.testTag("chat_with_local_button"),
      ) {
        Icon(Icons.Rounded.Forum, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.chat_with_local))
      }
    }
  }
}
