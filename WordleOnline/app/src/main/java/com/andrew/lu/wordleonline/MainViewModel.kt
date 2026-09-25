package com.andrew.lu.wordleonline

import android.app.Application
import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.andrew.lu.wordleonline.model.MatchDocument
import com.andrew.lu.wordleonline.model.PlayerStats
import com.andrew.lu.wordleonline.model.UserDocument
import kotlinx.coroutines.launch
import kotlin.math.max

class MainViewModel(application: Application) : AndroidViewModel(application) {
    //matchmaking stuff
    private var _match = MutableLiveData<MatchDocument?>()
    val match: LiveData<MatchDocument?> = _match
    private val dbHelper = ViewModelDBHelper()
    private val userDbHelper = UserDBHelper()

    //user stuff
    private var currentAuthUser = invalidUser

    //stuff to keep track of (user side)
    private var _guesses = MutableLiveData<List<String>>()
    val guesses : LiveData<List<String>> = _guesses
    var guessesValue : List<String>
        get() = _guesses.value ?: listOf()
        set(value){
            _guesses.value = value
        }

    //stuff to keep track of (shared side)
    //did we win? (different from winner)
    private val _userIsWinner = MutableLiveData<String?>()
    val userIsWinner : LiveData<String?> = _userIsWinner

    //no need for this to be a livedata because there's no reason to observe
    var targetWord : String = ""

    //stuff needed for backend calls
    var currentMatchId: String = ""
    var isHost: Boolean = false

    //timer stuff
    private var countDownTimer : CountDownTimer? = null
    private var _timeLeft = MutableLiveData<Long?>()
    val timeLeft : LiveData<Long?> = _timeLeft
    private var _finished = MutableLiveData<Boolean>()
    val finished : LiveData<Boolean> = _finished
    //smarter match observation
    private var matchObservationJob: kotlinx.coroutines.Job? = null
    //view userDoc (converted to livedata due to concurrency issues)
    //TODO: this is an unfortunate name
    private val _authUserDoc = MutableLiveData<UserDocument>()
    val authUserDoc: LiveData<UserDocument> = _authUserDoc

    //we can just get the authuser object
    var authUser : AuthUser? = null

    private val repository = Repository()


    //some general wrappers for repository
    fun generateTargetWord() : String{
        targetWord = repository.generateRandomSolution(application)
        return targetWord
    }

    fun isWordValid(word : String) : Boolean{
        return repository.checkIfWordIsValid(application, word)
    }

    // MainActivity gets updates on this via live data and informs view model
    fun setCurrentAuthUser(user: User) {
        currentAuthUser = user
    }

    fun setUser(user: AuthUser){
        authUser = user
    }

    fun signOut(){
        authUser?.logout()
    }

    fun setName(name : String){
        authUser?.setDisplayName(name)
    }

    fun submitGuess(word: String) : Boolean {
        //guess checking
        if(isWordValid(word)) {
            val newGuesses = guessesValue.toMutableList()
            newGuesses.add(word)
            guessesValue = newGuesses
            //one extra write if user finishes... but shouldn't matter
            updateScores()
            return true
        }
        return false
    }

    //matchmaking stuff
    fun startMatchmaking(){
        //need to launch in a coroutine
        targetWord = generateTargetWord()
        viewModelScope.launch {
            val matchId = dbHelper.findOrCreateLobby(currentAuthUser.uid, targetWord, currentAuthUser.name)
            currentMatchId = matchId
            //update targetWord here
            observeMatch(currentMatchId)
        }
    }

    private fun calculateWinner(hostScores: PlayerStats, guestScores: PlayerStats) : String {
        //guessed correct words
        val hostGuessedCorrectly = hostScores.guesses.contains(targetWord)
        val guestGuessedCorrectly = guestScores.guesses.contains(targetWord)
        if(hostGuessedCorrectly && !guestGuessedCorrectly){
            return "win"
        }
        else if(!hostGuessedCorrectly && guestGuessedCorrectly) {
            return "loss"
        }
        //can simplify but it's easier to read
        else if(!hostGuessedCorrectly && !guestGuessedCorrectly){
            return "tie"
        }

        //both guessed correctly, tiebreaker will be number of guesses
        val hostNumGuesses = hostScores.guesses.size
        val guestNumGuesses = guestScores.guesses.size

        if(hostNumGuesses < guestNumGuesses){
            return "win"
        }
        else if(hostNumGuesses > guestNumGuesses){
            return "loss"
        }
        else{
            return "tie"
        }
    }

    private fun observeMatch(matchId: String){
        matchObservationJob?.cancel()
        matchObservationJob = viewModelScope.launch {
            dbHelper.listenToMatch(matchId).collect{ match ->
                //this will probably be used to update opponent stuff
                //we also need to update host here because this is the first time we get info about host
                Log.d("ViewModel", "match status: ${match?.matchId} " +
                        "\n match winner: ${match?.hostWinner}" )
                if(match != null){
                    isHost = (match.hostId == currentAuthUser.uid)
                    //sync the targetWord
                    targetWord = match.targetWord
                    //observe scores
                    if(match.hostScores.finished && match.guestScores.finished){
                        //we need to store winner because this will also get triggered on quit
                        viewModelScope.launch {
                            dbHelper.updateWinner(matchId,
                                calculateWinner(match.hostScores, match.guestScores)
                            )
                        }
                    }
                    //observe winner
                    if(match.hostWinner != null && userIsWinner.value == null){
                        //cancel timer as well once finished
                        countDownTimer?.cancel()
                        if((isHost && match.hostWinner == "win") ||
                            (!isHost && match.hostWinner == "loss")){
                            _userIsWinner.value = "win"
                        }
                        else if (match.hostWinner == "tie"){
                            _userIsWinner.value = "tie"
                        }
                        else{
                            _userIsWinner.value = "loss"
                        }
                        updateStats()
                    }
                }
                _match.value = match
            }
        }
    }

