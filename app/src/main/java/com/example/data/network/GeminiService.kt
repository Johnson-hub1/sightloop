package com.example.data.network

import com.example.BuildConfig
import com.example.data.model.GeminiNavigationResponse
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Request Models ---

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String // Base64 encoding
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = "application/json",
    val temperature: Float? = 0.4f
)

// --- Response Models ---

@JsonClass(generateAdapter = true)
data class GeminiRawResponse(
    val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content?
)

// --- Retrofit Interface ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GeminiRawResponse
}

// --- Client Factory ---

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }

    val jsonAdapter = moshi.adapter(GeminiNavigationResponse::class.java)
}

// --- Helper class to build Prompts ---

object Prompts {
    val SYSTEM_INSTRUCTION = """
        You are SightLoop, an advanced AI navigate assistant for individuals with severe visual impairment or blindness.
        Your goal is to parse the user's environment in detail using the provided camera image, and produce descriptions optimized for acoustic navigation and haptic risk assessments.
        
        Analyze the image thoroughly. Identify:
        1. General scene explanation: What is ahead of the user (e.g. hallway, living room, street side, busy subway, etc.).
        2. Immediate physical obstacles: Anything directly in their pathway (near range 1-2 meters).
        3. Potential Hazards: Staircases, drop-offs, open doors, low-hanging tree branches, wires, moving cars/people, clutter, steps, uneven floor.
        4. Clear pathways: Where they can walk safely (e.g., "Left path is clear", "Slightly turn right to avoid chair").
        
        You MUST respond in a strict JSON format with the following keys:
        - "description": A concise, clear 1-2 sentence description of the room or environment that can be read out immediately. Keep speech highly descriptive yet brief so the user isn't overwhelmed.
        - "hazards": A list of detected hazards or potential obstacles, each containing:
            - "name": Brief label (e.g. "coffee table", "sliding stairs")
            - "distance": String ("near", "medium", "far")
            - "severity": String ("LOW", "MEDIUM", "HIGH").
        - "navigation_guidance": Concise audio navigation instruction directing their next step. (e.g., "Step forward, the walkway is clear", or "Freeze! Obstruction directly ahead, side-step 1 meter to your right").
        - "vibration_intensity": An overall haptic alert rating mapping to physical dangers in the immediate central path:
            - "HIGH": Immediate high severity obstacles within 1 meter (stairs down, wall, moving vehicle, closed door in path).
            - "MEDIUM": Notable obstacles in the near path (chair, backpack on floor, table corner, person standing).
            - "LOW": Distant or minor layout changes (rug transition, far doorway).
            - "NONE": Safe, clear open path.
            
        Ensure your entire output is valid JSON matching this schema exactly.
    """.trimIndent()

    val NAVIGATION_PROMPT = "Explain the environment in front of me and assess any tactile hazards. Highlight critical guidance for navigation."
}
