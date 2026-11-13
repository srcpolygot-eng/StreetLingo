package com.example.data.model

import androidx.compose.ui.graphics.Color
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class StreetDifficulty(val id: String, val label: String, val cefr: String) {
  BEGINNER("beginner", "Beginner", "A1–A2"),
  INTERMEDIATE("intermediate", "Intermediate", "B1–B2"),
  ADVANCED("advanced", "Fluent", "C1"),
  ;

  companion object {
    fun fromId(id: String): StreetDifficulty =
      entries.firstOrNull { it.id == id } ?: BEGINNER
  }
}

enum class ViewDimension {
  MODE_2D,
  MODE_3D,
}

data class CityDistrict(
  val id: String,
  val name: String,
  val streetName: String,
  val languageName: String,
  val languageTag: String,
  val heroDrawableName: String,
  val skyTopColor: Color,
  val skyBottomColor: Color,
  val roadColor: Color,
  val sidewalkColor: Color,
  val accentColor: Color,
  val locals: List<StreetLocal>,
  val buildings: List<StreetBuilding>,
  val props: List<StreetProp>,
)

data class StreetLocal(
  val id: String,
  val name: String,
  val roleTitle: String,
  val localGreeting: String,
  val greetingPhonetic: String,
  val greetingEnglish: String,
  val questTitle: String,
  val questPromptHint: String,
  val illustrationSubject: String,
  val worldX: Float, // Street lateral offset (-6f..6f)
  val worldZ: Float, // Street longitudinal position (0f..48f)
  val stallColor: Color,
  val awningColor: Color,
  val outfitColor: Color,
  val skinTone: Color,
  val hairColor: Color,
  val starterReplies: List<String>,
)

data class StreetBuilding(
  val id: String,
  val title: String,
  val worldX: Float,
  val worldZ: Float,
  val width: Float,
  val depth: Float,
  val height: Float,
  val wallColor: Color,
  val roofColor: Color,
  val awningColor: Color,
  val windowGlow: Color,
)

enum class PropKind {
  LAMP,
  TREE,
  CAFE_TABLE,
  FOUNTAIN,
  SIGNPOST,
}

data class StreetProp(
  val id: String,
  val label: String,
  val targetWord: String,
  val worldX: Float,
  val worldZ: Float,
  val kind: PropKind,
)