    fun onReadyClicked(isReady: Boolean){
        dbHelper.toggleReady(currentMatchId, isHost, isReady)
    }

    fun onStartGameClicked(){
        Log.d("ViewModel", currentMatchId)
        dbHelper.startGame(currentMatchId)
    }

    //resets everything for the viewmodel in terms of match state
    //only resets things you can't read off the db
    //this function might be a bit spaghetti
    fun reset(){
        _guesses.value = emptyList()
        _userIsWinner.value = null
        _timeLeft.value = null
        _finished.value = false
        _match.value = null
        //targetWord = ""
        //currentMatchId = ""
        //isHost = false
        countDownTimer?.cancel()
        //Log.d("ViewModel", _userIsWinner.value.toString())
    }

    fun leaveLobby(){
        reset()
        matchObservationJob?.cancel()
        matchObservationJob = null
        viewModelScope.launch {
            dbHelper.leaveLobby(currentMatchId, currentAuthUser.uid)
        }
    }

    //this will also count as a loss
    fun leaveGame(addLoss: Boolean) {
        Log.d("ViewModel", "Loss Added? $addLoss")
        Log.d("ViewModel", "isHost? $isHost")
        Log.d("ViewModel", "match? ${match.value?.matchId}")
        viewModelScope.launch {
            if (addLoss) {
                dbHelper.updateWinner(
                    currentMatchId,
                    if (isHost) "loss" else "win"
                )
                _userIsWinner.value = "loss"
                updateStats()
            }
            //as you can see concurrency nightmare
        }.invokeOnCompletion {
            leaveLobby()
        }
    }

    //make sure you leave after exit and clear lobby
    override fun onCleared() {
        super.onCleared()
        leaveGame(addLoss = true)
    }

    //timer
    //have a parameter just in case we want to change timer length
    fun startTimer(){
        var startingMilliseconds = 300L * 1000L
        //var startingMilliseconds = 5L * 1000L
        if(_timeLeft.value != null){
            startingMilliseconds = _timeLeft.value!! * 1000
        }
        countDownTimer?.cancel() //if there is a countdown timer we should cancel
        countDownTimer = object : CountDownTimer(startingMilliseconds,
            1000){
            override fun onTick(millisUntilFinished: Long){
                val secondsLeft = millisUntilFinished / 1000
                _timeLeft.value = secondsLeft
            }

            override fun onFinish() {
                setFinished()
            }
        }.start()
    }

    fun updateScores(){
        val matchValue = match.value
        val newPlayerStats = PlayerStats(
            guesses = guessesValue,
            finished = _finished.value ?: false,
            uid = currentAuthUser.uid
        )
        if(matchValue != null){
            dbHelper.updateScores(currentMatchId, isHost, newPlayerStats)
        }
    }

    //ALL STATS STUFF
    //this actually should be a suspend fun
    suspend fun updateStats(){
        Log.d("ViewModel", "Updating stats....")
        //viewModelScope.launch {
        val userDoc = userDbHelper.getUserProfile(currentAuthUser.uid)
        val numOfGuesses = guessesValue.size
        val lastGuess = if (numOfGuesses == 0) "" else guessesValue[numOfGuesses - 1]
        //update guess distribution
        val newGuessDistribution = userDoc?.guessDistribution?.toMutableList() ?: MutableList(7) {0}
        if(lastGuess != targetWord){
            Log.d("MainViewModel", "lastGuess: $lastGuess targetWord: $targetWord")
            newGuessDistribution[0]++
        }
        else{
            Log.d("MainViewModel", "numberOfGuesses: $numOfGuesses")
            newGuessDistribution[numOfGuesses]++
        }
        userDoc?.guessDistribution = newGuessDistribution
        //update wins
        when (userIsWinner.value) {
            "win" -> {
                userDoc?.wins++
                userDoc?.winStreak++
                userDoc?.maxWinStreak = max(userDoc.winStreak, userDoc.maxWinStreak)
            }
            "tie" -> {
                userDoc?.draws++
                userDoc?.winStreak = 0
            }
            "loss" -> {
                userDoc?.losses++
                userDoc?.winStreak = 0
            }
        }
        userDbHelper.updateUserProfile(currentAuthUser.uid,
            userDoc ?: UserDocument())
        _authUserDoc.postValue(userDoc ?: UserDocument())
        //}
    }

    //TODO: this could also be refactored out
    fun refreshStats(){
        viewModelScope.launch {
            val userDoc = userDbHelper.getUserProfile(currentAuthUser.uid)
            _authUserDoc.postValue(userDoc ?: UserDocument())
        }
    }

    fun setFinished(){
        _finished.value = true
        updateScores()
    }

    fun updateChat(chatMessage : String){
        val prevChat = match.value?.chatMessages ?: ""
        val formattedChatMessage = if(isHost){
            "\n${currentAuthUser.name} (Host): $chatMessage"
        }
        else{
            "\n${currentAuthUser.name} (Guest): $chatMessage"
        }
        val newChat = prevChat + formattedChatMessage
        viewModelScope.launch {
            dbHelper.updateChat(currentMatchId, newChat)
        }
    }
}