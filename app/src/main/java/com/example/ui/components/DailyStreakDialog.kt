package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.StreakCalculator

@Composable
fun DailyStreakDialog(
  streakDays: Long,
  lastPracticeDate: String,
  onStartVoicePractice: () -> Unit,
  onDismiss: () -> Unit,
) {
  val todayKey = remember { StreakCalculator.todayKey() }
  val practicedToday = remember(lastPracticeDate, todayKey) {
    StreakCalculator.isPracticedToday(lastPracticeDate, todayKey)
  }
  val weekDays = remember { StreakCalculator.recentWeekLabels() }
  val activeDaysToHighlight = if (practicedToday) {
    streakDays.coerceIn(1L, 7L).toInt()
  } else {
    streakDays.coerceIn(0L, 6L).toInt()
  }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(26.dp),
      color = MaterialTheme.colorScheme.background,
      tonalElevation = 6.dp,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Box(
              modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(
                  if (practicedToday) MaterialTheme.colorScheme.secondaryContainer
                  else MaterialTheme.colorScheme.surfaceVariant,
                ),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Rounded.LocalFireDepartment,
                contentDescription = null,
                tint = if (practicedToday) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
              )
            }
            Column {
              Text(
                text = if (streakDays == 1L) "1 Day Streak" else "$streakDays Days Streak",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
              )
              Text(
                text = if (practicedToday) {
                  "Completed today's street practice"
                } else {
                  "Chat or speak 1 phrase today to keep your streak"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (practicedToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(48.dp).testTag("close_streak_dialog_button"),
          ) {
            Icon(Icons.Rounded.Close, contentDescription = "Close streak dialog")
          }
        }

        // Rolling 7-day calendar strip
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = MaterialTheme.colorScheme.surface,
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
          ) {
            weekDays.forEachIndexed { index, (dateKey, dayInitial) ->
              val isToday = dateKey == todayKey
              val filled = if (practicedToday) {
                index >= 7 - activeDaysToHighlight
              } else {
                index in (6 - activeDaysToHighlight) until 6
              }
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                  text = dayInitial,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                  color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                  modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                      when {
                        filled -> MaterialTheme.colorScheme.secondary
                        isToday -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                      }
                    ),
                  contentAlignment = Alignment.Center,
                ) {
                  if (filled) {
                    Icon(
                      imageVector = Icons.Rounded.CheckCircle,
                      contentDescription = "Practiced",
                      tint = MaterialTheme.colorScheme.onSecondary,
                      modifier = Modifier.size(18.dp),
                    )
                  } else if (isToday) {
                    Icon(
                      imageVector = Icons.Rounded.LocalFireDepartment,
                      contentDescription = "Today",
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(16.dp),
                    )
                  }
                }
              }
            }
          }
        }

        Button(
          onClick = {
            onDismiss()
            onStartVoicePractice()
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("streak_dialog_voice_practice_button"),
        ) {
          Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(if (practicedToday) "Practice Speaking (+20 XP)" else "Extend Streak with Voice Practice")
        }
      }
    }
  }
}
