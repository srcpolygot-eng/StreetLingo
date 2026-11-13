package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ai.GeminiStreetService
import com.example.data.model.ChatTurn
import com.example.data.model.CityDistrict
import com.example.data.model.GrammarPoint
import com.example.data.model.ParsedIllustration
import com.example.data.model.PronunciationEvaluator
import com.example.data.model.PronunciationResult
import com.example.data.model.SavedPhrase
import com.example.data.model.StreetDifficulty
import com.example.data.model.StreetLocal
import com.example.data.model.UserProfile
import com.example.data.model.ViewDimension
import com.example.data.repository.StreetLingoRepository
import com.example.data.world.CityCatalog
import com.example.data.world.GrammarCatalog
import com.google.firebase.ai.Chat
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "StreetLingoVM"

sealed interface DataUiState<out T> {
  data object Loading : DataUiState<Nothing>
  data class Success<T>(val data: T) : DataUiState<T>
  data class Error(val message: String) : DataUiState<Nothing>
}

sealed interface IllustrationUiState {
  data object Idle : IllustrationUiState
  data class Streaming(
    val parsed: ParsedIllustration,
    val subject: String,
    val modelName: String,
  ) : IllustrationUiState

  data class Success(
    val parsed: ParsedIllustration,
    val subject: String,
    val modelName: String,
  ) : IllustrationUiState

  data class Error(val message: String) : IllustrationUiState
}

sealed interface HdPaintUiState {
  data object Idle : HdPaintUiState
  data object Loading : HdPaintUiState
  data class Success(val bitmap: Bitmap) : HdPaintUiState
  data class Error(val message: String) : HdPaintUiState
}

sealed interface GrammarAiUiState {
  data object Idle : GrammarAiUiState
  data class Streaming(val text: String) : GrammarAiUiState
  data class Success(val text: String) : GrammarAiUiState
  data class Error(val message: String) : GrammarAiUiState
}

data class ContextualGrammarState(
  val point: GrammarPoint?,
  val sourceSentence: String,
  val cityId: String,
  val npcName: String? = null,
  val turnNote: String = "",
)

data class VoicePracticePrompt(
  val npcName: String,
  val targetPhrase: String,
  val phonetic: String = "",
  val englishMeaning: String = "",
)

data class VoicePracticeState(
  val cityId: String,
  val prompts: List<VoicePracticePrompt>,
  val promptIndex: Int = 0,
  val lastSpokenTranscript: String = "",
  val evaluation: PronunciationResult? = null,
) {
  val currentPrompt: VoicePracticePrompt
    get() = prompts.getOrElse(promptIndex) {
      VoicePracticePrompt(npcName = "", targetPhrase = "")
    }
  val npcName: String get() = currentPrompt.npcName
  val targetPhrase: String get() = currentPrompt.targetPhrase
  val phonetic: String get() = currentPrompt.phonetic
  val englishMeaning: String get() = currentPrompt.englishMeaning
  val hasPlaylist: Boolean get() = prompts.size > 1
  val totalPrompts: Int get() = prompts.size
}

data class StreetWorldUiState(
  val viewDimension: ViewDimension = ViewDimension.MODE_3D,
  val playerX: Float = 0f,
  val playerZ: Float = 3.5f,
  val playerYawRad: Float = 0f, // 0 = looking +Z down the street
  val playerPitchRad: Float = -0.04f,
  val walkTargetX: Float? = null,
  val walkTargetZ: Float? = null,
  val activeLocal: StreetLocal? = null,
  val nearbyLocal: StreetLocal? = null,
  val chatTurns: List<ChatTurn> = emptyList(),
  val isChatStreaming: Boolean = false,
  val chatError: String? = null,
  val showChatSheet: Boolean = false,
  val showIllustrationSheet: Boolean = false,
  val savedToastMessage: String? = null,
)

