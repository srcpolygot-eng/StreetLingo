package com.example.data.world

import com.example.data.model.GrammarCategory
import com.example.data.model.StreetDifficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrammarCatalogTest {

  @Test
  fun allCitiesHaveRichGrammarPointsAndValidNpcExamples() {
    for (city in CityCatalog.cities) {
      val points = GrammarCatalog.pointsForCity(city.id)
      assertTrue("${city.id} should have at least 6 grammar points", points.size >= 6)

      val npcIds = city.locals.map { it.id }.toSet()
      for (point in points) {
        assertEquals(city.id, point.cityId)
        assertTrue("${point.id} should have dialogue examples", point.dialogueExamples.isNotEmpty())
        for (ex in point.dialogueExamples) {
          assertTrue(
            "Example npcId ${ex.npcId} should belong to ${city.id}",
            npcIds.contains(ex.npcId),
          )
          assertTrue(
            "Highlighted segment '${ex.highlightedSegment}' should appear in '${ex.targetSentence}'",
            ex.targetSentence.contains(ex.highlightedSegment, ignoreCase = true),
          )
        }
      }
    }
  }

  @Test
  fun detectGrammarPointsFindsConceptsInCityDialogues() {
    val parisMatch = GrammarCatalog.detectGrammarPoints("paris", "C'est combien pour un pain au chocolat ?")
    assertTrue(parisMatch.any { it.id == "fr_cest_combien" })

    val tokyoMatch = GrammarCatalog.detectGrammarPoints("tokyo", "醤油ラーメンをひとつお願いします。")
    assertTrue(tokyoMatch.any { it.id == "ja_onegaishimasu_kudasai" })

    val madridMatch = GrammarCatalog.detectGrammarPoints("madrid", "¿Te apetecen unos churros crujientes con chocolate?")
    assertTrue(madridMatch.any { it.id == "es_gustar_apetecer" })
  }

  @Test
  fun filterPointsSupportsCategoryDifficultyAndQuery() {
    val parisQuestions = GrammarCatalog.filterPoints(
      cityId = "paris",
      category = GrammarCategory.QUESTIONS,
    )
    assertFalse(parisQuestions.isEmpty())
    assertTrue(parisQuestions.all { it.category == GrammarCategory.QUESTIONS })

    val tokyoIntermediate = GrammarCatalog.filterPoints(
      cityId = "tokyo",
      difficulty = StreetDifficulty.INTERMEDIATE,
    )
    assertFalse(tokyoIntermediate.isEmpty())
    assertTrue(tokyoIntermediate.all { it.difficulty == StreetDifficulty.INTERMEDIATE })

    val madridChef = GrammarCatalog.filterPoints(
      cityId = "madrid",
      query = "Carlos",
    )
    assertFalse(madridChef.isEmpty())
    assertNotNull(GrammarCatalog.pointById(madridChef.first().id))
  }
}
