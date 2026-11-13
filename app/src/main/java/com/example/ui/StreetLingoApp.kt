package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.ai.GeminiStreetService
import com.example.data.model.UserProfile
import com.example.data.repository.StreetLingoRepository
import com.example.firebase.attemptAutoSignIn
import com.example.firebase.authStateFlow
import com.example.ui.components.ContextualGrammarSheet
import com.example.ui.components.rememberTtsController
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.GrammarGuideScreen
import com.example.ui.screens.PassportScreen
import com.example.ui.screens.PhrasebookScreen
import com.example.ui.screens.StreetExplorerScreen
import com.example.ui.viewmodel.DataUiState
import com.example.ui.viewmodel.StreetLingoViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_STREETS = "streets"
const val ROUTE_GRAMMAR = "grammar"
const val ROUTE_PHRASEBOOK = "phrasebook"
const val ROUTE_PASSPORT = "passport"

@Composable
fun StreetLingoApp(auth: FirebaseAuth = Firebase.auth) {
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  LaunchedEffect(Unit) {
    if (auth.currentUser == null) {
      attemptAutoSignIn(
        context = context,
        credentialManager = CredentialManager.create(context),
        onAuthSuccess = {},
        onUnauthenticated = {},
        scope = scope,
      )
    }
  }

  val user = currentUser
  if (user == null) {
    AuthScreen(onAuthSuccess = {})
  } else {
    key(user.uid) {
      AuthenticatedStreetLingo(
        currentUserId = user.uid,
        displayName = user.displayName ?: user.email?.substringBefore("@") ?: "Traveler",
      )
    }
  }
}

