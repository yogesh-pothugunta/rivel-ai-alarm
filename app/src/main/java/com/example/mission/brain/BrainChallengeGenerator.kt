package com.example.mission.brain

import kotlin.random.Random

data class BrainQuestion(
    val id: Int,
    val type: String, // "ARITHMETIC", "SEQUENCE", "LOGIC"
    val questionText: String,
    val options: List<String>,
    val correctAnswer: String,
    val hint: String = ""
)

object BrainChallengeGenerator {

    fun generateChallengeSet(difficulty: String): List<BrainQuestion> {
        val count = when (difficulty.uppercase()) {
            "EASY" -> 2
            "HARD" -> 4
            else -> 3 // MEDIUM or ADAPTIVE default
        }

        val questions = mutableListOf<BrainQuestion>()
        for (i in 1..count) {
            when (i % 2) {
                1 -> questions.add(generateArithmeticQuestion(i, difficulty))
                else -> questions.add(generateSequenceQuestion(i, difficulty))
            }
        }
        return questions
    }

    private fun generateArithmeticQuestion(id: Int, difficulty: String): BrainQuestion {
        val random = Random.Default
        val questionText: String
        val correctValue: Int

        when (difficulty.uppercase()) {
            "EASY" -> {
                val a = random.nextInt(12, 45)
                val b = random.nextInt(8, 35)
                val isAdd = random.nextBoolean()
                if (isAdd) {
                    questionText = "$a + $b = ?"
                    correctValue = a + b
                } else {
                    val max = maxOf(a, b)
                    val min = minOf(a, b)
                    questionText = "$max - $min = ?"
                    correctValue = max - min
                }
            }
            "HARD" -> {
                val a = random.nextInt(12, 25)
                val b = random.nextInt(4, 12)
                val c = random.nextInt(15, 60)
                questionText = "($a × $b) - $c = ?"
                correctValue = (a * b) - c
            }
            else -> { // MEDIUM
                val a = random.nextInt(15, 65)
                val b = random.nextInt(15, 45)
                val c = random.nextInt(5, 20)
                questionText = "$a + $b - $c = ?"
                correctValue = a + b - c
            }
        }

        val correctStr = correctValue.toString()
        val options = mutableSetOf(correctStr)
        while (options.size < 4) {
            val delta = random.nextInt(-15, 16)
            if (delta != 0) {
                options.add((correctValue + delta).toString())
            }
        }

        return BrainQuestion(
            id = id,
            type = "ARITHMETIC",
            questionText = questionText,
            options = options.toList().shuffled(),
            correctAnswer = correctStr,
            hint = "Focus your calculation"
        )
    }

    private fun generateSequenceQuestion(id: Int, difficulty: String): BrainQuestion {
        val random = Random.Default
        val start = random.nextInt(2, 15)
        val step = when (difficulty.uppercase()) {
            "EASY" -> random.nextInt(2, 6)
            "HARD" -> random.nextInt(4, 12)
            else -> random.nextInt(3, 8)
        }

        val seq = listOf(
            start,
            start + step,
            start + (step * 2),
            start + (step * 3)
        )
        val nextVal = start + (step * 4)

        val questionText = "${seq.joinToString(", ")}, [ ? ]"
        val correctStr = nextVal.toString()

        val options = mutableSetOf(correctStr)
        while (options.size < 4) {
            val offset = random.nextInt(-10, 11)
            if (offset != 0) {
                options.add((nextVal + offset).toString())
            }
        }

        return BrainQuestion(
            id = id,
            type = "SEQUENCE",
            questionText = questionText,
            options = options.toList().shuffled(),
            correctAnswer = correctStr,
            hint = "Find the step pattern"
        )
    }
}
