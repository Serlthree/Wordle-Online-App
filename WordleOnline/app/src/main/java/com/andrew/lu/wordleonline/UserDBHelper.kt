package com.andrew.lu.wordleonline

import android.util.Log
import com.andrew.lu.wordleonline.model.UserDocument
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserDBHelper {
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val rootCollection = db.collection("users")

    fun updateUserProfile(playerId: String, userStats: UserDocument){
        Log.d("UserDBHelper", "updating user profile")
        rootCollection.document(playerId).set(userStats)
    }

    //this gets fetched because we fetch every time match updates
    //see match listener
    //TODO: this could probably be refactored out completely
    suspend fun getUserProfile(playerId: String) : UserDocument? {
        return try {
            val snapshot = rootCollection.document(playerId).get().await()
            if (snapshot.exists()) {
                snapshot.toObject(UserDocument::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("UserDBHelper", "Error getting user profile", e)
            null
        }
    }
}