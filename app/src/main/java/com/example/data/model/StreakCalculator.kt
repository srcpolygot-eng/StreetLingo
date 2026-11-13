package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToLong

data class StreakUpdate(
  val newStreak: Long,
  val lastPracticeDate: String,
  val didIncrement: Boolean,
)

object StreakCalculator {
  private const val DATE_PATTERN = "yyyy-MM-dd"
  private const val DAY_MS = 86_400_000L

  fun todayKey(
    nowMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
  ): String {
    val formatter = SimpleDateFormat(DATE_PATTERN, Locale.US).apply {
      this.timeZone = timeZone
    }
    return formatter.format(Date(nowMillis))
  }

  fun isPracticedToday(
    lastPracticeDate: String,
    todayDate: String = todayKey(),
  ): Boolean = lastPracticeDate.isNotBlank() && lastPracticeDate == todayDate

  fun computeStreakUpdate(
    currentStreak: Long,
    lastPracticeDate: String,
    todayDate: String = todayKey(),
  ): StreakUpdate {
    val cleanLast = lastPracticeDate.trim()
    if (cleanLast == todayDate) {
      return StreakUpdate(
        newStreak = currentStreak.coerceAtLeast(1L),
        lastPracticeDate = todayDate,
        didIncrement = false,
      )
    }

    if (cleanLast.isEmpty()) {
      val next = if (currentStreak <= 0L) 1L else currentStreak
      return StreakUpdate(
        newStreak = next,
        lastPracticeDate = todayDate,
        didIncrement = currentStreak <= 0L,
      )
    }

    val diffDays = daysBetween(cleanLast, todayDate)
    return when {
      diffDays == 1L -> StreakUpdate(
        newStreak = currentStreak.coerceAtLeast(0L) + 1L,
        lastPracticeDate = todayDate,
        didIncrement = true,
      )
      diffDays == 0L -> StreakUpdate(
        newStreak = currentStreak.coerceAtLeast(1L),
        lastPracticeDate = todayDate,
        didIncrement = false,
      )
      else -> StreakUpdate(
        newStreak = 1L,
        lastPracticeDate = todayDate,
        didIncrement = true,
      )
    }
  }

  fun recentWeekLabels(
    nowMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
  ): List<Pair<String, String>> {
    val cal = Calendar.getInstance(timeZone, Locale.US).apply {
      timeInMillis = nowMillis
      add(Calendar.DAY_OF_YEAR, -6)
    }
    val keyFmt = SimpleDateFormat(DATE_PATTERN, Locale.US).apply { this.timeZone = timeZone }
    val dayFmt = SimpleDateFormat("EEE", Locale.US).apply { this.timeZone = timeZone }
    return (0 until 7).map {
      val key = keyFmt.format(cal.time)
      val label = dayFmt.format(cal.time).take(1)
      cal.add(Calendar.DAY_OF_YEAR, 1)
      key to label
    }
  }

  private fun daysBetween(startDate: String, endDate: String): Long {
    val startMs = parseUtcMidnightMillis(startDate) ?: return -1L
    val endMs = parseUtcMidnightMillis(endDate) ?: return -1L
    return ((endMs - startMs).toDouble() / DAY_MS.toDouble()).roundToLong()
  }

  private fun parseUtcMidnightMillis(dateStr: String): Long? {
    return try {
      val fmt = SimpleDateFormat(DATE_PATTERN, Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
      }
      fmt.parse(dateStr)?.time
    } catch (_: Exception) {
      null
    }
  }
}
