package com.andrew.lu.wordleonline

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.andrew.lu.wordleonline.databinding.FragmentStartScreenBinding

class StartScreenFragment : Fragment(R.layout.fragment_start_screen) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentStartScreenBinding.bind(view)
        binding.multiplayerButton.setOnClickListener {
            val navController = findNavController()
            val action = StartScreenFragmentDirections.actionStartScreenToLobby()
            navController.navigate(action)
        }

        binding.profileButton.setOnClickListener {
            val navController = findNavController()
            val action = StartScreenFragmentDirections.actionStartScreenToProfile()
            navController.navigate(action)
        }
    }
}