package com.example.gettingrichapp.model

data class HandEvaluation(
    val cards: List<Card>,
    val hardTotal: Int,
    val softTotal: Int?,  // null if no ace or if soft total > 21
    val isSoft: Boolean,
    val isPair: Boolean,
    val isBlackjack: Boolean,
    val pairValue: CardValue? = null
)
