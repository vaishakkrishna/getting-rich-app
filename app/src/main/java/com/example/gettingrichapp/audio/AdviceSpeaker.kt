package com.example.gettingrichapp.audio

import com.example.gettingrichapp.model.Advice

interface AdviceSpeaker {
    fun initialize()
    fun speak(advice: Advice)
    var includeCountInSpeech: Boolean
    fun release()
}
