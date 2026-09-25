package com.andrew.lu.wordleonline

import android.content.Context

class Repository {
    //fetch data only once
    private val allWords = mutableListOf<String>()
    private val allSolutions = mutableListOf<String>()

    fun fetchAllWords(context: Context) : List<String> {
        if(allWords.isEmpty()){
            allWords.addAll(
                context.resources.openRawResource(R.raw.all_words)
                    .bufferedReader()
                    .readLines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            )
        }
        return allWords
    }

    fun fetchAllSolutions(context: Context): List<String> {
        if(allSolutions.isEmpty()){
            allSolutions.addAll(
                context.resources.openRawResource(R.raw.all_solutions)
                    .bufferedReader()
                    .readLines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            )
        }
        return allSolutions
    }

    fun generateRandomSolution(context: Context) : String{
        if(allSolutions.isEmpty()){
            fetchAllSolutions(context)
        }
        return allSolutions.random()
    }

    fun checkIfWordIsValid(context: Context, word: String) : Boolean{
        if(allWords.isEmpty()){
            fetchAllWords(context)
        }
        return allWords.contains(word)
    }
}