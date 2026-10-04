package com.example.data.repository

import android.content.Context
import com.example.audio.AudioPlayerController
import com.example.data.local.TtsDao
import com.example.data.local.TtsHistoryEntity
import com.example.data.model.TtsDefaults
import com.example.data.model.TtsEngineMode
import com.example.data.model.TtsVoice
import com.example.data.remote.CartesiaApiService
import com.example.data.remote.CartesiaNetworkModule
import com.example.data.remote.CartesiaOutputFormat
import com.example.data.remote.CartesiaRemoteVoiceDto
import com.example.data.remote.CartesiaTtsRequest
import com.example.data.remote.CartesiaVoiceSpec
import com.example.data.remote.CartesiaVoicesPageDto
import com.squareup.moshi.Types
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class SynthesisResult(
    val historyItem: TtsHistoryEntity,
    val usedCartesiaCloud: Boolean,
    val statusNotice: String
)

class TtsRepository(
    private val context: Context,
    private val ttsDao: TtsDao,
    private val apiService: CartesiaApiService = CartesiaNetworkModule.apiService
) {
    val allHistory: Flow<List<TtsHistoryEntity>> = ttsDao.getAllHistory()

    private val audioDir: File by lazy {
        File(context.filesDir, "tts_audio").apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun fetchCartesiaVoices(): Result<List<TtsVoice>> = withContext(Dispatchers.IO) {
        if (!CartesiaNetworkModule.isApiKeyConfigured()) {
            return@withContext Result.success(TtsDefaults.defaultVoices)
        }

        try {
            val response = apiService.listVoices()
            if (!response.isSuccessful) {
                val errBody = response.errorBody()?.string().orEmpty()
                return@withContext Result.failure(
                    IllegalStateException(parseApiErrorMessage(response.code(), errBody))
                )
            }

            val rawJson = response.body()?.string()?.trim().orEmpty()
            if (rawJson.isEmpty()) {
                return@withContext Result.success(TtsDefaults.defaultVoices)
            }

            val remoteDtos: List<CartesiaRemoteVoiceDto> = if (rawJson.startsWith("[")) {
                val listType = Types.newParameterizedType(List::class.java, CartesiaRemoteVoiceDto::class.java)
                val adapter = CartesiaNetworkModule.moshi.adapter<List<CartesiaRemoteVoiceDto>>(listType)
                adapter.fromJson(rawJson).orEmpty()
            } else {
                val pageAdapter = CartesiaNetworkModule.moshi.adapter(CartesiaVoicesPageDto::class.java)
                pageAdapter.fromJson(rawJson)?.data.orEmpty()
            }

            val mappedVoices = remoteDtos.mapNotNull { dto ->
                val id = dto.id?.trim().orEmpty()
                val name = dto.name?.trim().orEmpty()
                if (id.isEmpty() || name.isEmpty()) {
                    null
                } else {
                    val lang = dto.language?.ifBlank { "multilingual" } ?: "multilingual"
                    TtsVoice(
                        id = id,
                        name = name,
                        banglaTitle = "$name (${lang.uppercase()})",
                        description = dto.description?.takeIf { it.isNotBlank() }
                            ?: "Cartesia Cloud AI studio voice ($lang)",
                        languageCode = lang,
                        styleBadge = if (dto.isPublic == false) "Custom Voice" else "Cartesia Cloud",
                        isCustomFromApi = true
                    )
                }
            }

            val merged = (TtsDefaults.defaultVoices + mappedVoices).distinctBy { it.id }
            Result.success(merged)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun synthesizeSpeech(
        transcript: String,
        voice: TtsVoice,
        modelId: String,
        languageCode: String,
        speed: Float,
        engineMode: TtsEngineMode,
        audioPlayerController: AudioPlayerController
    ): Result<SynthesisResult> = withContext(Dispatchers.IO) {
        val cleanText = transcript.trim()
        if (cleanText.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("অনুগ্রহ করে টেক্সট লিখুন (Please enter some text).")
            )
        }

        val timestamp = System.currentTimeMillis()
        val outputFile = File(audioDir, "sonic_tts_$timestamp.wav")

        val shouldTryCartesia = when (engineMode) {
            TtsEngineMode.CARTESIA_CLOUD -> true
            TtsEngineMode.AUTO_SMART -> CartesiaNetworkModule.isApiKeyConfigured()
            TtsEngineMode.DEVICE_NATIVE -> false
        }

        if (shouldTryCartesia) {
            if (!CartesiaNetworkModule.isApiKeyConfigured() && engineMode == TtsEngineMode.CARTESIA_CLOUD) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "Cartesia API Key সেট করা নেই। উপরের Key বাটনে ট্যাপ করে অথবা AI Studio Secrets প্যানেলে আপনার sk_car_… কী-টি যুক্ত করুন।"
                    )
                )
            }

            val cartesiaAttempt = tryCartesiaSynthesisVerified(
                transcript = cleanText,
                voiceId = voice.id,
                preferredModelId = modelId,
                languageCode = languageCode,
                outputFile = outputFile
            )

            if (cartesiaAttempt.isSuccess) {
                val actualModelUsed = cartesiaAttempt.getOrThrow()
                val durationMs = AudioPlayerController.resolveAudioDurationMs(context, outputFile)
                val entity = TtsHistoryEntity(
                    transcript = cleanText,
                    voiceId = voice.id,
                    voiceName = voice.name,
                    modelId = actualModelUsed,
                    languageCode = languageCode,
                    engineUsed = "Cartesia $actualModelUsed",
                    playbackSpeed = speed,
                    audioFilePath = outputFile.absolutePath,
                    durationMs = durationMs,
                    fileSizeBytes = outputFile.length()
                )
                val insertedId = ttsDao.insertHistory(entity)
                return@withContext Result.success(
                    SynthesisResult(
                        historyItem = entity.copy(id = insertedId),
                        usedCartesiaCloud = true,
                        statusNotice = "Cartesia AI ($actualModelUsed) দিয়ে ভয়েস তৈরি ও প্লে হচ্ছে!"
                    )
                )
            } else {
                // Always surface the exact Cartesia error if the user has an API key configured so they know why if anything failed
                return@withContext Result.failure(
                    cartesiaAttempt.exceptionOrNull()
                        ?: IllegalStateException("Cartesia API সংযোগে সমস্যা হয়েছে।")
                )
            }
        }

        val deviceSuccess = audioPlayerController.synthesizeWithDeviceTtsToFile(
            text = cleanText,
            languageCode = languageCode,
            speed = speed,
            outputFile = outputFile
        )

        if (!deviceSuccess || !outputFile.exists()) {
            return@withContext Result.failure(
                IllegalStateException("অডিও ফাইল তৈরি করা সম্ভব হয়নি। অনুগ্রহ করে আবার চেষ্টা করুন।")
            )
        }

        val durationMs = AudioPlayerController.resolveAudioDurationMs(context, outputFile)
        val entity = TtsHistoryEntity(
            transcript = cleanText,
            voiceId = voice.id,
            voiceName = "${voice.name} (Device)",
            modelId = "android-tts",
            languageCode = languageCode,
            engineUsed = "Android Device TTS",
            playbackSpeed = speed,
            audioFilePath = outputFile.absolutePath,
            durationMs = durationMs,
            fileSizeBytes = outputFile.length()
        )
        val insertedId = ttsDao.insertHistory(entity)

        Result.success(
            SynthesisResult(
                historyItem = entity.copy(id = insertedId),
                usedCartesiaCloud = false,
                statusNotice = "অন-ডিভাইস ভয়েস ইঞ্জিন দিয়ে অডিও তৈরি সম্পন্ন হয়েছে!"
            )
        )
    }

    private suspend fun tryCartesiaSynthesisVerified(
        transcript: String,
        voiceId: String,
        preferredModelId: String,
        languageCode: String,
        outputFile: File
    ): Result<String> {
        val hasBangla = AudioPlayerController.containsBanglaCharacters(transcript)
        val resolvedLang = when {
            languageCode == "bn" || (languageCode == "auto" && hasBangla) -> "bn"
            languageCode == "auto" -> null
            else -> languageCode
        }

        // If Bangla is used, sonic-3.6 and sonic-3 natively support "language": "bn"
        val candidateModels = if (resolvedLang == "bn") {
            listOf(
                if (preferredModelId.startsWith("sonic-3")) preferredModelId else "sonic-3.6",
                "sonic-3.6",
                "sonic-3",
                "sonic-2"
            ).distinct()
        } else {
            listOf(preferredModelId, "sonic-3.6", "sonic-2").distinct()
        }

        var lastError: Throwable? = null

        for (model in candidateModels) {
            // sonic-2 does not accept language="bn", only sonic-3 / sonic-3.6 do
            val langForModel = if (model == "sonic-2" && resolvedLang == "bn") null else resolvedLang
            try {
                val request = CartesiaTtsRequest(
                    modelId = model,
                    transcript = transcript,
                    voice = CartesiaVoiceSpec(mode = "id", id = voiceId),
                    outputFormat = CartesiaOutputFormat(
                        container = "raw",
                        encoding = "pcm_s16le",
                        sampleRate = 24000
                    ),
                    language = langForModel
                )
                val response = apiService.synthesizeSpeech(request)
                if (response.isSuccessful) {
                    val rawBytes = response.body()?.bytes()
                    if (rawBytes != null && rawBytes.size > 64) {
                        AudioPlayerController.writeRawPcmOrWavToStandardWavFile(
                            inputBytes = rawBytes,
                            defaultSampleRate = 24000,
                            outputFile = outputFile
                        )
                        if (outputFile.exists() && outputFile.length() > 100L) {
                            return Result.success(model)
                        }
                    }
                } else {
                    val code = response.code()
                    val errStr = response.errorBody()?.string().orEmpty()
                    lastError = IllegalStateException(parseApiErrorMessage(code, errStr))
                    if (code == 401 || code == 403 || code == 402 || code == 429) {
                        return Result.failure(lastError)
                    }
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        return Result.failure(
            lastError ?: IllegalStateException("Cartesia API থেকে অডিও ডাউনলোড করা যায়নি।")
        )
    }

    suspend fun toggleFavorite(item: TtsHistoryEntity) {
        ttsDao.updateFavorite(item.id, !item.isFavorite)
    }

    suspend fun deleteHistoryItem(item: TtsHistoryEntity) = withContext(Dispatchers.IO) {
        try {
            val file = File(item.audioFilePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {
        }
        ttsDao.deleteById(item.id)
    }

    suspend fun clearAllHistory(items: List<TtsHistoryEntity>) = withContext(Dispatchers.IO) {
        items.forEach { item ->
            try {
                File(item.audioFilePath).delete()
            } catch (_: Exception) {
            }
        }
        ttsDao.clearAll()
    }

    private fun parseApiErrorMessage(code: Int, rawBody: String): String {
        val extracted = try {
            val json = JSONObject(rawBody)
            json.optString("message").ifBlank {
                json.optString("error").ifBlank { rawBody.take(160) }
            }
        } catch (_: Exception) {
            rawBody.take(160)
        }
        return when (code) {
            401, 403 -> "Cartesia API Key অনুমোদিত নয় (HTTP $code)। $extracted"
            402, 429 -> "Cartesia API কোটা বা ক্রেডিট শেষ হয়েছে (HTTP $code)। $extracted"
            else -> "Cartesia API ত্রুটি (HTTP $code): ${extracted.ifBlank { "অজানা ত্রুটি" }}"
        }
    }
}
