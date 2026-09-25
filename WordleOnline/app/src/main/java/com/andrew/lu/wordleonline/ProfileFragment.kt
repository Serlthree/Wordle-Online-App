package com.andrew.lu.wordleonline

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.andrew.lu.wordleonline.databinding.FragmentProfileBinding
import com.patrykandpatrick.vico.views.cartesian.CartesianChart
import com.patrykandpatrick.vico.views.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.views.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.views.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.views.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.views.cartesian.data.columnSeries
import com.patrykandpatrick.vico.views.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.views.common.Fill
import com.patrykandpatrick.vico.views.common.component.LineComponent
import com.patrykandpatrick.vico.views.common.component.TextComponent
import kotlinx.coroutines.launch
import kotlin.getValue

class ProfileFragment : Fragment(R.layout.fragment_profile){
    private val viewModel : MainViewModel by activityViewModels()
    private val modelProducer = CartesianChartModelProducer()

    private fun showChangeUserNameDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Enter New Username")

        val input = EditText(requireContext())
        input.inputType = EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME

        // Add a framelayout to actually set margins properly
        val container = android.widget.FrameLayout(requireContext())
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(60, 20, 60, 20)

        container.addView(input, params)

        builder.setView(container)

        builder.setPositiveButton("OK"){ dialog, _, ->
            val newName = input.text.toString()
            viewModel.setName(newName)
        }
        builder.setNegativeButton("Cancel"){ dialog, _, ->
            dialog.cancel()
        }

        builder.show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentProfileBinding.bind(view)

        //refresh profile here
        viewModel.refreshStats()
        viewModel.authUserDoc.observe(viewLifecycleOwner) {userStats ->
            //just bind everything
            var winRate = 0
            if (userStats.wins + userStats.losses > 0) {
                winRate = (userStats.wins * 100) / (userStats.wins + userStats.losses)
            }
            val gamesPlayed = userStats.wins + userStats.losses + userStats.draws
            var totalGuesses = 0
            for ((guessNum, timesGuessed) in userStats.guessDistribution.withIndex()) {
                totalGuesses += guessNum * timesGuessed
            }
            var avgGuesses = 0.0f
            if (gamesPlayed - userStats.guessDistribution[0] > 0) {
                avgGuesses = totalGuesses.toFloat() / (gamesPlayed.toFloat() - userStats.guessDistribution[0])
            }

            binding.tvWins.text = userStats.wins.toString()
            binding.tvDraws.text = userStats.draws.toString()
            binding.tvLosses.text = userStats.losses.toString()
            binding.tvWinRate.text = "$winRate%"

            binding.tvCurrentStreak.text = userStats.winStreak.toString()
            binding.tvMaxStreak.text = userStats.maxWinStreak.toString()
            binding.tvGamesPlayed.text = gamesPlayed.toString()
            binding.tvAvgGuesses.text = "%.1f".format(avgGuesses)

            binding.homeBtn.setOnClickListener {
                val navController = findNavController()
                navController.navigateUp()
            }

            binding.signOutBtn.setOnClickListener {
                val navController = findNavController()
                navController.navigateUp()
                viewModel.signOut()
            }

            binding.changeNameBtn.setOnClickListener {
                showChangeUserNameDialog()
            }

            //for the chart
            //need to define line styling for it to show up
            val labelComponent = TextComponent(
                color = ContextCompat.getColor(requireContext(), android.R.color.black)
                // Optional: add textSizeSp = 12f, typeface, etc.
            )
            val lineComponent = LineComponent(
                fill = Fill(ContextCompat.getColor(requireContext(), android.R.color.darker_gray)),
                thicknessDp = 1f
            )


            val chartView = binding.chartView
            val xLabels = listOf("DNF", "1", "2", "3", "4", "5", "6")
            val xFormatter = CartesianValueFormatter { _, x, _ -> xLabels.getOrNull(x.toInt()) ?: "" }
            val yFormatter = CartesianValueFormatter { _, y, _ -> y.toInt().toString() }
            chartView.chart = CartesianChart(
                ColumnCartesianLayer(
                    ColumnCartesianLayer.ColumnProvider.series(
                        LineComponent(
                            fill = Fill(ContextCompat.getColor(requireContext(), R.color.wordle_green)),
                            thicknessDp = 16f
                        )
                    )
                ),
                startAxis = VerticalAxis.start(
                    label = labelComponent,
                    line = lineComponent,
                    tick = lineComponent,
                    valueFormatter = yFormatter,
                    itemPlacer = VerticalAxis.ItemPlacer.step(step = { 1.0 })
                ),
                bottomAxis = HorizontalAxis.bottom(
                    label = labelComponent,
                    line = lineComponent,
                    tick = lineComponent,
                    valueFormatter = xFormatter,
                    itemPlacer = HorizontalAxis.ItemPlacer.segmented()
                )
            )

            chartView.modelProducer = modelProducer

            viewLifecycleOwner.lifecycleScope.launch {
                modelProducer.runTransaction {
                    columnSeries {
                        series(*userStats.guessDistribution.toTypedArray())
                    }
                }
            }
        }
    }
}