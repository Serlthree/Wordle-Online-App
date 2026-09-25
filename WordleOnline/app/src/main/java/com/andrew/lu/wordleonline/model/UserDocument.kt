package com.andrew.lu.wordleonline.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class UserDocument(
    @DocumentId
    val uid : String = "",
    var wins : Int = 0,
    var draws : Int = 0,
    var losses : Int = 0,
    var guessDistribution: List<Int> = List(7) {0},
    @ServerTimestamp
    val createdAt : Date? = null,
    var winStreak : Int = 0,
    var maxWinStreak : Int = 0
)