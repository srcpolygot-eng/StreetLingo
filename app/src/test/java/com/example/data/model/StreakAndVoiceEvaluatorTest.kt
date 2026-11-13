package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakAndVoiceEvaluatorTest {

  @Test
  fun computeStreakUpdate_firstPractice_startsAtOneDay() {
    val result = StreakCalculator.computeStreakUpdate(
      currentStreak = 0L,
      lastPracticeDate = "",
      todayDate = "2026-11-12",
    )
    assertEquals(1L, result.newStreak)
    assertEquals("2026-11-12", result.lastPracticeDate)
    assertTrue(result.didIncrement)
  }

  @Test
  fun computeStreakUpdate_sameDay_isIdempotent() {
    val result = StreakCalculator.computeStreakUpdate(
      currentStreak = 5L,
      lastPracticeDate = "2026-11-12",
      todayDate = "2026-11-12",
    )
    assertEquals(5L, result.newStreak)
    assertEquals("2026-11-12", result.lastPracticeDate)
    assertFalse(result.didIncrement)
  }

  @Test
  fun computeStreakUpdate_consecutiveDay_incrementsStreak() {
    val result = StreakCalculator.computeStreakUpdate(
      currentStreak = 4L,
      lastPracticeDate = "2026-11-11",
      todayDate = "2026-11-12",
    )
    assertEquals(5L, result.newStreak)
    assertEquals("2026-11-12", result.lastPracticeDate)
    assertTrue(result.didIncrement)
  }

  @Test
  fun computeStreakUpdate_missedDayGap_resetsToOne() {
    val result = StreakCalculator.computeStreakUpdate(
      currentStreak = 9L,
      lastPracticeDate = "2026-11-09",
      todayDate = "2026-11-12",
    )
    assertEquals(1L, result.newStreak)
    assertEquals("2026-11-12", result.lastPracticeDate)
    assertTrue(result.didIncrement)
  }

  @Test
  fun recentWeekLabels_returnsSevenChronologicalDays() {
    val days = StreakCalculator.recentWeekLabels()
    assertEquals(7, days.size)
    assertEquals(StreakCalculator.todayKey(), days.last().first)
  }

  @Test
  fun pronunciationEvaluator_latinDiacriticsAndExactMatch_scoresHigh() {
    val result = PronunciationEvaluator.evaluate(
      expected = "Bonjour ! Un café crème, s'il vous plaît.",
      spoken = "bonjour un cafe creme s il vous plait",
    )
    assertTrue("Expected >= 90 score, got ${result.score}", result.score >= 90)
    assertTrue(result.wordMatches.all { it.matched })
  }

  @Test
  fun pronunciationEvaluator_cjkJapaneseMatch_scoresAccurately() {
    val result = PronunciationEvaluator.evaluate(
      expected = "ラーメンをお願いします",
      spoken = "ラーメンをお願いします",
    )
    assertEquals(100, result.score)
    assertTrue(result.wordMatches.isNotEmpty())
  }

  @Test
  fun pronunciationEvaluator_partialLatinMatch_identifiesMissedWords() {
    val result = PronunciationEvaluator.evaluate(
      expected = "Un croissant chaud, merci",
      spoken = "bonjour baguette merci",
    )
    assertTrue(result.score in 1..70)
    assertTrue(result.wordMatches.any { !it.matched })
    assertTrue(result.wordMatches.any { it.text.equals("merci", ignoreCase = true) && it.matched })
  }
}
