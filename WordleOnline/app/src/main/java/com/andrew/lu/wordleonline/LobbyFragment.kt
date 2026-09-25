package com.andrew.lu.wordleonline

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.andrew.lu.wordleonline.databinding.FragmentLobbyBinding
import kotlin.getValue
import androidx.core.graphics.toColorInt

class LobbyFragment : Fragment(R.layout.fragment_lobby){
    private val viewModel : MainViewModel by activityViewModels()

    private fun navigateToGame(){
        val navController = findNavController()
        val action = LobbyFragmentDirections.actionLobbyToGameBoard()
        navController.navigate(action)
    }

    private fun quitLobby(){
        viewModel.leaveLobby()
        val navController = findNavController()
        navController.navigateUp()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentLobbyBinding.bind(view)

        binding.leaveLobbyButton.setOnClickListener {
            quitLobby()
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner){
            quitLobby()
        }

        binding.startGameButton.setOnClickListener {
            viewModel.onStartGameClicked()
        }

        binding.readyButton.setOnCheckedChangeListener { button, bool ->
            viewModel.onReadyClicked(bool)
        }

        //this needs to observe viewModel stuff
        viewModel.match.observe(viewLifecycleOwner){match ->
            if(match == null) return@observe
            Log.d("Lobby", "match id ${match.matchId}")

            //update names
            binding.tvPlayer1Name.text = "Host: ${match.hostUsername}"
            if(match.guestId != null) {
                binding.tvPlayer2Name.text = "Guest: ${match.guestUsername}"
                binding.tvPlayer2Name.setTextColor(Color.BLACK)
            }
            else{
                binding.tvPlayer2Name.text = "Waiting for player..."
                binding.tvPlayer2Name.setTextColor(Color.GRAY)
            }

            //update ready
            if(match.hostReady){
                binding.tvPlayer1Status.text = "Ready"
                binding.tvPlayer1Status.setTextColor("#4CAF50".toColorInt())
            }
            else{
                binding.tvPlayer1Status.text = "Not Ready"
                binding.tvPlayer1Status.setTextColor("#F44336".toColorInt())
            }

            if(match.guestReady){
                binding.tvPlayer2Status.text = "Ready"
                binding.tvPlayer2Status.setTextColor("#4CAF50".toColorInt())
            }
            else{
                binding.tvPlayer2Status.text = "Not Ready"
                binding.tvPlayer2Status.setTextColor("#F44336".toColorInt())
            }

            //if both ready unlock the game button
            val bothReady = (match.hostReady && match.guestReady)
            binding.startGameButton.isEnabled = bothReady

            if(match.status == "starting"){
                Log.d("Lobby", "Game is starting")
                navigateToGame()
            }
        }

        viewModel.startMatchmaking()
    }

}