@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "Traveler",
  val activeCityId: String = "paris",
  val difficulty: String = "beginner",
  val preferredModel: String = "gemini-4-hydrogen",
  val xp: Long = 0L,
  val streakDays: Long = 0L,
  val lastPracticeDate: String = "",
  val completedNpcIds: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SavedPhrase(
  @DocumentId val id: String = "",
  val userId: String = "",
  val cityId: String = "paris",
  val npcName: String = "",
  val targetText: String = "",
  val phonetic: String = "",
  val translation: String = "",
  val culturalNote: String = "",
  val illustrationCommands: List<String> = emptyList(),
  val masteryLevel: Long = 1L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

sealed interface IllustrationPrimitive {
  data class Background(val topHex: String, val bottomHex: String) : IllustrationPrimitive
  data class Circle(
    val cx: Float,
    val cy: Float,
    val r: Float,
    val colorHex: String,
    val filled: Boolean,
  ) : IllustrationPrimitive

  data class Rect(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val rx: Float,
    val colorHex: String,
    val filled: Boolean,
  ) : IllustrationPrimitive

  data class Oval(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val colorHex: String,
    val filled: Boolean,
  ) : IllustrationPrimitive

  data class Line(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val stroke: Float,
    val colorHex: String,
  ) : IllustrationPrimitive

  data class Arc(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val startDeg: Float,
    val sweepDeg: Float,
    val stroke: Float,
    val colorHex: String,
  ) : IllustrationPrimitive

  data class Polygon(
    val points: List<Pair<Float, Float>>,
    val colorHex: String,
    val filled: Boolean,
  ) : IllustrationPrimitive

  data class Label(
    val x: Float,
    val y: Float,
    val text: String,
    val colorHex: String,
  ) : IllustrationPrimitive
}

data class IllustrationCaption(
  val targetPhrase: String = "",
  val phonetic: String = "",
  val englishMeaning: String = "",
  val culturalTip: String = "",
)

data class ParsedIllustration(
  val primitives: List<IllustrationPrimitive> = emptyList(),
  val caption: IllustrationCaption? = null,
  val rawLines: List<String> = emptyList(),
)

object IllustrationDslParser {
  fun parseLines(lines: List<String>): ParsedIllustration {
    val primitives = mutableListOf<IllustrationPrimitive>()
    var caption: IllustrationCaption? = null

    for (raw in lines) {
      val line = raw.trim()
      if (line.isEmpty() || line.startsWith("```")) continue
      val parts = line.split("|").map { it.trim() }
      when (parts.firstOrNull()?.uppercase()) {
        "BG" -> if (parts.size >= 3) {
          primitives += IllustrationPrimitive.Background(parts[1], parts[2])
        }
        "CIRCLE" -> if (parts.size >= 5) {
          val cx = parts[1].toFloatOrNull()?.coerceIn(0f, 1f) ?: continue
          val cy = parts[2].toFloatOrNull()?.coerceIn(0f, 1f) ?: continue
          val r = parts[3].toFloatOrNull()?.coerceIn(0.01f, 0.8f) ?: continue
          val hex = parts[4]
          val filled = parts.getOrNull(5)?.lowercase() != "stroke"
          primitives += IllustrationPrimitive.Circle(cx, cy, r, hex, filled)
        }
        "RECT" -> if (parts.size >= 7) {
          val x = parts[1].toFloatOrNull()?.coerceIn(-0.2f, 1.2f) ?: continue
          val y = parts[2].toFloatOrNull()?.coerceIn(-0.2f, 1.2f) ?: continue
          val w = parts[3].toFloatOrNull()?.coerceIn(0.01f, 1.2f) ?: continue
          val h = parts[4].toFloatOrNull()?.coerceIn(0.01f, 1.2f) ?: continue
          val rx = parts[5].toFloatOrNull()?.coerceIn(0f, 0.5f) ?: 0f
          val hex = parts[6]
          val filled = parts.getOrNull(7)?.lowercase() != "stroke"
          primitives += IllustrationPrimitive.Rect(x, y, w, h, rx, hex, filled)
        }
        "OVAL" -> if (parts.size >= 6) {
          val x = parts[1].toFloatOrNull()?.coerceIn(-0.2f, 1.2f) ?: continue
          val y = parts[2].toFloatOrNull()?.coerceIn(-0.2f, 1.2f) ?: continue
          val w = parts[3].toFloatOrNull()?.coerceIn(0.01f, 1.2f) ?: continue
          val h = parts[4].toFloatOrNull()?.coerceIn(0.01f, 1.2f) ?: continue
          val hex = parts[5]
          val filled = parts.getOrNull(6)?.lowercase() != "stroke"
          primitives += IllustrationPrimitive.Oval(x, y, w, h, hex, filled)
        }
        "LINE" -> if (parts.size >= 7) {
          val x1 = parts[1].toFloatOrNull() ?: continue
          val y1 = parts[2].toFloatOrNull() ?: continue
          val x2 = parts[3].toFloatOrNull() ?: continue
          val y2 = parts[4].toFloatOrNull() ?: continue
          val stroke = parts[5].toFloatOrNull()?.coerceIn(1f, 24f) ?: 4f
          val hex = parts[6]
          primitives += IllustrationPrimitive.Line(x1, y1, x2, y2, stroke, hex)
        }
        "ARC" -> if (parts.size >= 9) {
          val x = parts[1].toFloatOrNull() ?: continue
          val y = parts[2].toFloatOrNull() ?: continue
          val w = parts[3].toFloatOrNull() ?: continue
          val h = parts[4].toFloatOrNull() ?: continue
          val start = parts[5].toFloatOrNull() ?: 0f
          val sweep = parts[6].toFloatOrNull() ?: 180f
          val stroke = parts[7].toFloatOrNull()?.coerceIn(1f, 24f) ?: 4f
          val hex = parts[8]
          primitives += IllustrationPrimitive.Arc(x, y, w, h, start, sweep, stroke, hex)
        }
        "POLY" -> if (parts.size >= 3) {
          val pts = parts[1].split(";").mapNotNull { pair ->
            val xy = pair.split(",")
            if (xy.size == 2) {
              val px = xy[0].trim().toFloatOrNull()
              val py = xy[1].trim().toFloatOrNull()
              if (px != null && py != null) px to py else null
            } else null
          }
          if (pts.size >= 3) {
            val hex = parts[2]
            val filled = parts.getOrNull(3)?.lowercase() != "stroke"
            primitives += IllustrationPrimitive.Polygon(pts, hex, filled)
          }
        }
        "LABEL" -> if (parts.size >= 5) {
          val x = parts[1].toFloatOrNull()?.coerceIn(0.05f, 0.95f) ?: continue
          val y = parts[2].toFloatOrNull()?.coerceIn(0.08f, 0.95f) ?: continue
          val text = parts[3]
          val hex = parts[4]
          primitives += IllustrationPrimitive.Label(x, y, text, hex)
        }
        "CAPTION" -> if (parts.size >= 4) {
          caption = IllustrationCaption(
            targetPhrase = parts[1],
            phonetic = parts[2],
            englishMeaning = parts[3],
            culturalTip = parts.getOrNull(4).orEmpty(),
          )
        }
      }
    }
    return ParsedIllustration(primitives = primitives, caption = caption, rawLines = lines)
  }
}

enum class GrammarCategory(val id: String, val label: String) {
  POLITE_REQUESTS("polite_requests", "Polite Requests"),
  QUESTIONS("questions", "Questions"),
  ARTICLES_PARTICLES("articles_particles", "Articles & Particles"),
  VERBS_DESCRIPTIONS("verbs_descriptions", "Verbs & Tone"),
  NUMBERS_PRICES("numbers_prices", "Prices & Counting"),
}

data class GrammarExample(
  val npcId: String,
  val npcName: String,
  val locationContext: String,
  val targetSentence: String,
  val highlightedSegment: String,
  val phonetic: String,
  val englishTranslation: String,
  val breakdownNote: String,
)

data class GrammarPoint(
  val id: String,
  val cityId: String,
  val languageName: String,
  val category: GrammarCategory,
  val difficulty: StreetDifficulty,
  val title: String,
  val shortBadge: String,
  val patternFormula: String,
  val explanation: String,
  val streetTip: String,
  val dialogueExamples: List<GrammarExample>,
  val commonMistakes: List<String>,
  val detectionKeywords: List<String>,
)

data class ChatTurn(
  val id: String,
  val isUser: Boolean,
  val text: String,
  val phonetic: String = "",
  val translation: String = "",
  val coachTip: String = "",
  val suggestedReplies: List<String> = emptyList(),
  val questCompleted: Boolean = false,
  val isStreaming: Boolean = false,
  val grammarPointIds: List<String> = emptyList(),
  val grammarNote: String = "",
)

