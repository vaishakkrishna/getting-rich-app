package com.example.gettingrichapp

import android.content.Context
import com.example.gettingrichapp.audio.AdviceSpeaker
import com.example.gettingrichapp.audio.TtsAdviceSpeaker
import com.example.gettingrichapp.camera.DatFrameProvider
import com.example.gettingrichapp.camera.FrameProvider
import com.example.gettingrichapp.counting.CardCounter
import com.example.gettingrichapp.counting.HiLoCardCounter
import com.example.gettingrichapp.detection.CardDetector
import com.example.gettingrichapp.detection.TfLiteCardDetector
import com.example.gettingrichapp.glasses.DatGlassesConnection
import com.example.gettingrichapp.glasses.GlassesConnection
import com.example.gettingrichapp.session.BlackjackSession
import com.example.gettingrichapp.session.GameSession
import com.example.gettingrichapp.strategy.BasicStrategyEngine
import com.example.gettingrichapp.strategy.HandEvaluator
import com.example.gettingrichapp.strategy.StrategyEngine

object ServiceLocator {
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    val glassesConnection: GlassesConnection by lazy { DatGlassesConnection(appContext) }

    val frameProvider: FrameProvider by lazy { DatFrameProvider(appContext, glassesConnection) }

    val cardDetector: CardDetector by lazy { TfLiteCardDetector(appContext).also { it.initialize() } }

    val cardCounter: CardCounter by lazy { HiLoCardCounter() }

    val handEvaluator: HandEvaluator by lazy { HandEvaluator }

    val strategyEngine: StrategyEngine by lazy { BasicStrategyEngine() }

    val adviceSpeaker: AdviceSpeaker by lazy { TtsAdviceSpeaker(appContext).also { it.initialize() } }

    val gameSession: GameSession by lazy {
        BlackjackSession(
            frameProvider = frameProvider,
            cardDetector = cardDetector,
            cardCounter = cardCounter,
            handEvaluator = handEvaluator,
            strategyEngine = strategyEngine,
            adviceSpeaker = adviceSpeaker
        )
    }
}
