package com.example.api

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.example.data.model.GeminiNavigationResponse
import com.example.data.model.NavigationDatabase
import com.example.data.model.NavigationLog
import com.example.data.network.*
import com.example.services.HapticManager
import com.example.services.TtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * SightLoop Developer & Client Integration API.
 * Encapsulates core services including environmental computer vision via Gemini,
 * synchronized high-contrast haptic pulses, text-to-speech audio outputs, and secure SQLite persistence.
 */
class SightLoopApi(private val context: Context) {

    private val database = NavigationDatabase.getDatabase(context)
    private val logDao = database.navigationLogDao()
    private val ttsManager = TtsManager(context)
    private val hapticManager = HapticManager(context)

    /**
     * Executes real-time visual parsing of the camera environment via Gemini.
     * @param bitmap The frame bitmap snapshot from the camera stream.
     * @return Result containing the structured GeminiNavigationResponse.
     */
    suspend fun analyzeEnvironment(bitmap: Bitmap): Result<GeminiNavigationResponse> = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext Result.failure(IllegalStateException("Gemini API Key is not configured in the AI Studio secrets panel."))
            }

            val resizedBitmap = downscaleBitmap(bitmap, 800)
            val base64Data = bitmapToBase64(resizedBitmap)

            val request = GenerateContentRequest(
                contents = listOf(
                    Content(
                        parts = listOf(
                            Part(text = Prompts.NAVIGATION_PROMPT),
                            Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Data))
                        )
                    )
                ),
                generationConfig = GenerationConfig(responseMimeType = "application/json"),
                systemInstruction = Content(
                    parts = listOf(Part(text = Prompts.SYSTEM_INSTRUCTION))
                )
            )

            val response = RetrofitClient.service.generateContent(apiKey, request)
            val rawTextJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: return@withContext Result.failure(Exception("Gemini returned an empty candidate response."))

            val cleanedJson = cleanJson(rawTextJson)
            val parsedResponse = RetrofitClient.jsonAdapter.fromJson(cleanedJson)
                ?: return@withContext Result.failure(Exception("Failed to map JSON schema to GeminiNavigationResponse."))

            // Log session securely into local SQLite Room database
            saveLogToDatabase(parsedResponse)

            Result.success(parsedResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Executes physical vibrational alert pulses matching specified danger levels.
     * @param intensity Must be "NONE", "LOW", "MEDIUM", or "HIGH".
     */
    fun triggerHapticFeedback(intensity: String) {
        hapticManager.playHazardWarning(intensity)
    }

    /**
     * Plays click feedback for primary interactive buttons.
     */
    fun triggerClickHaptic() {
        hapticManager.playClick()
    }

    /**
     * Cancels any active vibrations instantly.
     */
    fun cancelHaptics() {
        hapticManager.stop()
    }

    /**
     * Directs verbal audio directions to the visually impaired user.
     * @param phrase Text layout instructions to play immediately.
     * @param interrupt Interrupt preceding outputs if true.
     */
    fun speakVoiceAnnouncement(phrase: String, interrupt: Boolean = true) {
        ttsManager.speak(phrase, interrupt)
    }

    /**
     * Adjusts the playback voice announcement speech rate.
     * @param rate Floating speed index (e.g. 1.0f is default).
     */
    fun setSpeechPlaybackRate(rate: Float) {
        ttsManager.setSpeechSpeed(rate)
    }

    /**
     * Clean shutdown of the TextToSpeech engine.
     */
    fun destroy() {
        ttsManager.shutdown()
        hapticManager.stop()
    }

    // --- Private Helper Utilities ---

    private suspend fun saveLogToDatabase(response: GeminiNavigationResponse) {
        try {
            logDao.insertLog(
                NavigationLog(
                    timestamp = System.currentTimeMillis(),
                    description = response.description,
                    hazardsCount = response.hazards?.size ?: 0,
                    dangerLevel = response.vibration_intensity,
                    directions = response.navigation_guidance
                )
            )
        } catch (e: Exception) {
            // Suppress background persistence logging errors
        }
    }

    private fun downscaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun cleanJson(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```json")) {
            text = text.substringAfter("```json")
        } else if (text.startsWith("```")) {
            text = text.substringAfter("```")
        }
        if (text.endsWith("```")) {
            text = text.substringBeforeLast("```")
        }
        return text.trim()
    }
}
