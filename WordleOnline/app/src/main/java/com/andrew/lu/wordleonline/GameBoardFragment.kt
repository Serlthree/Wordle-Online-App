package com.andrew.lu.wordleonline

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.activity.addCallback
import androidx.core.animation.Animator
import androidx.core.animation.AnimatorSet
import androidx.core.animation.ObjectAnimator
import androidx.core.animation.AnimatorListenerAdapter
import com.andrew.lu.wordleonline.databinding.FragmentGameBoardBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.util.Locale.getDefault

//NOTE: you get the loss/win/draw popup if you view the opponent board after the game is over
class GameBoardFragment : Fragment(R.layout.fragment_game_board) {
    //variables
    private val wordleGridViews = Array(6) {arrayOfNulls<TextView>(5)}
    private val buttonViews = mutableMapOf<String, Button>()
    private var curRow = 0
    private var curCol = 0
    private val curGuess = StringBuilder()
    private var countCorrect = 0
    //adds loss if you quit early
    private var addLoss = true
    private val viewModel : MainViewModel by activityViewModels()
    private lateinit var binding : FragmentGameBoardBinding
    //finished will be a variable here
    private var isFinished = false


    private fun onKeyPressed(key: String){
        //disable if finished
        if(!isFinished) {
            if (key == "ENTER" && curCol == 5) {
                val validGuess = viewModel.submitGuess(curGuess.toString().lowercase(getDefault()))
                if (validGuess) {
                    curGuess.clear()
                    curCol = 0
                    curRow++
                } else {
                    Snackbar.make(
                        requireView(),
                        "That word is not in the dictionary. Try Again",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            } else if (key == "DELETE" && curCol > 0) {
                curCol-- //remember 0 actually means something here
                wordleGridViews[curRow][curCol]?.setBackgroundResource(R.drawable.cell_border)
                wordleGridViews[curRow][curCol]?.text = ""
                curGuess.deleteAt(curGuess.lastIndex)
            } else if (key != "DELETE" && key != "ENTER" && curCol < 5) {
                wordleGridViews[curRow][curCol]?.setBackgroundResource(R.drawable.cell_guess)
                wordleGridViews[curRow][curCol]?.text = key
                curCol++
                curGuess.append(key)
            }
        }
    }

    private fun addButtonsToRow(row: List<String>, layout: LinearLayout){
        for (key in row) {
            val button = Button(context).apply {
                text = key
                layoutParams = LinearLayout.LayoutParams(
                    0, // Width is 0 because we use weight
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    if (key == "ENTER" || key == "DELETE") 1.5f else 1.0f // Wider buttons
                ).apply {
                    setMargins(4, 4, 4, 4)
                }

                // Optional: Apply styling
                setPadding(0, 0, 0, 0)

                setOnClickListener { onKeyPressed(key) }
            }
            layout.addView(button)
            buttonViews[key] = button
        }
    }

    fun flipTile(view: TextView, key: String, state: String) {
        val flipOut = ObjectAnimator.ofFloat(view, "rotationY", 0f, 90f).apply {
            duration = 150
        }
        val flipIn = ObjectAnimator.ofFloat(view, "rotationY", -90f, 0f).apply {
            duration = 150
        }
        flipIn.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                when (state) {
                    "correct" -> view.setBackgroundResource(R.drawable.cell_correct)
                    "present" -> view.setBackgroundResource(R.drawable.cell_present)
                    "absent" -> view.setBackgroundResource((R.drawable.cell_absent))
                }
                view.text = key
                view.setTextColor(Color.WHITE)
            }
        })
        AnimatorSet().apply {
            playSequentially(flipOut, flipIn)
            start()
        }
    }

    //helper functions to set colors
    private fun setCorrect(row: Int, col: Int, key : String){
        if(row == curRow) {
            flipTile(wordleGridViews[row][col]!!, key, "correct")
        }
        else if(row < curRow){
            wordleGridViews[row][col]?.setBackgroundResource(R.drawable.cell_correct)
            wordleGridViews[row][col]?.text = key
            wordleGridViews[row][col]?.setTextColor(Color.WHITE)
        }
        //we need uppercase because our buttons are uppercase
        buttonViews[key.uppercase()]?.backgroundTintList =
            ColorStateList.valueOf(0xFF6CA965.toInt())
    }

    private fun setPresent(row: Int, col: Int, key : String){
        if(row == curRow) {
            flipTile(wordleGridViews[row][col]!!, key, "present")
        }
        else if(row < curRow){
            wordleGridViews[row][col]?.setBackgroundResource(R.drawable.cell_present)
            wordleGridViews[row][col]?.text = key
            wordleGridViews[row][col]?.setTextColor(Color.WHITE)
        }
        val button = buttonViews[key.uppercase()]
        val currentColor = button?.backgroundTintList?.defaultColor
        if (currentColor != 0xFF6CA965.toInt()) {
            button?.backgroundTintList = ColorStateList.valueOf(0xFFC8B653.toInt())
        }
    }

    private fun setAbsent(row: Int, col: Int, key : String){
        if(row == curRow) {
            flipTile(wordleGridViews[row][col]!!, key, "absent")
        }
        else if(row < curRow){
            wordleGridViews[row][col]?.setBackgroundResource(R.drawable.cell_absent)
            wordleGridViews[row][col]?.text = key
            wordleGridViews[row][col]?.setTextColor(Color.WHITE)
        }
        //only set if not green or yellow
        val button = buttonViews[key.uppercase()]
        val currentColor = button?.backgroundTintList?.defaultColor
        if (currentColor != 0xFF6CA965.toInt() && currentColor != 0xFFC8B653.toInt()) {
            button?.backgroundTintList = ColorStateList.valueOf(0xFF787C7F.toInt())
        }
    }

    private fun drawGuesses(guesses : List<String>){
        val targetWord = viewModel.targetWord
        for ((row, guess) in guesses.withIndex()){
            //two pass here, one to set correct values, other to set yellows and grays
            val charMap = IntArray(26)
            countCorrect = 0
            for (col in 0 until 5){
                if(targetWord[col] == guess[col]){
                    setCorrect(row, col, guess[col].toString())
                    countCorrect++
                }
                else{
                    charMap[targetWord[col] - 'a']++
                }
            }
            //FAQ: why can't we one pass, we need info on the frequency of the correct values
            //to set the correct display
            for(col in 0 until 5){
                if(targetWord[col] == guess[col]) continue
                else if (charMap[guess[col] - 'a'] > 0){
                    setPresent(row, col, guess[col].toString())
                    charMap[guess[col] - 'a']--
                }
                else{
                    setAbsent(row, col, guess[col].toString())
                }
            }
            if(countCorrect == 5 || curRow >= 5){
                //player finished
                viewModel.setFinished()
            }
        }
    }

    //confirmation stuff
    private fun showQuitConfirmation(){
        if(addLoss) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Quit Game?")
                .setMessage("Leaving will count as a loss.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Quit") { _, _ ->
                    quitGame()
                }
                .show()
        }
        else{
            quitGame()
        }
    }

    private fun showWin(){
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("You Win!")
            .setMessage("Congratulations! You Won!\nThe word was: ${viewModel.targetWord}")
            .setPositiveButton("Cool!", null)
            .show()
    }

    private fun showDraw(){
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("You Drew!")
            .setMessage("You Drew!\nThe word was: ${viewModel.targetWord}")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showLoss(){
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("You Lost!")
            .setMessage("You Lost! Try again next time!\nThe word was: ${viewModel.targetWord}")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun quitGame(){
        //have viewmodel leave game
        viewModel.leaveGame(addLoss)
        //get out of the game
        val navController = findNavController()
        val action = GameBoardFragmentDirections.actionGameBoardToStartScreen()
        navController.navigate(action)
    }

    private fun seeOpponentsBoard(){
        val navController = findNavController()
        val action = GameBoardFragmentDirections.actionGameBoardToOpponentBoard()
        navController.navigate(action)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentGameBoardBinding.bind(view)
        curCol = 0 //reset by just setting col to 0
        curGuess.clear() // also reset this

        //programmatically create the grid layout
        val wordleBoard = binding.wordleGridContainer
        for (row in 0 until 6){
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            for(col in 0 until 5){
                val cellView = layoutInflater.inflate(R.layout.grid_single_cell,
                    rowLayout, false)
                //have to use findviewbyid here because there's no binding
                wordleGridViews[row][col] = cellView.findViewById(R.id.textViewLetter)
                rowLayout.addView(cellView)
            }
            wordleBoard.addView(rowLayout)
        }

        //programmatically create the keyboard layout
        val row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
        val row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L")
        val row3 = listOf("DELETE", "Z", "X", "C", "V", "B", "N", "M", "ENTER")
        addButtonsToRow(row1, binding.row1)
        addButtonsToRow(row2, binding.row2)
        addButtonsToRow(row3, binding.row3)

        //draw the previous guesses
        viewModel.guesses.observe(viewLifecycleOwner){
            drawGuesses(it)
        }

        //quit button + android back button
        binding.quitButton.setOnClickListener {
            showQuitConfirmation()
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner){
            showQuitConfirmation()
        }

        //win stuff (observe)
        //NOTE: don't exit, let user exit
        viewModel.userIsWinner.observe(viewLifecycleOwner){
            Log.d("GameBoardFragment", "userIsWinner? $it")
            addLoss = false //don't make person get a loss for leaving after match is over
            when (it) {
                "win" -> {
                    showWin()
                }
                "tie" -> {
                    showDraw()
                }
                "loss" -> {
                    showLoss()
                }
                else -> {
                    //this is null which means a winner isn't decided yet
                    addLoss = true
                }
            }
        }

        //timer stuff (observe)
        //we shouldn't remake if it wins
        if(!isFinished) viewModel.startTimer()

        viewModel.timeLeft.observe(viewLifecycleOwner){
            binding.timer.text = "Time Left: $it"
        }

        viewModel.finished.observe(viewLifecycleOwner){
            isFinished = it
        }

        //add button to look at opponent's board
        binding.seeOpponentBoard.setOnClickListener {
            seeOpponentsBoard()
        }
    }
}