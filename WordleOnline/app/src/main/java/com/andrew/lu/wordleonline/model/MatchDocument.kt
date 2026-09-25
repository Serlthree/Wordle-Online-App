package com.andrew.lu.wordleonline.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class PlayerStats(
    val guesses: List<String> = emptyList(),
    val finished: Boolean = false,
    val uid: String = ""
)

//2 states for status: waiting, full, starting
//3 states for hostWinner: win, loss, tie
data class MatchDocument(
    @DocumentId
    val matchId: String = "",
    val status: String = "waiting",
    val targetWord: String = "",
    @ServerTimestamp
    val createdAt: Date? = null,
    val hostId: String? = null,
    val guestId: String? = null,
    val hostUsername: String? = null,
    val guestUsername: String? = null,
    val hostReady: Boolean = false,
    val guestReady: Boolean = false,
    val hostScores: PlayerStats = PlayerStats(),
    val guestScores: PlayerStats = PlayerStats(),
    val hostWinner: String? = null,
    val chatMessages : String = ""
)

