package com.andrew.lu.wordleonline

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.andrew.lu.wordleonline.databinding.FragmentOpponentBoardBinding
import kotlin.getValue

//a lot of this is just copied from GameBoardFragment
class OpponentBoardFragment : Fragment(R.layout.fragment_opponent_board) {
    private val wordleGridViews = Array(6) { arrayOfNulls<TextView>(5) }
    private lateinit var binding : FragmentOpponentBoardBinding
    private val viewModel : MainViewModel by activityViewModels()

    //helper functions to set colors (greatly simplified logic)
    private fun setCorrect(row: Int, col: Int){
        wordleGridViews[row][col]?.setBackgroundResource(R.drawable.cell_correct)
    }

    private fun setPresent(row: Int, col: Int){
        wordleGridViews[row][col]?.setBackgroundResource(R.drawable.cell_present)
    }

    private fun setAbsent(row: Int, col: Int){
        wordleGridViews[row][col]?.setBackgroundResource(R.drawable.cell_absent)
    }

    private fun drawGuesses(guesses : List<String>){
        val targetWord = viewModel.targetWord
        for ((row, guess) in guesses.withIndex()){
            //two pass here, one to set correct values, other to set yellows and grays
            val charMap = IntArray(26)
            for (col in 0 until 5){
                if(targetWord[col] == guess[col]){
                    setCorrect(row, col)
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
                    setPresent(row, col)
                    charMap[guess[col] - 'a']--
                }
                else{
                    setAbsent(row, col)
                }
            }
        }
    }

    private fun sendMessage(){
        val txt = binding.chatET.text.toString()
        viewModel.updateChat(txt)
        //reset text
        binding.chatET.text.clear()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentOpponentBoardBinding.bind(view)

        //programmatically create the grid layout
        val wordleBoard = binding.opponentGridContainer
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

        //draw the previous guesses
        //we should always have playerstats
        viewModel.match.observe(viewLifecycleOwner){
            if(viewModel.isHost){
                drawGuesses(it?.guestScores!!.guesses)
            }
            else{
                drawGuesses(it?.hostScores!!.guesses)
            }

            //chat stuff
            binding.chatTextView.text = it.chatMessages
        }

        //timer function is copied
        viewModel.timeLeft.observe(viewLifecycleOwner){
            binding.timer.text = "Time Left: $it"
        }

        //navigation
        binding.returnToBoardBtn.setOnClickListener {
            val navController = findNavController()
            navController.navigateUp()
        }

        //edittext for chat
        binding.sendBtn.setOnClickListener {
            sendMessage()
        }

        //makes the enter button a send
        binding.chatET.setOnEditorActionListener { view, actionId, event ->
            if(actionId == EditorInfo.IME_ACTION_SEND){
                sendMessage()
                true
            }
            else{
                false
            }
        }
    }
}