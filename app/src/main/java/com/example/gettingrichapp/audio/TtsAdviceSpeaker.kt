package com.example.gettingrichapp.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import com.example.gettingrichapp.model.Action
import com.example.gettingrichapp.model.Advice
import java.util.Locale

class TtsAdviceSpeaker(private val context: Context) : AdviceSpeaker {

    private var tts: TextToSpeech? = null
    override var includeCountInSpeech: Boolean = false

    override fun initialize() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
    }

    override fun speak(advice: Advice) {
        val engine = tts ?: return

        val actionText = when (advice.action) {
            Action.HIT -> "Hit"
            Action.STAND -> "Stand"
            Action.DOUBLE -> "Double down"
            Action.SPLIT -> "Split"
            Action.SURRENDER -> "Surrender"
        }

        val utterance = if (includeCountInSpeech) {
            "$actionText. Count ${advice.runningCount}."
        } else {
            actionText
        }

        engine.speak(utterance, TextToSpeech.QUEUE_ADD, null, "advice_${System.currentTimeMillis()}")
    }

    override fun release() {
        tts?.shutdown()
        tts = null
    }
}
