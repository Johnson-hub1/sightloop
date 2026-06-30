package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.model.GeminiNavigationResponse
import com.example.data.model.NavigationDatabase
import com.example.data.model.NavigationLog
import com.example.data.network.*
import com.example.services.HapticManager
import com.example.services.TtsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class NavigationViewModel(application: Application) : AndroidViewModel(application) {

    private val database = NavigationDatabase.getDatabase(application)
    private val logDao = database.navigationLogDao()

    private val ttsManager = TtsManager(application)
    private val hapticManager = HapticManager(application)
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // --- State flows for Jetpack Compose ---
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _isContinuousMode = MutableStateFlow(false)
    val isContinuousMode: StateFlow<Boolean> = _isContinuousMode.asStateFlow()

    private val _isPrototypeMode = MutableStateFlow(true) // Defaults to true for instant functional prototype evaluation
    val isPrototypeMode: StateFlow<Boolean> = _isPrototypeMode.asStateFlow()

    private val _speechSpeed = MutableStateFlow(1.0f)
    val speechSpeed: StateFlow<Float> = _speechSpeed.asStateFlow()

    private val _isFlashEnabled = MutableStateFlow(false)
    val isFlashEnabled: StateFlow<Boolean> = _isFlashEnabled.asStateFlow()

    private val _activeDescription = MutableStateFlow("Tap the center button to inspect your surroundings.")
    val activeDescription: StateFlow<String> = _activeDescription.asStateFlow()

    private val _activeGuidance = MutableStateFlow("Hold your device comfortably height level facing forward.")
    val activeGuidance: StateFlow<String> = _activeGuidance.asStateFlow()

    private val _activeVibrationLevel = MutableStateFlow("NONE")
    val activeVibrationLevel: StateFlow<String> = _activeVibrationLevel.asStateFlow()

    private val _hazardsList = MutableStateFlow<List<com.example.data.model.Hazard>>(emptyList())
    val hazardsList: StateFlow<List<com.example.data.model.Hazard>> = _hazardsList.asStateFlow()

    private val _cameraStatus = MutableStateFlow("Prototype mode active")
    val cameraStatus: StateFlow<String> = _cameraStatus.asStateFlow()

    val navigationHistory = logDao.getAllLogs()

    private var continuousScanJob: Job? = null
    private var simulationIndex = 0

    private val mockEnvironments = listOf(
        GeminiNavigationResponse(
            description = "Living room hallway. A cluttered laptop charger extension cord and a heavy cardboard box lie directly ahead on the ground.",
            hazards = listOf(
                com.example.data.model.Hazard("Power cord extension", "1 meter", "HIGH"),
                com.example.data.model.Hazard("Cardboard package box", "2 meters", "MEDIUM")
            ),
            navigation_guidance = "Stop. Step 1 meter to your left side to safely circumvent the power cord.",
            vibration_intensity = "HIGH"
        ),
        GeminiNavigationResponse(
            description = "Kitchen area with clear marble counters. A tall metal bar stool is positioned slightly to the right of your direct walking pathway.",
            hazards = listOf(
                com.example.data.model.Hazard("Metal bar stool", "1.5 meters", "MEDIUM")
            ),
            navigation_guidance = "Walk straight. Guide yourself slightly to the left side of the room.",
            vibration_intensity = "MEDIUM"
        ),
        GeminiNavigationResponse(
            description = "Busy pedestrian crosswalk. Two individuals are walking ahead of you and a rental electric scooter is passing by quickly from the left.",
            hazards = listOf(
                com.example.data.model.Hazard("Passing scooter", "2 meters", "HIGH"),
                com.example.data.model.Hazard("Pedestrians walking", "3 meters", "LOW")
            ),
            navigation_guidance = "Wait. Scooter passing ahead. Prepare to advance in 4 seconds.",
            vibration_intensity = "HIGH"
        ),
        GeminiNavigationResponse(
            description = "Modern carpeted office lounge. Wide open workspace with no physical obstacles or immediate furniture blockage in your path.",
            hazards = emptyList(),
            navigation_guidance = "Path clear. Move straight forward with safety.",
            vibration_intensity = "NONE"
        )
    )

    init {
        // Initial speech welcoming the user
        viewModelScope.launch {
            delay(1200)
            speakVoiceAnnouncement("Welcome to SightLoop. Functional prototype mode is active by default. Double tap the large center panel to test environment description simulation and tactile haptics.")
        }
    }

    // --- Core Commands ---

    fun togglePrototypeMode() {
        val nextState = !_isPrototypeMode.value
        _isPrototypeMode.value = nextState
        hapticManager.playClick()
        if (nextState) {
            _cameraStatus.value = "Prototype mode active"
            speakVoiceAnnouncement("Prototype simulation mode enabled. Audits will play realistic pre-programmed scenarios.")
        } else {
            _cameraStatus.value = "Camera ready"
            speakVoiceAnnouncement("Live camera mode activated. Real API credentials and live snapshot analysis will be used.")
        }
    }

    fun toggleContinuousScan() {
        val nextState = !_isContinuousMode.value
        _isContinuousMode.value = nextState
        hapticManager.playClick()

        if (nextState) {
            speakVoiceAnnouncement("Continuous scan mode activated. Scanning environment every 7 seconds.")
            startContinuousScanning()
        } else {
            continuousScanJob?.cancel()
            speakVoiceAnnouncement("Continuous scan mode deactivated.")
            hapticManager.stop()
        }
    }

    fun setSpeechRate(rate: Float) {
        _speechSpeed.value = rate
        ttsManager.setSpeechSpeed(rate)
        speakVoiceAnnouncement("Speech speed set to ${getSpeedLabel(rate)}")
    }

    private fun getSpeedLabel(rate: Float): String {
        return when {
            rate < 0.9f -> "Slow"
            rate < 1.2f -> "Normal"
            rate < 1.6f -> "Fast"
            else -> "Aggressive"
        }
    }

    fun toggleCameraFlash() {
        val nextState = !_isFlashEnabled.value
        _isFlashEnabled.value = nextState
        hapticManager.playClick()
        if (nextState) {
            speakVoiceAnnouncement("Flashlight turned on.")
        } else {
            speakVoiceAnnouncement("Flashlight turned off.")
        }
    }

    /**
     * Executes a single, immediate scan audit of the camera snapshot.
     */
    fun performImmediateScan(imageCapture: ImageCapture?) {
        if (_isPrototypeMode.value) {
            performPrototypeScan()
            return
        }
        if (_isProcessing.value) {
            speakVoiceAnnouncement("Still processing preceding request. Please wait.")
            return
        }
        hapticManager.playClick()
        captureAndAnalyze(imageCapture)
    }

    /**
     * High-fidelity simulation scan pipeline for evaluation in prototype environments.
     */
    fun performPrototypeScan() {
        if (_isProcessing.value) {
            speakVoiceAnnouncement("Still processing preceding request. Please wait.")
            return
        }
        _isProcessing.value = true
        _cameraStatus.value = "Auditing surroundings..."
        hapticManager.playProcessingTick()
        speakVoiceAnnouncement("Analyzing room.")

        viewModelScope.launch {
            delay(1800) // realistic rendering and processing delay
            val mockData = mockEnvironments[simulationIndex]
            simulationIndex = (simulationIndex + 1) % mockEnvironments.size
            saveAndDispatchSuccess(mockData)
        }
    }

    private fun startContinuousScanning() {
        continuousScanJob?.cancel()
        continuousScanJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive && _isContinuousMode.value) {
                // Trigger a scan but don't crash the loop if it fails
                try {
                    if (_isPrototypeMode.value) {
                        withContext(Dispatchers.Main) {
                            performPrototypeScan()
                        }
                    } else {
                        // Let the UI layout provide the current imageCapture instance via a callback or binding.
                        // We dispatch a trigger event which the UI handles, or we capture using a held binding.
                        _triggerContinuousCapture.value = System.currentTimeMillis()
                    }
                } catch (e: Exception) {
                    Log.e("NavigationViewModel", "Continuous loop capture trigger error", e)
                }
                delay(7200) // Audits every 7 seconds roughly
            }
        }
    }

    // A simple trigger channel that the Composable screen listens to in order to feed back the ImageCapture instance
    private val _triggerContinuousCapture = MutableStateFlow<Long>(0L)
    val triggerContinuousCapture: StateFlow<Long> = _triggerContinuousCapture.asStateFlow()

    @OptIn(ExperimentalGetImage::class)
    fun captureAndAnalyze(imageCapture: ImageCapture?) {
        if (_isPrototypeMode.value) {
            performPrototypeScan()
            return
        }
        if (imageCapture == null) {
            _cameraStatus.value = "Camera disconnect error"
            speakVoiceAnnouncement("Camera is not initialized. Please verify permissions.")
            return
        }

        _isProcessing.value = true
        _cameraStatus.value = "Auditing surroundings..."
        hapticManager.playProcessingTick()
        speakVoiceAnnouncement("Analyzing room.")

        // Enforce flash mode dynamically before taking picture
        imageCapture.flashMode = if (_isFlashEnabled.value) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF

        imageCapture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    image.close()

                    if (bitmap != null) {
                        dispatchGeminiAnalysis(bitmap)
                    } else {
                        handleFailure("Failed to decode camera frame.")
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("NavigationViewModel", "Camera snapshot failure", exception)
                    handleFailure("Failed to capture image snapshot.")
                }
            }
        )
    }

    /**
     * Transmits the resized snapshot image to the Gemini endpoint.
     */
    private fun dispatchGeminiAnalysis(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resizedBitmap = bitmap.downscaleAndCompress(800)
                val base64Image = resizedBitmap.toBase64()

                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    withContext(Dispatchers.Main) {
                        _isProcessing.value = false
                        hapticManager.stop()
                        _cameraStatus.value = "Gemini key missing"
                        val errorMsg = "Visual system key not loaded. Configure your API key in the secrets panel."
                        _activeDescription.value = errorMsg
                        speakVoiceAnnouncement(errorMsg)
                    }
                    return@launch
                }

                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(
                                Part(text = Prompts.NAVIGATION_PROMPT),
                                Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
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

                if (rawTextJson != null) {
                    val cleanedJson = cleanJson(rawTextJson)
                    val parsedResponse = RetrofitClient.jsonAdapter.fromJson(cleanedJson)

                    if (parsedResponse != null) {
                        saveAndDispatchSuccess(parsedResponse)
                    } else {
                        handleFailure("Parsed instruction schema mismatch.")
                    }
                } else {
                    handleFailure("Empty environment description.")
                }

            } catch (e: Exception) {
                Log.e("NavigationViewModel", "Gemini API exchange failed", e)
                handleFailure("Failed to load environment audit.")
            }
        }
    }

    private suspend fun saveAndDispatchSuccess(data: GeminiNavigationResponse) = withContext(Dispatchers.Main) {
        _isProcessing.value = false
        _cameraStatus.value = "Environment mapped"
        _activeDescription.value = data.description
        _activeGuidance.value = data.navigation_guidance
        _activeVibrationLevel.value = data.vibration_intensity
        _hazardsList.value = data.hazards ?: emptyList()

        // Trigger safety haptics matching danger severity
        hapticManager.playSuccess()
        if (data.vibration_intensity != "NONE") {
            delay(400)
            hapticManager.playHazardWarning(data.vibration_intensity)
        }

        // Format TTS speech readout
        val readoutText = buildString {
            append(data.description)
            append(". Guidance: ")
            append(data.navigation_guidance)
            val hazardSize = data.hazards?.size ?: 0
            if (hazardSize > 0) {
                append(". Identified $hazardSize tactile risk areas.")
            }
        }
        speakVoiceAnnouncement(readoutText)

        // Save session log strictly into SQLite database
        logDao.insertLog(
            NavigationLog(
                timestamp = System.currentTimeMillis(),
                description = data.description,
                hazardsCount = data.hazards?.size ?: 0,
                dangerLevel = data.vibration_intensity,
                directions = data.navigation_guidance
            )
        )
    }

    private fun handleFailure(error: String) {
        viewModelScope.launch(Dispatchers.Main) {
            _isProcessing.value = false
            hapticManager.stop()
            _cameraStatus.value = "Audit failure"
            val fallbackMsg = "$error Please try again."
            _activeDescription.value = fallbackMsg
            speakVoiceAnnouncement("Communication error. Please tap to try again.")
        }
    }

    fun clearLogHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            logDao.clearAllLogs()
            withContext(Dispatchers.Main) {
                hapticManager.playClick()
                speakVoiceAnnouncement("History logs cleared.")
            }
        }
    }

    // --- Helpers ---

    private fun speakVoiceAnnouncement(text: String) {
        ttsManager.speak(text)
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                val rotationDegrees = image.imageInfo.rotationDegrees
                if (rotationDegrees != 0) {
                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                } else {
                    bitmap
                }
            } else null
        } catch (e: Exception) {
            Log.e("NavigationViewModel", "Error converting image proxy to Bitmap", e)
            null
        }
    }

    private fun Bitmap.downscaleAndCompress(maxDimension: Int): Bitmap {
        val width = this.width
        val height = this.height
        if (width <= maxDimension && height <= maxDimension) return this
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
        return Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
    }

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
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

    override fun onCleared() {
        super.onCleared()
        continuousScanJob?.cancel()
        cameraExecutor.shutdown()
        ttsManager.shutdown()
        hapticManager.stop()
    }
}
