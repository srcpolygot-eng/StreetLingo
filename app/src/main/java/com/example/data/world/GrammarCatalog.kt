package com.example.data.world

import com.example.data.model.GrammarCategory
import com.example.data.model.GrammarPoint
import com.example.data.model.StreetDifficulty

object GrammarCatalog {
  val allPoints: List<GrammarPoint> =
    GrammarLessonsData.parisPoints +
      GrammarLessonsData.tokyoPoints +
      GrammarLessonsData.madridPoints

  fun pointsForCity(cityId: String): List<GrammarPoint> =
    allPoints.filter { it.cityId == cityId }

  fun pointById(id: String): GrammarPoint? =
    allPoints.firstOrNull { it.id == id }

  fun filterPoints(
    cityId: String,
    category: GrammarCategory? = null,
    difficulty: StreetDifficulty? = null,
    query: String = "",
  ): List<GrammarPoint> {
    val q = query.trim().lowercase()
    return pointsForCity(cityId).filter { point ->
      val catMatch = category == null || point.category == category
      val diffMatch = difficulty == null || point.difficulty == difficulty
      val queryMatch = q.isEmpty() ||
        point.title.lowercase().contains(q) ||
        point.shortBadge.lowercase().contains(q) ||
        point.explanation.lowercase().contains(q) ||
        point.patternFormula.lowercase().contains(q) ||
        point.dialogueExamples.any {
          it.targetSentence.lowercase().contains(q) ||
            it.englishTranslation.lowercase().contains(q) ||
            it.npcName.lowercase().contains(q)
        }
      catMatch && diffMatch && queryMatch
    }
  }

  /**
   * Matches any dialogue utterance (NPC or learner) against the city's grammar points
   * so contextual grammar chips appear reliably on every turn and saved phrase.
   */
  fun detectGrammarPoints(
    cityId: String,
    text: String,
    maxResults: Int = 3,
  ): List<GrammarPoint> {
    val clean = text.trim()
    if (clean.isEmpty()) return emptyList()
    val lower = clean.lowercase()
    val scored = pointsForCity(cityId).mapNotNull { point ->
      val hits = point.detectionKeywords.count { keyword ->
        lower.contains(keyword.lowercase())
      }
      if (hits > 0) point to hits else null
    }.sortedByDescending { it.second }
    return scored.take(maxResults).map { it.first }
  }
}