class StreetLingoViewModel(
  private val repository: StreetLingoRepository,
  private val aiService: GeminiStreetService,
  private val currentUserId: String,
  private val initialDisplayName: String,
) : ViewModel() {

  val profileState: StateFlow<DataUiState<UserProfile>> = repository.observeUserProfile(currentUserId)
    .map<UserProfile?, DataUiState<UserProfile>> { profile ->
      DataUiState.Success(
        profile ?: UserProfile(
          userId = currentUserId,
          displayName = initialDisplayName,
        )
      )
    }
    .catch { error ->
      val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      val rootError = firestoreError ?: error
      Log.w(TAG, "Error observing profile", rootError)
      emit(DataUiState.Error(rootError.message ?: "Failed to load learner profile"))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = DataUiState.Loading,
    )

  val phrasesState: StateFlow<DataUiState<List<SavedPhrase>>> = repository.observePhrases(currentUserId)
    .map<List<SavedPhrase>, DataUiState<List<SavedPhrase>>> { DataUiState.Success(it) }
    .catch { error ->
      val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      val rootError = firestoreError ?: error
      Log.w(TAG, "Error observing phrases", rootError)
      emit(DataUiState.Error(rootError.message ?: "Failed to load phrasebook"))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = DataUiState.Loading,
    )

  private val _worldState = MutableStateFlow(StreetWorldUiState())
  val worldState: StateFlow<StreetWorldUiState> = _worldState.asStateFlow()

  private val _illustrationState = MutableStateFlow<IllustrationUiState>(IllustrationUiState.Idle)
  val illustrationState: StateFlow<IllustrationUiState> = _illustrationState.asStateFlow()

  private val _hdPaintState = MutableStateFlow<HdPaintUiState>(HdPaintUiState.Idle)
  val hdPaintState: StateFlow<HdPaintUiState> = _hdPaintState.asStateFlow()

  private val _contextualGrammar = MutableStateFlow<ContextualGrammarState?>(null)
  val contextualGrammar: StateFlow<ContextualGrammarState?> = _contextualGrammar.asStateFlow()

  private val _contextualGrammarAiState = MutableStateFlow<GrammarAiUiState>(GrammarAiUiState.Idle)
  val contextualGrammarAiState: StateFlow<GrammarAiUiState> = _contextualGrammarAiState.asStateFlow()

  private val _guideExtraExamples = MutableStateFlow<Map<String, GrammarAiUiState>>(emptyMap())
  val guideExtraExamples: StateFlow<Map<String, GrammarAiUiState>> = _guideExtraExamples.asStateFlow()

  private val _selectedGrammarCityId = MutableStateFlow<String?>(null)
  val selectedGrammarCityId: StateFlow<String?> = _selectedGrammarCityId.asStateFlow()

  private val _focusedGrammarPointId = MutableStateFlow<String?>(null)
  val focusedGrammarPointId: StateFlow<String?> = _focusedGrammarPointId.asStateFlow()

  private val _voicePracticeState = MutableStateFlow<VoicePracticeState?>(null)
  val voicePracticeState: StateFlow<VoicePracticeState?> = _voicePracticeState.asStateFlow()

  private val _voiceCoachAiState = MutableStateFlow<GrammarAiUiState>(GrammarAiUiState.Idle)
  val voiceCoachAiState: StateFlow<GrammarAiUiState> = _voiceCoachAiState.asStateFlow()

  private val _showStreakDialog = MutableStateFlow(false)
  val showStreakDialog: StateFlow<Boolean> = _showStreakDialog.asStateFlow()

  private var activeChatSession: Chat? = null
  private var chatJob: Job? = null
  private var illustrationJob: Job? = null
  private var hdPaintJob: Job? = null
  private var contextualGrammarJob: Job? = null
  private var guideExamplesJob: Job? = null
  private var voiceCoachJob: Job? = null

  init {
    viewModelScope.launch {
      try {
        repository.ensureUserProfile(currentUserId, initialDisplayName)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to ensure user profile", e)
      }
    }
    updateNearbyLocal()
  }

  fun currentProfile(): UserProfile {
    val state = profileState.value
    return if (state is DataUiState.Success) state.data
    else UserProfile(userId = currentUserId, displayName = initialDisplayName)
  }

  fun currentCity(): CityDistrict = CityCatalog.cityById(currentProfile().activeCityId)

  fun toggleViewDimension(mode: ViewDimension) {
    _worldState.update { it.copy(viewDimension = mode) }
  }

  fun selectCity(cityId: String) {
    viewModelScope.launch {
      try {
        repository.updatePreferences(currentUserId, activeCityId = cityId)
        _worldState.update {
          it.copy(
            playerX = 0f,
            playerZ = 3.5f,
            playerYawRad = 0f,
            playerPitchRad = -0.04f,
            walkTargetX = null,
            walkTargetZ = null,
            activeLocal = null,
            showChatSheet = false,
          )
        }
        val city = CityCatalog.cityById(cityId)
        updateNearbyLocalForCity(city)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to select city", e)
      }
    }
  }

  fun selectDifficulty(difficulty: StreetDifficulty) {
    viewModelScope.launch {
      try {
        repository.updatePreferences(currentUserId, difficulty = difficulty.id)
        activeChatSession = null
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to update difficulty", e)
      }
    }
  }

  fun selectModel(modelName: String) {
    viewModelScope.launch {
      try {
        repository.updatePreferences(currentUserId, preferredModel = modelName)
        activeChatSession = null
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to update preferred model", e)
      }
    }
  }

  fun movePlayerBy(forwardDelta: Float, strafeDelta: Float) {
    _worldState.update { state ->
      val sinYaw = sin(state.playerYawRad)
      val cosYaw = cos(state.playerYawRad)
      val dx = forwardDelta * sinYaw + strafeDelta * cosYaw
      val dz = forwardDelta * cosYaw - strafeDelta * sinYaw
      val nextX = (state.playerX + dx).coerceIn(-4.2f, 4.2f)
      val nextZ = (state.playerZ + dz).coerceIn(1.5f, 44.5f)
      state.copy(
        playerX = nextX,
        playerZ = nextZ,
        walkTargetX = null,
        walkTargetZ = null,
      )
    }
    updateNearbyLocal()
  }

  fun movePlayer2D(dx: Float, dz: Float) {
    _worldState.update { state ->
      val nextX = (state.playerX + dx).coerceIn(-4.2f, 4.2f)
      val nextZ = (state.playerZ + dz).coerceIn(1.5f, 44.5f)
      val nextYaw = if (hypot(dx, dz) > 0.01f) atan2(dx, dz) else state.playerYawRad
      state.copy(
        playerX = nextX,
        playerZ = nextZ,
        playerYawRad = nextYaw,
        walkTargetX = null,
        walkTargetZ = null,
      )
    }
    updateNearbyLocal()
  }

  fun rotateCamera(deltaYawRad: Float, deltaPitchRad: Float) {
    _worldState.update { state ->
      state.copy(
        playerYawRad = state.playerYawRad + deltaYawRad,
        playerPitchRad = (state.playerPitchRad + deltaPitchRad).coerceIn(-0.42f, 0.35f),
      )
    }
  }

  fun setPlayerPosition(x: Float, z: Float, yawRad: Float? = null) {
    _worldState.update { state ->
      state.copy(
        playerX = x.coerceIn(-4.2f, 4.2f),
        playerZ = z.coerceIn(1.5f, 44.5f),
        playerYawRad = yawRad ?: state.playerYawRad,
      )
    }
    updateNearbyLocal()
  }

  fun focusLocalOnStreet(local: StreetLocal) {
    val approachX = if (local.worldX < 0f) (local.worldX + 1.8f) else (local.worldX - 1.8f)
    val approachZ = (local.worldZ - 2.2f).coerceIn(1.5f, 44.5f)
    val yawToLocal = atan2(local.worldX - approachX, local.worldZ - approachZ)
    _worldState.update {
      it.copy(
        playerX = approachX.coerceIn(-4.2f, 4.2f),
        playerZ = approachZ,
        playerYawRad = yawToLocal,
        playerPitchRad = -0.02f,
        nearbyLocal = local,
      )
    }
  }

  private fun updateNearbyLocal() {
    updateNearbyLocalForCity(currentCity())
  }

  private fun updateNearbyLocalForCity(city: CityDistrict) {
    val state = _worldState.value
    val nearest = city.locals.minByOrNull { local ->
      hypot(local.worldX - state.playerX, local.worldZ - state.playerZ)
    }
    val distance = nearest?.let { hypot(it.worldX - state.playerX, it.worldZ - state.playerZ) } ?: Float.MAX_VALUE
    _worldState.update {
      it.copy(nearbyLocal = if (distance <= 6.2f) nearest else null)
    }
  }

  fun openChatWithLocal(local: StreetLocal) {
    focusLocalOnStreet(local)
    val city = currentCity()
    val isSameLocal = _worldState.value.activeLocal?.id == local.id && _worldState.value.chatTurns.isNotEmpty()
    if (!isSameLocal) {
      activeChatSession = null
      val detectedIds = GrammarCatalog.detectGrammarPoints(city.id, local.localGreeting).map { it.id }
      val starterTurn = ChatTurn(
        id = UUID.randomUUID().toString(),
        isUser = false,
        text = local.localGreeting,
        phonetic = local.greetingPhonetic,
        translation = local.greetingEnglish,
        coachTip = "Quest: ${local.questTitle}",
        suggestedReplies = local.starterReplies,
        grammarPointIds = detectedIds,
      )
      _worldState.update {
        it.copy(
          activeLocal = local,
          chatTurns = listOf(starterTurn),
          chatError = null,
          showChatSheet = true,
        )
      }
      // Stream an instant welcome illustration for this local's street stall!
      streamIllustration(
        subject = local.illustrationSubject,
        phraseContext = local.localGreeting,
        openSheet = false,
      )
    } else {
      _worldState.update { it.copy(showChatSheet = true, chatError = null) }
    }
  }

  fun closeChatSheet() {
    _worldState.update { it.copy(showChatSheet = false) }
  }

  fun openIllustrationSheetFor(subject: String, phraseContext: String) {
    streamIllustration(subject = subject, phraseContext = phraseContext, openSheet = true)
  }

  fun closeIllustrationSheet() {
    _worldState.update { it.copy(showIllustrationSheet = false) }
  }

  fun sendChatMessage(userText: String) {
    val cleanText = userText.trim()
    if (cleanText.isEmpty()) return
    val local = _worldState.value.activeLocal ?: return
    val profile = currentProfile()
    val city = CityCatalog.cityById(profile.activeCityId)
    val difficulty = StreetDifficulty.fromId(profile.difficulty)
    val modelName = profile.preferredModel.ifBlank { "gemini-4-hydrogen" }

    val userGrammarIds = GrammarCatalog.detectGrammarPoints(city.id, cleanText).map { it.id }
    val userTurn = ChatTurn(
      id = UUID.randomUUID().toString(),
      isUser = true,
      text = cleanText,
      grammarPointIds = userGrammarIds,
    )
    val streamingTurnId = UUID.randomUUID().toString()
    val placeholderTurn = ChatTurn(
      id = streamingTurnId,
      isUser = false,
      text = "",
      isStreaming = true,
    )

    val historyBeforeUser = _worldState.value.chatTurns
    _worldState.update {
      it.copy(
        chatTurns = it.chatTurns + userTurn + placeholderTurn,
        isChatStreaming = true,
        chatError = null,
      )
    }

    chatJob?.cancel()
    chatJob = viewModelScope.launch {
      try {
        val chat = activeChatSession ?: aiService.startNpcChat(
          modelName = modelName,
          city = city,
          local = local,
          difficulty = difficulty,
          historyTurns = historyBeforeUser,
        ).also { activeChatSession = it }

        var latestQuestDone = false
        var latestReplyText = ""

        aiService.streamLocalReply(chat, cleanText).collect { update ->
          latestReplyText = update.replyText
          latestQuestDone = update.questCompleted
          val detectedIds = GrammarCatalog.detectGrammarPoints(city.id, update.replyText).map { it.id }
          val mergedGrammarIds = (update.grammarPointIds + detectedIds).distinct().take(3)
          _worldState.update { state ->
            state.copy(
              chatTurns = state.chatTurns.map { turn ->
                if (turn.id == streamingTurnId) {
                  turn.copy(
                    text = update.replyText,
                    phonetic = update.phonetic,
                    translation = update.translation,
                    coachTip = update.coachTip,
                    suggestedReplies = update.suggestedReplies,
                    questCompleted = update.questCompleted,
                    grammarPointIds = mergedGrammarIds,
                    grammarNote = update.grammarNote,
                    isStreaming = !update.isComplete,
                  )
                } else turn
              },
            )
          }
        }

        _worldState.update { it.copy(isChatStreaming = false) }

        // Award XP for each conversational exchange (+20 XP, +75 XP bonus on quest completion) and update daily streak
        val latestProfile = currentProfile()
        val xpGain = if (latestQuestDone && !latestProfile.completedNpcIds.contains(local.id)) 75L else 20L
        val completedId = if (latestQuestDone) local.id else null
        val streakUpdate = repository.awardProgress(
          userId = currentUserId,
          xpDelta = xpGain,
          completedNpcId = completedId,
          currentStreakDays = latestProfile.streakDays,
          lastPracticeDate = latestProfile.lastPracticeDate,
        )
        if (streakUpdate.didIncrement) {
          _worldState.update {
            it.copy(savedToastMessage = "Daily streak extended to ${streakUpdate.newStreak} day(s)!")
          }
        }

        // Automatically stream an instant illustration reflecting the live reply!
        if (latestReplyText.isNotBlank()) {
          streamIllustration(
            subject = "${local.roleTitle} scene illustrating: $latestReplyText",
            phraseContext = latestReplyText,
            openSheet = false,
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Local chat failed", e)
        _worldState.update { state ->
          state.copy(
            chatTurns = state.chatTurns.filterNot { it.id == streamingTurnId && it.text.isBlank() }
              .map { if (it.id == streamingTurnId) it.copy(isStreaming = false) else it },
            isChatStreaming = false,
            chatError = e.localizedMessage ?: "Failed to chat with ${local.name}.",
          )
        }
      }
    }
  }

  fun streamIllustration(subject: String, phraseContext: String, openSheet: Boolean) {
    val profile = currentProfile()
    val city = CityCatalog.cityById(profile.activeCityId)
    val modelName = profile.preferredModel.ifBlank { "gemini-4-hydrogen" }

    _hdPaintState.value = HdPaintUiState.Idle
    if (openSheet) {
      _worldState.update { it.copy(showIllustrationSheet = true) }
    }

    illustrationJob?.cancel()
    illustrationJob = viewModelScope.launch {
      _illustrationState.value = IllustrationUiState.Streaming(
        parsed = ParsedIllustration(),
        subject = subject,
        modelName = modelName,
      )
      try {
        var finalParsed = ParsedIllustration()
        aiService.streamInstantIllustration(
          modelName = modelName,
          city = city,
          subject = subject,
          phraseContext = phraseContext,
        ).collect { parsed ->
          finalParsed = parsed
          _illustrationState.value = IllustrationUiState.Streaming(
            parsed = parsed,
            subject = subject,
            modelName = modelName,
          )
        }
        _illustrationState.value = IllustrationUiState.Success(
          parsed = finalParsed,
          subject = subject,
          modelName = modelName,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Instant illustration failed", e)
        _illustrationState.value = IllustrationUiState.Error(
          e.localizedMessage ?: "Failed to stream illustration."
        )
      }
    }
  }

  fun requestHdStudioPaint(subject: String) {
    val city = currentCity()
    hdPaintJob?.cancel()
    hdPaintJob = viewModelScope.launch {
      _hdPaintState.value = HdPaintUiState.Loading
      try {
        val bitmap = aiService.generateHdRasterIllustration(city, subject)
        _hdPaintState.value = HdPaintUiState.Success(bitmap)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "HD Studio Paint failed", e)
        _hdPaintState.value = HdPaintUiState.Error(
          e.localizedMessage ?: "HD Studio Paint failed."
        )
      }
    }
  }

  fun saveTurnToPhrasebook(turn: ChatTurn, local: StreetLocal?) {
    val city = currentCity()
    val currentIllus = when (val s = _illustrationState.value) {
      is IllustrationUiState.Success -> s.parsed.rawLines
      is IllustrationUiState.Streaming -> s.parsed.rawLines
      else -> emptyList()
    }
    viewModelScope.launch {
      try {
        repository.savePhrase(
          userId = currentUserId,
          cityId = city.id,
          npcName = local?.name ?: city.name,
          targetText = turn.text,
          phonetic = turn.phonetic,
          translation = turn.translation.ifBlank { turn.text },
          culturalNote = turn.coachTip,
          illustrationCommands = currentIllus,
        )
        val currentProf = currentProfile()
        val streakUpdate = repository.awardProgress(
          userId = currentUserId,
          xpDelta = 15L,
          currentStreakDays = currentProf.streakDays,
          lastPracticeDate = currentProf.lastPracticeDate,
        )
        val toast = if (streakUpdate.didIncrement) {
          "Saved to your Phrasebook (+15 XP · ${streakUpdate.newStreak}d streak!)"
        } else {
          "Saved to your Phrasebook (+15 XP)"
        }
        _worldState.update { it.copy(savedToastMessage = toast) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to save phrase", e)
        _worldState.update { it.copy(savedToastMessage = e.localizedMessage ?: "Could not save phrase") }
      }
    }
  }

  fun saveCurrentIllustrationToPhrasebook() {
    val parsed = when (val s = _illustrationState.value) {
      is IllustrationUiState.Success -> s.parsed
      is IllustrationUiState.Streaming -> s.parsed
      else -> return
    }
    val caption = parsed.caption ?: return
    val city = currentCity()
    val localName = _worldState.value.activeLocal?.name ?: _worldState.value.nearbyLocal?.name ?: city.name
    viewModelScope.launch {
      try {
        repository.savePhrase(
          userId = currentUserId,
          cityId = city.id,
          npcName = localName,
          targetText = caption.targetPhrase,
          phonetic = caption.phonetic,
          translation = caption.englishMeaning,
          culturalNote = caption.culturalTip,
          illustrationCommands = parsed.rawLines,
        )
        val currentProf = currentProfile()
        val streakUpdate = repository.awardProgress(
          userId = currentUserId,
          xpDelta = 15L,
          currentStreakDays = currentProf.streakDays,
          lastPracticeDate = currentProf.lastPracticeDate,
        )
        val toast = if (streakUpdate.didIncrement) {
          "Illustrated card saved (+15 XP · ${streakUpdate.newStreak}d streak!)"
        } else {
          "Illustrated card saved (+15 XP)"
        }
        _worldState.update { it.copy(savedToastMessage = toast) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to save illustrated card", e)
        _worldState.update { it.copy(savedToastMessage = e.localizedMessage ?: "Could not save card") }
      }
    }
  }

  fun updatePhraseMastery(phraseId: String, newLevel: Long) {
    viewModelScope.launch {
      try {
        repository.updatePhraseMastery(currentUserId, phraseId, newLevel)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to update mastery", e)
      }
    }
  }

  fun deletePhrase(phraseId: String) {
    viewModelScope.launch {
      try {
        repository.deletePhrase(currentUserId, phraseId)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to delete phrase", e)
      }
    }
  }

  fun clearSavedToast() {
    _worldState.update { it.copy(savedToastMessage = null) }
  }

  fun openContextualGrammar(
    point: GrammarPoint?,
    sourceSentence: String,
    cityId: String = currentCity().id,
    npcName: String? = null,
    turnNote: String = "",
  ) {
    val resolvedPoint = point
      ?: GrammarCatalog.detectGrammarPoints(cityId, sourceSentence, maxResults = 1).firstOrNull()
    contextualGrammarJob?.cancel()
    _contextualGrammarAiState.value = GrammarAiUiState.Idle
    _contextualGrammar.value = ContextualGrammarState(
      point = resolvedPoint,
      sourceSentence = sourceSentence,
      cityId = cityId,
      npcName = npcName,
      turnNote = turnNote,
    )
  }

  fun closeContextualGrammar() {
    contextualGrammarJob?.cancel()
    _contextualGrammar.value = null
    _contextualGrammarAiState.value = GrammarAiUiState.Idle
  }

  fun requestContextualAiExplanation() {
    val ctx = _contextualGrammar.value ?: return
    val city = CityCatalog.cityById(ctx.cityId)
    val modelName = currentProfile().preferredModel.ifBlank { "gemini-4-hydrogen" }
    contextualGrammarJob?.cancel()
    contextualGrammarJob = viewModelScope.launch {
      _contextualGrammarAiState.value = GrammarAiUiState.Streaming("")
      try {
        var finalOutput = ""
        aiService.streamContextualGrammarBreakdown(
          modelName = modelName,
          city = city,
          sentence = ctx.sourceSentence,
          grammarPoint = ctx.point,
        ).collect { text ->
          finalOutput = text
          _contextualGrammarAiState.value = GrammarAiUiState.Streaming(text)
        }
        _contextualGrammarAiState.value = GrammarAiUiState.Success(finalOutput)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Contextual grammar AI breakdown failed", e)
        _contextualGrammarAiState.value = GrammarAiUiState.Error(
          e.localizedMessage ?: "Failed to analyze sentence grammar."
        )
      }
    }
  }

  fun selectGrammarCity(cityId: String) {
    _selectedGrammarCityId.value = cityId
  }

  fun focusGrammarPointInGuide(point: GrammarPoint) {
    _selectedGrammarCityId.value = point.cityId
    _focusedGrammarPointId.value = point.id
    closeContextualGrammar()
    closeChatSheet()
  }

  fun clearFocusedGrammarPoint() {
    _focusedGrammarPointId.value = null
  }

  fun requestMoreExamplesForPoint(point: GrammarPoint) {
    val city = CityCatalog.cityById(point.cityId)
    val modelName = currentProfile().preferredModel.ifBlank { "gemini-4-hydrogen" }
    guideExamplesJob?.cancel()
    guideExamplesJob = viewModelScope.launch {
      _guideExtraExamples.update { it + (point.id to GrammarAiUiState.Streaming("")) }
      try {
        var finalOutput = ""
        aiService.streamMoreStreetExamples(
          modelName = modelName,
          city = city,
          grammarPoint = point,
        ).collect { text ->
          finalOutput = text
          _guideExtraExamples.update { it + (point.id to GrammarAiUiState.Streaming(text)) }
        }
        _guideExtraExamples.update { it + (point.id to GrammarAiUiState.Success(finalOutput)) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "More grammar examples failed", e)
        _guideExtraExamples.update {
          it + (point.id to GrammarAiUiState.Error(e.localizedMessage ?: "Failed to generate extra examples."))
        }
      }
    }
  }

  fun practiceLocalFromGrammar(cityId: String, npcId: String) {
    val city = CityCatalog.cityById(cityId)
    val local = city.locals.firstOrNull { it.id == npcId } ?: city.locals.firstOrNull() ?: return
    closeContextualGrammar()
    viewModelScope.launch {
      try {
        if (currentProfile().activeCityId != cityId) {
          repository.updatePreferences(currentUserId, activeCityId = cityId)
        }
        openChatWithLocal(local)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to switch city for grammar practice", e)
      }
    }
  }

  fun openStreakDialog() {
    _showStreakDialog.value = true
  }

  fun closeStreakDialog() {
    _showStreakDialog.value = false
  }

  fun openVoicePracticeForCity(cityId: String = currentCity().id) {
    val city = CityCatalog.cityById(cityId)
    val prompts = city.locals.flatMap { local ->
      buildList {
        add(
          VoicePracticePrompt(
            npcName = local.name,
            targetPhrase = local.localGreeting,
            phonetic = local.greetingPhonetic,
            englishMeaning = local.greetingEnglish,
          ),
        )
        local.starterReplies.firstOrNull()?.let { reply ->
          add(
            VoicePracticePrompt(
              npcName = "Reply to ${local.name}",
              targetPhrase = reply,
              phonetic = "",
              englishMeaning = "Starter response for ${local.questTitle}",
            ),
          )
        }
      }
    }
    voiceCoachJob?.cancel()
    _voiceCoachAiState.value = GrammarAiUiState.Idle
    _voicePracticeState.value = VoicePracticeState(
      cityId = city.id,
      prompts = prompts,
      promptIndex = 0,
    )
  }

  fun openVoicePracticeForTarget(
    cityId: String,
    npcName: String,
    targetPhrase: String,
    phonetic: String = "",
    englishMeaning: String = "",
  ) {
    voiceCoachJob?.cancel()
    _voiceCoachAiState.value = GrammarAiUiState.Idle
    _voicePracticeState.value = VoicePracticeState(
      cityId = cityId,
      prompts = listOf(
        VoicePracticePrompt(
          npcName = npcName,
          targetPhrase = targetPhrase,
          phonetic = phonetic,
          englishMeaning = englishMeaning,
        ),
      ),
      promptIndex = 0,
    )
  }

  fun previousVoicePrompt() {
    val current = _voicePracticeState.value ?: return
    if (current.prompts.size <= 1) return
    val prevIdx = if (current.promptIndex == 0) current.prompts.lastIndex else current.promptIndex - 1
    voiceCoachJob?.cancel()
    _voiceCoachAiState.value = GrammarAiUiState.Idle
    _voicePracticeState.value = current.copy(
      promptIndex = prevIdx,
      lastSpokenTranscript = "",
      evaluation = null,
    )
  }

  fun nextVoicePrompt() {
    val current = _voicePracticeState.value ?: return
    if (current.prompts.size <= 1) return
    val nextIdx = (current.promptIndex + 1) % current.prompts.size
    voiceCoachJob?.cancel()
    _voiceCoachAiState.value = GrammarAiUiState.Idle
    _voicePracticeState.value = current.copy(
      promptIndex = nextIdx,
      lastSpokenTranscript = "",
      evaluation = null,
    )
  }

  fun onVoiceSpokenResult(transcript: String) {
    val current = _voicePracticeState.value ?: return
    val clean = transcript.trim()
    if (clean.isEmpty()) return
    val eval = PronunciationEvaluator.evaluate(current.targetPhrase, clean)
    voiceCoachJob?.cancel()
    _voiceCoachAiState.value = GrammarAiUiState.Idle
    _voicePracticeState.value = current.copy(
      lastSpokenTranscript = clean,
      evaluation = eval,
    )
    viewModelScope.launch {
      try {
        val profile = currentProfile()
        val streakUpdate = repository.awardProgress(
          userId = currentUserId,
          xpDelta = 20L,
          currentStreakDays = profile.streakDays,
          lastPracticeDate = profile.lastPracticeDate,
        )
        val toast = if (streakUpdate.didIncrement) {
          "Voice practice: ${eval.score}% (+20 XP · ${streakUpdate.newStreak}d streak!)"
        } else {
          "Voice practice: ${eval.score}% (+20 XP)"
        }
        _worldState.update { it.copy(savedToastMessage = toast) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to record voice practice progress", e)
      }
    }
  }

  fun requestVoiceCoachAiFeedback() {
    val current = _voicePracticeState.value ?: return
    if (current.lastSpokenTranscript.isBlank()) return
    val city = CityCatalog.cityById(current.cityId)
    val modelName = currentProfile().preferredModel.ifBlank { "gemini-4-hydrogen" }
    voiceCoachJob?.cancel()
    voiceCoachJob = viewModelScope.launch {
      _voiceCoachAiState.value = GrammarAiUiState.Streaming("")
      try {
        var finalText = ""
        aiService.streamVoiceCoachFeedback(
          modelName = modelName,
          city = city,
          expectedPhrase = current.targetPhrase,
          spokenTranscript = current.lastSpokenTranscript,
        ).collect { text ->
          finalText = text
          _voiceCoachAiState.value = GrammarAiUiState.Streaming(text)
        }
        _voiceCoachAiState.value = GrammarAiUiState.Success(finalText)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Voice coach feedback failed", e)
        _voiceCoachAiState.value = GrammarAiUiState.Error(
          e.localizedMessage ?: "Failed to get pronunciation coaching."
        )
      }
    }
  }

  fun closeVoicePractice() {
    voiceCoachJob?.cancel()
    _voicePracticeState.value = null
    _voiceCoachAiState.value = GrammarAiUiState.Idle
  }

  companion object {
    fun provideFactory(
      repository: StreetLingoRepository,
      aiService: GeminiStreetService,
      currentUserId: String,
      initialDisplayName: String,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        StreetLingoViewModel(
          repository = repository,
          aiService = aiService,
          currentUserId = currentUserId,
          initialDisplayName = initialDisplayName,
        )
      }
    }
  }
}
