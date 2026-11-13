package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.model.CityDistrict
import com.example.data.model.StreakCalculator
import com.example.data.model.StreetDifficulty
import com.example.data.model.UserProfile
import com.example.data.world.CityCatalog
import com.example.firebase.signOut

private val GEMINI_MODELS = listOf(
  "gemini-4-hydrogen" to "Gemini 4 Hydrogen (Default)",
  "gemini-3.8-flash" to "Gemini 3.8 Flash",
  "gemini-3.5-flash-lite" to "Gemini 3.5 Flash-Lite",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PassportScreen(
  profile: UserProfile,
  savedPhraseCount: Int,
  onSelectCity: (String) -> Unit,
  onSelectDifficulty: (StreetDifficulty) -> Unit,
  onSelectModel: (String) -> Unit,
  onOpenStreakDialog: () -> Unit,
  onOpenVoicePractice: () -> Unit,
  onSignedOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .statusBarsPadding(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = "${profile.displayName}'s Passport",
          style = MaterialTheme.typography.headlineLarge,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = "Street quests, language level & Gemini 4 Hydrogen studio settings",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    // XP & Streak Summary
    item {
      val practicedToday = StreakCalculator.isPracticedToday(profile.lastPracticeDate)
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          StatCard(
            label = "Total XP",
            value = "${profile.xp}",
            icon = Icons.Rounded.Bolt,
            modifier = Modifier.weight(1f),
          )
          StatCard(
            label = if (practicedToday) "Practiced Today" else "Daily Streak",
            value = "${profile.streakDays}d",
            icon = Icons.Rounded.LocalFireDepartment,
            onClick = onOpenStreakDialog,
            modifier = Modifier
              .weight(1f)
              .testTag("passport_streak_card"),
          )
          StatCard(
            label = "Saved Cards",
            value = "$savedPhraseCount",
            icon = Icons.Rounded.AutoAwesome,
            modifier = Modifier.weight(1f),
          )
        }

        Card(
          shape = RoundedCornerShape(22.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
          modifier = Modifier.fillMaxWidth().testTag("passport_voice_practice_card"),
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
              verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              Text(
                text = "Voice Speaking Studio",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = if (practicedToday) {
                  "Daily streak active! Drill pronunciation for extra XP."
                } else {
                  "Practice 1 spoken phrase today to keep your daily streak alive."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Button(
              onClick = onOpenVoicePractice,
              modifier = Modifier.testTag("passport_voice_practice_button"),
            ) {
              Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Speak")
            }
          }
        }
      }
    }

    // Difficulty Selector
    item {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "Conversation Level",
            style = MaterialTheme.typography.titleMedium,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            StreetDifficulty.entries.forEach { level ->
              FilterChip(
                selected = profile.difficulty == level.id,
                onClick = { onSelectDifficulty(level) },
                label = { Text("${level.label} (${level.cefr})") },
                modifier = Modifier.testTag("difficulty_chip_${level.id}"),
              )
            }
          }
        }
      }
    }

    // AI Model Selector
    item {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "Streaming AI Model",
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = "Powers instant stroke-by-stroke illustrations and live local street dialogues.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            GEMINI_MODELS.forEach { (id, label) ->
              FilterChip(
                selected = profile.preferredModel == id,
                onClick = { onSelectModel(id) },
                label = { Text(label) },
                modifier = Modifier.testTag("model_chip_$id"),
              )
            }
          }
        }
      }
    }

    item {
      Text(
        text = "City Neighborhoods",
        style = MaterialTheme.typography.titleLarge,
      )
    }

    items(CityCatalog.cities, key = { it.id }) { city ->
      CityPassportCard(
        city = city,
        completedIds = profile.completedNpcIds.toSet(),
        isActive = profile.activeCityId == city.id,
        onExplore = { onSelectCity(city.id) },
      )
    }

    item {
      OutlinedButton(
        onClick = {
          signOut(
            credentialManager = credentialManager,
            scope = scope,
            onSignOutComplete = onSignedOut,
          )
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("sign_out_button"),
      ) {
        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.sign_out))
      }
    }
  }
}

@Composable
private fun StatCard(
  label: String,
  value: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
) {
  val cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  val cardShape = RoundedCornerShape(18.dp)
  val cardContent: @Composable () -> Unit = {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.size(20.dp),
      )
      Text(
        text = value,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
  if (onClick != null) {
    Card(
      onClick = onClick,
      modifier = modifier,
      shape = cardShape,
      colors = cardColors,
      content = { cardContent() },
    )
  } else {
    Card(
      modifier = modifier,
      shape = cardShape,
      colors = cardColors,
      content = { cardContent() },
    )
  }
}

@Composable
private fun CityPassportCard(
  city: CityDistrict,
  completedIds: Set<String>,
  isActive: Boolean,
  onExplore: () -> Unit,
) {
  val completedCount = city.locals.count { completedIds.contains(it.id) }
  val total = city.locals.size.coerceAtLeast(1)
  val drawableRes = when (city.id) {
    "tokyo" -> R.drawable.img_city_tokyo
    "madrid" -> R.drawable.img_city_madrid
    else -> R.drawable.img_city_paris
  }

  Card(
    onClick = onExplore,
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("passport_city_${city.id}"),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Image(
        painter = painterResource(id = drawableRes),
        contentDescription = "${city.name} ${city.streetName}",
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .fillMaxWidth()
          .height(125.dp)
          .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)),
      )
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(
              text = "${city.name} · ${city.languageName}",
              style = MaterialTheme.typography.titleLarge,
            )
            Text(
              text = city.streetName,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (isActive) {
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = MaterialTheme.colorScheme.primaryContainer,
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  Icons.Rounded.CheckCircle,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = "Active",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
            }
          }
        }
        LinearProgressIndicator(
          progress = { completedCount.toFloat() / total.toFloat() },
          modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(999.dp)),
        )
        Text(
          text = "$completedCount / $total local quests completed",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