@Composable
private fun AuthenticatedStreetLingo(
  currentUserId: String,
  displayName: String,
) {
  val viewModel: StreetLingoViewModel = viewModel(
    key = currentUserId,
    factory = remember(currentUserId) {
      viewModelFactory {
        initializer {
          val app = checkNotNull(this[APPLICATION_KEY]) {
            "APPLICATION_KEY missing from CreationExtras"
          }
          val databaseId = app.getString(R.string.firestore_database_id)
          val db = FirebaseFirestore.getInstance(databaseId)
          StreetLingoViewModel(
            repository = StreetLingoRepository(db),
            aiService = GeminiStreetService(),
            currentUserId = currentUserId,
            initialDisplayName = displayName,
          )
        }
      }
    },
  )

  val profileState by viewModel.profileState.collectAsStateWithLifecycle()
  val phrasesState by viewModel.phrasesState.collectAsStateWithLifecycle()
  val worldState by viewModel.worldState.collectAsStateWithLifecycle()
  val illustrationState by viewModel.illustrationState.collectAsStateWithLifecycle()
  val hdPaintState by viewModel.hdPaintState.collectAsStateWithLifecycle()
  val contextualGrammar by viewModel.contextualGrammar.collectAsStateWithLifecycle()
  val contextualGrammarAiState by viewModel.contextualGrammarAiState.collectAsStateWithLifecycle()
  val guideExtraExamples by viewModel.guideExtraExamples.collectAsStateWithLifecycle()
  val selectedGrammarCityId by viewModel.selectedGrammarCityId.collectAsStateWithLifecycle()
  val focusedGrammarPointId by viewModel.focusedGrammarPointId.collectAsStateWithLifecycle()
  val tts = rememberTtsController()

  val profile = when (val s = profileState) {
    is DataUiState.Success -> s.data
    else -> UserProfile(userId = currentUserId, displayName = displayName)
  }

  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_STREETS

  val navItems = listOf(
    Triple(ROUTE_STREETS, R.string.nav_streets, Icons.Rounded.Explore to Icons.Outlined.Explore),
    Triple(ROUTE_GRAMMAR, R.string.nav_grammar, Icons.AutoMirrored.Rounded.MenuBook to Icons.AutoMirrored.Outlined.MenuBook),
    Triple(ROUTE_PHRASEBOOK, R.string.nav_phrasebook, Icons.Rounded.AutoStories to Icons.Outlined.AutoStories),
    Triple(ROUTE_PASSPORT, R.string.nav_passport, Icons.Rounded.Badge to Icons.Outlined.Badge),
  )

  fun navigateTab(route: String) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) {
        saveState = true
      }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp

    Scaffold(
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      bottomBar = {
        if (!useRail) {
          NavigationBar(windowInsets = WindowInsets.navigationBars) {
            navItems.forEach { (route, labelRes, icons) ->
              val selected = currentRoute == route
              NavigationBarItem(
                selected = selected,
                onClick = { navigateTab(route) },
                icon = {
                  Icon(
                    imageVector = if (selected) icons.first else icons.second,
                    contentDescription = stringResource(labelRes),
                  )
                },
                label = { Text(stringResource(labelRes)) },
                modifier = Modifier.testTag("nav_tab_$route"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (useRail) {
          NavigationRail(modifier = Modifier.fillMaxHeight()) {
            navItems.forEach { (route, labelRes, icons) ->
              val selected = currentRoute == route
              NavigationRailItem(
                selected = selected,
                onClick = { navigateTab(route) },
                icon = {
                  Icon(
                    imageVector = if (selected) icons.first else icons.second,
                    contentDescription = stringResource(labelRes),
                  )
                },
                label = { Text(stringResource(labelRes)) },
                modifier = Modifier.testTag("nav_rail_$route"),
              )
            }
          }
        }

        if (profileState is DataUiState.Loading) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        } else {
          NavHost(
            navController = navController,
            startDestination = ROUTE_STREETS,
            modifier = Modifier.fillMaxSize(),
          ) {
            composable(ROUTE_STREETS) {
              StreetExplorerScreen(
                profile = profile,
                worldState = worldState,
                illustrationState = illustrationState,
                hdPaintState = hdPaintState,
                viewModel = viewModel,
              )
            }
            composable(ROUTE_GRAMMAR) {
              GrammarGuideScreen(
                activeCityId = profile.activeCityId,
                selectedGrammarCityId = selectedGrammarCityId,
                focusedGrammarPointId = focusedGrammarPointId,
                activeNpcName = worldState.activeLocal?.name,
                recentChatTurns = worldState.chatTurns,
                extraExamplesMap = guideExtraExamples,
                onSelectCity = { cityId -> viewModel.selectGrammarCity(cityId) },
                onClearFocusedPoint = { viewModel.clearFocusedGrammarPoint() },
                onRequestMoreExamples = { point -> viewModel.requestMoreExamplesForPoint(point) },
                onPracticeWithLocal = { cityId, npcId ->
                  viewModel.practiceLocalFromGrammar(cityId, npcId)
                  navigateTab(ROUTE_STREETS)
                },
              )
            }
            composable(ROUTE_PHRASEBOOK) {
              PhrasebookScreen(
                phrasesState = phrasesState,
                onUpdateMastery = { id, level -> viewModel.updatePhraseMastery(id, level) },
                onDeletePhrase = { id -> viewModel.deletePhrase(id) },
                onInspectGrammar = { point, phrase ->
                  viewModel.openContextualGrammar(
                    point = point,
                    sourceSentence = phrase.targetText,
                    cityId = phrase.cityId,
                    npcName = phrase.npcName,
                    turnNote = phrase.culturalNote,
                  )
                },
              )
            }
            composable(ROUTE_PASSPORT) {
              val phraseCount = (phrasesState as? DataUiState.Success)?.data?.size ?: 0
              PassportScreen(
                profile = profile,
                savedPhraseCount = phraseCount,
                onSelectCity = { cityId ->
                  viewModel.selectCity(cityId)
                  navigateTab(ROUTE_STREETS)
                },
                onSelectDifficulty = { diff -> viewModel.selectDifficulty(diff) },
                onSelectModel = { model -> viewModel.selectModel(model) },
                onSignedOut = {},
              )
            }
          }
        }
      }
    }

    val activeContext = contextualGrammar
    if (activeContext != null) {
      ContextualGrammarSheet(
        state = activeContext,
        aiState = contextualGrammarAiState,
        tts = tts,
        onAskGeminiBreakdown = { viewModel.requestContextualAiExplanation() },
        onOpenInFullGuide = { point ->
          viewModel.focusGrammarPointInGuide(point)
          navigateTab(ROUTE_GRAMMAR)
        },
        onDismiss = { viewModel.closeContextualGrammar() },
      )
    }
  }
}
