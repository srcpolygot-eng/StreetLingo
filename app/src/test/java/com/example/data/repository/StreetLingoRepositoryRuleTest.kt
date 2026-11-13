package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StreetLingoRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun ensureAndObserveUserProfile_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = StreetLingoRepository(firestore)

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.ensureUserProfile(uid, "Alice Explorer")
    }

    val profile = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeUserProfile(uid).first { it != null }
    }
    assertNotNull(profile)
    assertEquals("paris", profile?.activeCityId)
    assertEquals("gemini-4-hydrogen", profile?.preferredModel)
  }

  @Test
  fun saveAndObservePhrase_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = StreetLingoRepository(firestore)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.ensureUserProfile(uid, "Alice Explorer")
    }

    val phraseId = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.savePhrase(
        userId = uid,
        cityId = "paris",
        npcName = "Camille",
        targetText = "Bonjour ! Un croissant, s'il vous plaît.",
        phonetic = "Bohn-zhoor! Uhn krwah-sahn, seel voo pleh.",
        translation = "Hello! A croissant, please.",
        culturalNote = "Always start with Bonjour in Parisian bakeries.",
        illustrationCommands = listOf("BG|#FFF8EE|#F4A236", "OVAL|0.5|0.5|0.4|0.25|#D9822B|filled"),
      )
    }
    assertTrue(phraseId.isNotBlank())

    val phrases = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observePhrases(uid).first { list -> list.any { it.id == phraseId } }
    }
    assertTrue(phrases.any { it.id == phraseId && it.cityId == "paris" })
  }

  @Test
  fun observePhrases_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = StreetLingoRepository(firestore)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.ensureUserProfile(aliceUid, "Alice")
      aliceRepo.savePhrase(
        userId = aliceUid,
        cityId = "tokyo",
        npcName = "Kenji",
        targetText = "ラーメンをお願いします",
        phonetic = "Rāmen o onegaishimasu",
        translation = "Ramen, please",
        culturalNote = "Slurping noodles shows appreciation.",
        illustrationCommands = listOf("BG|#23396D|#E88D67"),
      )
    }

    signInTestUser(BOB_EMAIL)
    val bobRepo = StreetLingoRepository(firestore)
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        bobRepo.observePhrases(aliceUid).first()
      }
      fail("Expected PERMISSION_DENIED for cross-user phrase access")
    } catch (e: Exception) {
      val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
        "Expected nested FirebaseFirestoreException, got $e"
      }
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }
  }

  @Test
  fun observePhrases_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = StreetLingoRepository(firestore)
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observePhrases("some_uid").first()
      }
      fail("Expected PERMISSION_DENIED for unauthenticated caller")
    } catch (e: Exception) {
      val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
        "Expected nested FirebaseFirestoreException, got $e"
      }
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }
  }

  @Test
  fun awardProgress_updatesXpStreakAndLastPracticeDate() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = StreetLingoRepository(firestore)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.ensureUserProfile(uid, "Alice Explorer")
    }

    val streakUpdate = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.awardProgress(
        userId = uid,
        xpDelta = 20L,
        completedNpcId = "paris_camille",
        currentStreakDays = 0L,
        lastPracticeDate = "",
      )
    }
    assertEquals(1L, streakUpdate.newStreak)
    assertTrue(streakUpdate.didIncrement)

    val updatedProfile = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeUserProfile(uid).first { it != null && it.xp >= 20L }
    }
    assertNotNull(updatedProfile)
    assertEquals(20L, updatedProfile?.xp)
    assertEquals(1L, updatedProfile?.streakDays)
    assertEquals(streakUpdate.lastPracticeDate, updatedProfile?.lastPracticeDate)
    assertTrue(updatedProfile?.completedNpcIds?.contains("paris_camille") == true)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_street@test.com"
    const val BOB_EMAIL = "bob_street@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
