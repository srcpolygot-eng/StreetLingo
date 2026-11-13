package com.example.data.model

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class SpokenWordMatch(
  val text: String,
  val matched: Boolean,
)

data class PronunciationResult(
  val score: Int,
  val wordMatches: List<SpokenWordMatch>,
  val feedbackSummary: String,
)

object PronunciationEvaluator {
  fun evaluate(expected: String, spoken: String): PronunciationResult {
    val cleanExpected = expected.trim()
    val cleanSpoken = spoken.trim()
    if (cleanExpected.isEmpty() || cleanSpoken.isEmpty()) {
      return PronunciationResult(
        score = 0,
        wordMatches = emptyList(),
        feedbackSummary = "Tap the mic and speak the phrase clearly.",
      )
    }

    val isCjk = cleanExpected.any { ch ->
      val block = Character.UnicodeBlock.of(ch)
      block == Character.UnicodeBlock.HIRAGANA ||
        block == Character.UnicodeBlock.KATAKANA ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
    }

    return if (isCjk) {
      evaluateCjk(cleanExpected, cleanSpoken)
    } else {
      evaluateLatin(cleanExpected, cleanSpoken)
    }
  }

  private fun evaluateLatin(expected: String, spoken: String): PronunciationResult {
    val expectedWords = tokenizeWords(expected)
    val spokenNormWords = tokenizeWords(spoken).map { normalizeLatin(it) }.toSet()

    val matches = expectedWords.map { rawWord ->
      val norm = normalizeLatin(rawWord)
      val matched = spokenNormWords.any { sp ->
        sp == norm || (norm.length >= 4 && levenshtein(sp, norm) <= 1)
      }
      SpokenWordMatch(text = rawWord, matched = matched)
    }

    val normExpFull = normalizeLatin(expectedWords.joinToString(" "))
    val normSpokenFull = normalizeLatin(tokenizeWords(spoken).joinToString(" "))
    val charSim = similarityRatio(normExpFull, normSpokenFull)
    val wordRatio = if (matches.isEmpty()) 0f else matches.count { it.matched }.toFloat() / matches.size.toFloat()
    val score = ((wordRatio * 0.6f + charSim * 0.4f) * 100f).roundToInt().coerceIn(0, 100)

    return PronunciationResult(
      score = score,
      wordMatches = matches,
      feedbackSummary = feedbackForScore(score),
    )
  }

  private fun evaluateCjk(expected: String, spoken: String): PronunciationResult {
    val normExp = stripPunctuation(expected).replace(" ", "")
    val normSpoken = stripPunctuation(spoken).replace(" ", "")
    val spokenChars = normSpoken.toSet()

    val matches = normExp.map { ch ->
      SpokenWordMatch(text = ch.toString(), matched = spokenChars.contains(ch))
    }
    val charMatchRatio = if (matches.isEmpty()) 0f else matches.count { it.matched }.toFloat() / matches.size.toFloat()
    val seqSim = similarityRatio(normExp, normSpoken)
    val score = ((charMatchRatio * 0.55f + seqSim * 0.45f) * 100f).roundToInt().coerceIn(0, 100)

    return PronunciationResult(
      score = score,
      wordMatches = matches,
      feedbackSummary = feedbackForScore(score),
    )
  }

  private fun feedbackForScore(score: Int): String = when {
    score >= 90 -> "Spot-on pronunciation! You sounded right at home on the street."
    score >= 70 -> "Great clarity! A local would understand you effortlessly."
    score >= 45 -> "Good effort — listen at 0.72x speed and retry the highlighted words."
    else -> "Keep going! Listen slowly first, then speak close to the microphone."
  }

  private fun tokenizeWords(text: String): List<String> =
    text.split(Regex("[\\s'’.,!?;:¡¿«»\"()—\\-]+"))
      .map { it.trim() }
      .filter { it.isNotEmpty() }

  private fun stripPunctuation(text: String): String =
    text.replace(Regex("[\\p{P}\\p{S}]"), "")

  private fun normalizeLatin(text: String): String {
    val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
    return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
      .replace(Regex("[^a-z0-9 ]"), "")
      .trim()
  }

  private fun similarityRatio(a: String, b: String): Float {
    val maxLen = max(a.length, b.length)
    if (maxLen == 0) return 1f
    val dist = levenshtein(a, b)
    return ((maxLen - dist).toFloat() / maxLen.toFloat()).coerceIn(0f, 1f)
  }

  private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var prev = IntArray(b.length + 1) { it }
    var curr = IntArray(b.length + 1)
    for (i in 1..a.length) {
      curr[0] = i
      for (j in 1..b.length) {
        val cost = if (a[i - 1] == b[j - 1]) 0 else 1
        curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
      }
      val tmp = prev
      prev = curr
      curr = tmp
    }
    return prev[b.length]
  }
}
