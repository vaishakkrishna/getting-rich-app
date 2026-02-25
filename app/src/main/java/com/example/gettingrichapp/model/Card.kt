package com.example.gettingrichapp.model

enum class Suit {
    HEARTS, DIAMONDS, CLUBS, SPADES
}

enum class CardValue(val numericValue: Int, val hiLoValue: Int) {
    TWO(2, 1),
    THREE(3, 1),
    FOUR(4, 1),
    FIVE(5, 1),
    SIX(6, 1),
    SEVEN(7, 0),
    EIGHT(8, 0),
    NINE(9, 0),
    TEN(10, -1),
    JACK(10, -1),
    QUEEN(10, -1),
    KING(10, -1),
    ACE(11, -1) // 11 or 1, handled by hand evaluation logic
}

data class Card(
    val value: CardValue,
    val suit: Suit
)
