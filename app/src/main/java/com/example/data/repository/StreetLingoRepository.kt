package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.SavedPhrase
import com.example.data.model.UserProfile
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StreetLingoRepository(private val db: FirebaseFirestore) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  fun observeUserProfile(userId: String): Flow<UserProfile?> {
    val path = "users/$userId"
    return db.collection("users").document(userId)
      .snapshots()
      .map { snapshot ->
        if (snapshot.exists()) {
          snapshot.toObject(
            UserProfile::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        } else {
          null
        }
      }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && FirebaseAuth.getInstance().currentUser?.uid == userId) {
          handleFirestoreError(exceptionToLog, OperationType.GET, path)
        }
        throw error
      }
  }

  suspend fun ensureUserProfile(userId: String, displayName: String) {
    val docRef = db.collection("users").document(userId)
    try {
      val existing = docRef.get().await()
      if (!existing.exists()) {
        val safeName = displayName.trim().ifBlank { "Traveler" }.take(60)
        val payload = mapOf(
          "userId" to userId,
          "displayName" to safeName,
          "activeCityId" to "paris",
          "difficulty" to "beginner",
          "preferredModel" to "gemini-4-hydrogen",
          "xp" to 0L,
          "streakDays" to 1L,
          "completedNpcIds" to emptyList<String>(),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.set(payload).await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun updatePreferences(
    userId: String,
    activeCityId: String? = null,
    difficulty: String? = null,
    preferredModel: String? = null,
  ) {
    val docRef = db.collection("users").document(userId)
    val updates = mutableMapOf<String, Any>(
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    if (activeCityId != null) updates["activeCityId"] = activeCityId
    if (difficulty != null) updates["difficulty"] = difficulty
    if (preferredModel != null) updates["preferredModel"] = preferredModel

    try {
      docRef.update(updates).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun awardProgress(
    userId: String,
    xpDelta: Long,
    completedNpcId: String? = null,
  ) {
    val docRef = db.collection("users").document(userId)
    val updates = mutableMapOf<String, Any>(
      "xp" to FieldValue.increment(xpDelta.coerceAtLeast(0L)),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    if (!completedNpcId.isNullOrBlank()) {
      updates["completedNpcIds"] = FieldValue.arrayUnion(completedNpcId)
    }
    try {
      docRef.update(updates).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  fun observePhrases(userId: String): Flow<List<SavedPhrase>> {
    val path = "users/$userId/phrases"
    return db.collection("users").document(userId)
      .collection("phrases")
      .orderBy("createdAt", Query.Direction.DESCENDING)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(
          SavedPhrase::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && FirebaseAuth.getInstance().currentUser?.uid == userId) {
          handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
        throw error
      }
  }

  suspend fun savePhrase(
    userId: String,
    cityId: String,
    npcName: String,
    targetText: String,
    phonetic: String,
    translation: String,
    culturalNote: String,
    illustrationCommands: List<String>,
  ): String {
    val docRef = db.collection("users").document(userId)
      .collection("phrases")
      .document()

    val payload = mapOf(
      "userId" to userId,
      "cityId" to cityId,
      "npcName" to npcName.trim().ifBlank { "Local" }.take(60),
      "targetText" to targetText.trim().take(300),
      "phonetic" to phonetic.trim().take(300),
      "translation" to translation.trim().ifBlank { targetText }.take(300),
      "culturalNote" to culturalNote.trim().take(500),
      "illustrationCommands" to illustrationCommands.take(80),
      "masteryLevel" to 1L,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )

    try {
      docRef.set(payload).await()
      return docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun updatePhraseMastery(userId: String, phraseId: String, newLevel: Long) {
    val docRef = db.collection("users").document(userId)
      .collection("phrases")
      .document(phraseId)
    val clamped = newLevel.coerceIn(0L, 5L)
    try {
      docRef.update(
        mapOf(
          "masteryLevel" to clamped,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun deletePhrase(userId: String, phraseId: String) {
    val docRef = db.collection("users").document(userId)
      .collection("phrases")
      .document(phraseId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }
}
