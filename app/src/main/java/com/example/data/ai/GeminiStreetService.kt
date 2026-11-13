package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.ChatTurn
import com.example.data.model.CityDistrict
import com.example.data.model.GrammarPoint
import com.example.data.model.IllustrationDslParser
import com.example.data.model.ParsedIllustration
import com.example.data.model.StreetDifficulty
import com.example.data.model.StreetLocal
import com.example.data.world.GrammarCatalog
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "GeminiStreetService"
private const val AI_TIMEOUT_MS = 60_000L

data class StreamChatUpdate(
  val replyText: String,
  val phonetic: String = "",
  val translation: String = "",
  val coachTip: String = "",
  val suggestedReplies: List<String> = emptyList(),
  val questCompleted: Boolean = false,
  val grammarPointIds: List<String> = emptyList(),
  val grammarNote: String = "",
  val isComplete: Boolean = false,
)

class GeminiStreetService {
  private val textModels = ConcurrentHashMap<String, GenerativeModel>()

  private val imageModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-nano-banana-2.1",
      generationConfig = generationConfig {
        responseModalities = listOf(ResponseModality.IMAGE)
      },
    )
  }

  private fun getModel(modelName: String): GenerativeModel {
    val safeName = modelName.ifBlank { "gemini-4-hydrogen" }
    return textModels.getOrPut(safeName) {
      Firebase.ai.generativeModel(
        modelName = safeName,
        generationConfig = generationConfig {
          temperature = 0.7f
        },
      )
    }
  }

  fun startNpcChat(
    modelName: String,
    city: CityDistrict,
    local: StreetLocal,
    difficulty: StreetDifficulty,
    historyTurns: List<ChatTurn>,
  ): Chat {
    val validGrammarIds = GrammarCatalog.pointsForCity(city.id)
      .joinToString(", ") { "${it.id} (${it.shortBadge})" }
    val systemInstruction = buildString {
      appendLine("You are ${local.name}, a friendly ${local.roleTitle} on ${city.streetName} in ${city.name}.")
      appendLine("Speak naturally in ${city.languageName} matched to a ${difficulty.label} (${difficulty.cefr}) learner.")
      appendLine("Your street quest for the learner: ${local.questTitle}.")
      appendLine("Respond using this clean line-based streaming format so the UI updates live:")
      appendLine("First output the natural ${city.languageName} reply directly on the first paragraph (1 to 3 warm, vivid sentences).")
      appendLine("Then on separate lines at the end output:")
      appendLine("PHONETIC: <pronunciation guide for your reply>")
      appendLine("ENGLISH: <natural English translation>")
      appendLine("TIP: <one short encouraging grammar or cultural note>")
      appendLine("GRAMMAR: <1 or 2 matching IDs from [$validGrammarIds], or 'none'> | <one short sentence showing how that grammar works in your reply>")
      appendLine("SUGGEST: <reply 1 in ${city.languageName}> | <reply 2 in ${city.languageName}> | <reply 3 in ${city.languageName}>")
      appendLine("QUEST_DONE: true or false (true once the learner fulfills '${local.questTitle}')")
    }

    val model = Firebase.ai.generativeModel(
      modelName = modelName.ifBlank { "gemini-4-hydrogen" },
      generationConfig = generationConfig {
        temperature = 0.7f
      },
      systemInstruction = content { text(systemInstruction) },
    )

    val history = mutableListOf<Content>()
    for (turn in historyTurns) {
      if (turn.text.isBlank()) continue
      history += content(if (turn.isUser) "user" else "model") {
        text(turn.text)
      }
    }
    return model.startChat(history = history)
  }

  fun streamLocalReply(
    chat: Chat,
    userMessage: String,
  ): Flow<StreamChatUpdate> = flow {
    val buffer = StringBuilder()
    try {
      withTimeout(AI_TIMEOUT_MS) {
        chat.sendMessageStream(userMessage).collect { chunk ->
          val piece = chunk.text.orEmpty()
          if (piece.isNotEmpty()) {
            buffer.append(piece)
            emit(parseStreamingReply(buffer.toString(), isComplete = false))
          }
        }
      }
      val finalText = buffer.toString().trim()
      if (finalText.isBlank()) {
        throw IllegalStateException("Model returned an empty response.")
      }
      emit(parseStreamingReply(finalText, isComplete = true))
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Chat streaming timed out", e)
      throw IllegalStateException("Response timed out after 60 seconds. Please try again.", e)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Chat streaming failed", e)
      throw e
    }
  }

  private fun parseStreamingReply(raw: String, isComplete: Boolean): StreamChatUpdate {
    val lines = raw.lines()
    val replyLines = mutableListOf<String>()
    var phonetic = ""
    var translation = ""
    var tip = ""
    var suggestions = emptyList<String>()
    var questDone = false
    var grammarIds = emptyList<String>()
    var grammarNote = ""

    for (line in lines) {
      val trimmed = line.trim()
      when {
        trimmed.startsWith("PHONETIC:", ignoreCase = true) ->
          phonetic = trimmed.substringAfter(":").trim()
        trimmed.startsWith("ENGLISH:", ignoreCase = true) ->
          translation = trimmed.substringAfter(":").trim()
        trimmed.startsWith("TIP:", ignoreCase = true) ->
          tip = trimmed.substringAfter(":").trim()
        trimmed.startsWith("GRAMMAR:", ignoreCase = true) -> {
          val payload = trimmed.substringAfter(":").trim()
          val parts = payload.split("|", limit = 2)
          grammarIds = parts.firstOrNull().orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals("none", ignoreCase = true) && GrammarCatalog.pointById(it) != null }
            .distinct()
          grammarNote = parts.getOrNull(1)?.trim().orEmpty()
        }
        trimmed.startsWith("SUGGEST:", ignoreCase = true) ->
          suggestions = trimmed.substringAfter(":")
            .split("|")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(3)
        trimmed.startsWith("QUEST_DONE:", ignoreCase = true) ->
          questDone = trimmed.substringAfter(":").trim().equals("true", ignoreCase = true)
        else -> {
          // Avoid leaking partial metadata tag prefixes mid-stream
          val upper = trimmed.uppercase()
          val isPartialTag = listOf("PHONETIC", "ENGLISH", "TIP", "GRAMMAR", "SUGGEST", "QUEST_DONE")
            .any { tag -> tag.startsWith(upper) && upper.length >= 3 }
          if (!isPartialTag) {
            replyLines += line
          }
        }
      }
    }

    return StreamChatUpdate(
      replyText = replyLines.joinToString("\n").trim(),
      phonetic = phonetic,
      translation = translation,
      coachTip = tip,
      suggestedReplies = suggestions,
      questCompleted = questDone,
      grammarPointIds = grammarIds,
      grammarNote = grammarNote,
      isComplete = isComplete,
    )
  }

  fun streamInstantIllustration(
    modelName: String,
    city: CityDistrict,
    subject: String,
    phraseContext: String,
  ): Flow<ParsedIllustration> = flow {
    val model = getModel(modelName)
    val prompt = buildString {
      appendLine("Create an instant, charming, layered vector scene illustration for a ${city.languageName} language learner in ${city.name} (${city.streetName}).")
      appendLine("Subject: $subject")
      if (phraseContext.isNotBlank()) {
        appendLine("Conversation context: $phraseContext")
      }
      appendLine()
      appendLine("Output ONLY pipe-delimited drawing commands (one command per line, NO markdown fences, NO commentary) so the Android Canvas paints each stroke live while streaming.")
      appendLine("All coordinates x, y, w, h, r, rx are normalized floats between 0.0 and 1.0 (origin top-left).")
      appendLine("Emit 14 to 24 rich visual primitives in back-to-front painter order:")
      appendLine("BG|#TopHex|#BottomHex")
      appendLine("RECT|x|y|w|h|rx|#Hex|filled_or_stroke")
      appendLine("OVAL|x|y|w|h|#Hex|filled_or_stroke")
      appendLine("CIRCLE|cx|cy|r|#Hex|filled_or_stroke")
      appendLine("POLY|x1,y1;x2,y2;x3,y3|#Hex|filled_or_stroke")
      appendLine("ARC|x|y|w|h|startDeg|sweepDeg|strokeWidth|#Hex")
      appendLine("LINE|x1|y1|x2|y2|strokeWidth|#Hex")
      appendLine("LABEL|x|y|short target-language word|#Hex   (include 2 to 3 crisp vocabulary callouts on the scene)")
      appendLine("End with exactly one summary line:")
      appendLine("CAPTION|<key ${city.languageName} phrase>|<phonetic guide>|<English meaning>|<1-sentence street cultural tip>")
    }

    val accumulated = StringBuilder()
    try {
      withTimeout(AI_TIMEOUT_MS) {
        model.generateContentStream(prompt).collect { chunk ->
          val piece = chunk.text.orEmpty()
          if (piece.isNotEmpty()) {
            accumulated.append(piece)
            val completedLines = accumulated.toString().lines().dropLast(1)
            if (completedLines.isNotEmpty()) {
              emit(IllustrationDslParser.parseLines(completedLines))
            }
          }
        }
      }
      val allLines = accumulated.toString().lines().filter { it.isNotBlank() }
      val parsed = IllustrationDslParser.parseLines(allLines)
      if (parsed.primitives.isEmpty()) {
        throw IllegalStateException("Model did not return drawable illustration commands.")
      }
      emit(parsed)
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Illustration streaming timed out", e)
      throw IllegalStateException("Illustration timed out after 60 seconds.", e)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Illustration streaming failed", e)
      throw e
    }
  }

  suspend fun generateHdRasterIllustration(
    city: CityDistrict,
    subject: String,
  ): Bitmap {
    val prompt = "Warm editorial gouache and ink travel sketchbook illustration in ${city.name} (${city.streetName}): $subject. Vibrant terracotta, cobalt blue, and sunlit gold tones, clean composition, no text."
    return try {
      withTimeout(AI_TIMEOUT_MS) {
        val response = imageModel.generateContent(prompt)
        val imagePart = response.candidates.firstOrNull()
          ?.content?.parts?.filterIsInstance<ImagePart>()
          ?.firstOrNull()
        imagePart?.image ?: throw IllegalStateException("No raster image was returned by gemini-nano-banana-2.1.")
      }
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "HD illustration timed out", e)
      throw IllegalStateException("HD Studio Paint timed out after 60 seconds.", e)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "HD illustration failed", e)
      throw e
    }
  }

  fun streamContextualGrammarBreakdown(
    modelName: String,
    city: CityDistrict,
    sentence: String,
    grammarPoint: GrammarPoint?,
  ): Flow<String> = flow {
    val model = getModel(modelName)
    val prompt = buildString {
      appendLine("You are a warm, crystal-clear ${city.languageName} street tutor helping a learner on ${city.streetName} in ${city.name}.")
      appendLine("Analyze this exact dialogue sentence: \"$sentence\"")
      if (grammarPoint != null) {
        appendLine("Focus especially on this grammar concept: ${grammarPoint.title} (${grammarPoint.patternFormula}).")
      }
      appendLine("Provide a concise, well-organized breakdown in plain readable lines (no markdown tables):")
      appendLine("1. Word-by-word or chunk-by-chunk gloss with English meanings.")
      appendLine("2. Why this grammar structure sounds natural in ${city.name}.")
      appendLine("3. One quick 'Try Saying Next' variation the learner can use in the street chat.")
    }
    val buffer = StringBuilder()
    try {
      withTimeout(AI_TIMEOUT_MS) {
        model.generateContentStream(prompt).collect { chunk ->
          val piece = chunk.text.orEmpty()
          if (piece.isNotEmpty()) {
            buffer.append(piece)
            emit(buffer.toString())
          }
        }
      }
      if (buffer.isBlank()) {
        throw IllegalStateException("Model returned an empty grammar breakdown.")
      }
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Contextual grammar breakdown timed out", e)
      throw IllegalStateException("Grammar breakdown timed out after 60 seconds.", e)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Contextual grammar breakdown failed", e)
      throw e
    }
  }

  fun streamMoreStreetExamples(
    modelName: String,
    city: CityDistrict,
    grammarPoint: GrammarPoint,
  ): Flow<String> = flow {
    val model = getModel(modelName)
    val localsList = city.locals.joinToString(", ") { "${it.name} (${it.roleTitle})" }
    val prompt = buildString {
      appendLine("Create 3 fresh, natural ${city.languageName} street dialogue examples for ${city.name} (${city.streetName}) practicing:")
      appendLine("${grammarPoint.title} — ${grammarPoint.patternFormula}")
      appendLine("Place each example at one of these local spots: $localsList.")
      appendLine("For each example, output:")
      appendLine("• <Local Name>: <${city.languageName} sentence>")
      appendLine("  Phonetic: <pronunciation>")
      appendLine("  English: <translation & 1-phrase grammar note>")
    }
    val buffer = StringBuilder()
    try {
      withTimeout(AI_TIMEOUT_MS) {
        model.generateContentStream(prompt).collect { chunk ->
          val piece = chunk.text.orEmpty()
          if (piece.isNotEmpty()) {
            buffer.append(piece)
            emit(buffer.toString())
          }
        }
      }
      if (buffer.isBlank()) {
        throw IllegalStateException("Model returned no extra examples.")
      }
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Grammar examples streaming timed out", e)
      throw IllegalStateException("Grammar examples timed out after 60 seconds.", e)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Grammar examples streaming failed", e)
      throw e
    }
  }
}
