package com.example.data.remote

import android.content.Context
import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Streaming

@JsonClass(generateAdapter = true)
data class CartesiaVoiceSpec(
    @Json(name = "mode") val mode: String = "id",
    @Json(name = "id") val id: String
)

@JsonClass(generateAdapter = true)
data class CartesiaOutputFormat(
    @Json(name = "container") val container: String = "raw",
    @Json(name = "encoding") val encoding: String = "pcm_s16le",
    @Json(name = "sample_rate") val sampleRate: Int = 24000
)

@JsonClass(generateAdapter = true)
data class CartesiaTtsRequest(
    @Json(name = "model_id") val modelId: String,
    @Json(name = "transcript") val transcript: String,
    @Json(name = "voice") val voice: CartesiaVoiceSpec,
    @Json(name = "output_format") val outputFormat: CartesiaOutputFormat = CartesiaOutputFormat(),
    @Json(name = "language") val language: String? = null
)

@JsonClass(generateAdapter = true)
data class CartesiaRemoteVoiceDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "language") val language: String? = null,
    @Json(name = "is_public") val isPublic: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class CartesiaVoicesPageDto(
    @Json(name = "data") val data: List<CartesiaRemoteVoiceDto>? = null
)

interface CartesiaApiService {
    @POST("tts/bytes")
    @Streaming
    suspend fun synthesizeSpeech(
        @Body request: CartesiaTtsRequest
    ): Response<ResponseBody>

    @GET("voices")
    suspend fun listVoices(): Response<ResponseBody>
}

object CartesiaNetworkModule {
    private const val BASE_URL = "https://api.cartesia.ai/"
    private const val CARTESIA_VERSION = "2024-11-13"
    private const val PREFS_NAME = "sonic_voice_prefs"
    private const val KEY_CUSTOM_API_KEY = "custom_cartesia_api_key"

    @Volatile
    private var runtimeOverrideKey: String? = null

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_CUSTOM_API_KEY, null)?.trim()
        if (!saved.isNullOrEmpty()) {
            runtimeOverrideKey = saved
        }
    }

    fun saveCustomApiKey(context: Context, newKey: String) {
        val clean = newKey.trim()
        runtimeOverrideKey = clean.ifEmpty { null }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_API_KEY, clean)
            .apply()
    }

    fun resolveActiveApiKey(): String {
        val override = runtimeOverrideKey?.trim().orEmpty()
        if (override.isNotEmpty()) return override
        val buildKey = BuildConfig.CARTESIA_API_KEY.trim()
        if (buildKey.isNotEmpty() &&
            buildKey != "YOUR_CARTESIA_API_KEY" &&
            buildKey != "MY_CARTESIA_API_KEY" &&
            !buildKey.startsWith("YOUR_")
        ) {
            return buildKey
        }
        return ""
    }

    fun isApiKeyConfigured(): Boolean {
        return resolveActiveApiKey().isNotEmpty()
    }

    fun getMaskedKeyStatus(): String {
        val key = resolveActiveApiKey()
        return if (key.length > 10) {
            val prefix = key.take(7)
            val suffix = key.takeLast(4)
            "$prefix••••$suffix"
        } else if (key.isNotEmpty()) {
            "Active"
        } else {
            "Not Set"
        }
    }

    val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val authInterceptor = Interceptor { chain ->
        val apiKey = resolveActiveApiKey()
        val requestBuilder = chain.request().newBuilder()
            .header("Cartesia-Version", CARTESIA_VERSION)
            .header("X-API-Key", apiKey)
            .header("Authorization", "Bearer $apiKey")
        chain.proceed(requestBuilder.build())
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    val apiService: CartesiaApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CartesiaApiService::class.java)
    }
}
