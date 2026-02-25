package com.example.gettingrichapp.model

enum class Action {
    HIT, STAND, DOUBLE, SPLIT, SURRENDER
}

data class Advice(
    val action: Action,
    val playerCards: List<Card>,
    val dealerUpcard: Card?,
    val handEvaluation: HandEvaluation?,
    val runningCount: Int = 0,
    val trueCount: Double = 0.0
)
