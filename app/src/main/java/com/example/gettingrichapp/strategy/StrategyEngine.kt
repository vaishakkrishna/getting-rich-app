package com.example.gettingrichapp.strategy

import com.example.gettingrichapp.model.Action
import com.example.gettingrichapp.model.Card
import com.example.gettingrichapp.model.HandEvaluation

interface StrategyEngine {
    fun recommend(playerHand: HandEvaluation, dealerUpcard: Card, handSize: Int): Action
}
