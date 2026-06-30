package com.example.services

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingSpeech: String? = null
    private var speechRate = 1.0f

    init {
        try {
            // Context needs to be the application context to avoid leaks
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e("TtsManager", "Error initializing TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TtsManager", "English language is not supported or missing metadata")
                tts?.setLanguage(Locale.getDefault())
            }
            isReady = true
            tts?.setSpeechRate(speechRate)
            
            pendingSpeech?.let {
                speak(it, true)
                pendingSpeech = null
            }
        } else {
            Log.e("TtsManager", "TextToSpeech initialization failed with status: $status")
        }
    }

    fun speak(text: String, interrupt: Boolean = true) {
        if (!isReady) {
            pendingSpeech = text
            return
        }

        val queueMode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        try {
            tts?.speak(text, queueMode, null, "SightLoopUtteranceId")
        } catch (e: Exception) {
            Log.e("TtsManager", "Failed to play speech: $text", e)
        }
    }

    fun setSpeechSpeed(rate: Float) {
        speechRate = rate
        if (isReady) {
            try {
                tts?.setSpeechRate(rate)
            } catch (e: Exception) {
                Log.e("TtsManager", "Failed to set speech rate: $rate", e)
            }
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e("TtsManager", "Error stopping TTS output", e)
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("TtsManager", "Error shutting down TTS", e)
        }
    }
}
