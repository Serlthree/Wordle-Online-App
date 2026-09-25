package com.andrew.lu.wordleonline

import com.andrew.lu.wordleonline.model.MatchDocument
import com.andrew.lu.wordleonline.model.PlayerStats
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
//TODO should be refactored to match db helper
class ViewModelDBHelper {
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val rootCollection = db.collection("matches")

    //matchmaking stuff
    //two problems, 2 people joining in at the same or "ghost" lobbies
    //suspend fun, call this on a background thread
    suspend fun findOrCreateLobby(playerId: String, targetWord: String, username: String): String{
        val curDate = System.currentTimeMillis()
        val timeOutMillis = 5 * 60 * 1000

        val openLobbies = rootCollection
            .whereEqualTo("status", "waiting")
            .whereEqualTo("guestId", null)
            .limit(1)
            .get()
            .await()

        //just create a new lobby
        if(openLobbies.isEmpty){
            val newMatch = MatchDocument(
                targetWord = targetWord,
                hostId = playerId,
                hostUsername = username
            )
            val newMatchRef = rootCollection.add(newMatch)
                .await()
            return newMatchRef.id
        }


        //just get this as a document
        val targetLobbyDoc = openLobbies.documents[0]
        val targetLobbyRef = targetLobbyDoc.reference
        val createdAt = targetLobbyDoc.getDate("createdAt")?.time ?: 0L

        //if lobby is stale > 5 minutes just delete it
        if (curDate - createdAt > timeOutMillis) {
            targetLobbyRef.delete().await()
            return findOrCreateLobby(playerId, targetWord, username)
        }

        //else try to join
        return try {
            //transaction to prevent race conditions
            db.runTransaction { transaction ->
                val snapshot = transaction.get(targetLobbyRef)
                val guest = snapshot.getString("guestId")

                //check if current guest is empty here
                if(guest == null){
                    transaction.update(targetLobbyRef, mapOf(
                        "guestId" to playerId,
                        "guestUsername" to username,
                        "status" to "full"
                    ))
                    targetLobbyRef.id //return id
                }
                else{
                    throw Exception("Lobby full.")
                }
            }.await()
        }
        catch (e: Exception){
            return findOrCreateLobby(playerId, targetWord, username)
        }
    }

    //this is how viewmodel gets updates so only the viewmodel has to touch this db stuff
    //flow basically allows you to get autoupdates
    fun listenToMatch(matchId: String): Flow<MatchDocument?> = callbackFlow {
        val listener = rootCollection.document(matchId).addSnapshotListener { snapshot, exception ->
            if(exception != null){
                close(exception)
                return@addSnapshotListener
            }
            if(snapshot != null && snapshot.exists()){
                val match = snapshot.toObject(MatchDocument::class.java)
                trySend(match)
            }
            else{
                trySend(null)
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun leaveLobby(matchId: String, playerId: String){
        db.runTransaction { transaction ->
            val doc = rootCollection.document(matchId)
            val snapshot = transaction.get(doc)

            val hostId = snapshot.getString("hostId")
            val guestId = snapshot.getString("guestId")
            val guestUsername = snapshot.getString("guestUsername")
            val status = snapshot.getString("status")

            //host leaves
            //don't change status when starting (TODO refactor to playing)
            if (hostId == playerId && guestId != null){
                transaction.update(doc, mapOf(
                    "hostId" to guestId,
                    "hostUsername" to guestUsername,
                    "guestId" to null,
                    "guestUsername" to null,
                    "hostReady" to false,
                    "guestReady" to false,
                    "status" to if(status == "starting") "starting" else "waiting"
                ))
            }
            //guest leaves
            else if (guestId == playerId){
                transaction.update(doc, mapOf(
                    "guestId" to null,
                    "guestUsername" to null,
                    "hostReady" to false,
                    "guestReady" to false,
                    "status" to if(status == "starting") "starting" else "waiting"
                ))
            }
            else{
                //both players left
                transaction.delete(doc)
            }
        }.await()
    }

    fun toggleReady(matchId: String, isHost: Boolean, isReady: Boolean){
        val field = if (isHost) "hostReady" else "guestReady"
        rootCollection.document(matchId).update(field, isReady)
    }

    fun updateScores(matchId: String, isHost: Boolean, playerStats: PlayerStats){
        val field = if (isHost) "hostScores" else "guestScores"
        rootCollection.document(matchId).update(field, playerStats)
    }

    //transactions do matter in case two people leave at the same time
    suspend fun updateWinner(matchId : String, isHostWinner: String){
        db.runTransaction { transaction ->
            val doc = rootCollection.document(matchId)
            val snapshot = transaction.get(doc)
            val hostWinner = snapshot.getString("hostWinner")
            //already updated, just drop it
            if(hostWinner != null){
                return@runTransaction
            }
            transaction.update(doc, "hostWinner", isHostWinner)
        }.await()
    }

    fun startGame(matchId: String){
        rootCollection.document(matchId).update("status", "starting")
    }

    //this needs to run a transaction
    suspend fun updateChat(matchId : String, chatMessages : String){
        db.runTransaction { transaction ->
            val doc = rootCollection.document(matchId)
            transaction.update(doc, "chatMessages", chatMessages)
        }.await()
    }
}