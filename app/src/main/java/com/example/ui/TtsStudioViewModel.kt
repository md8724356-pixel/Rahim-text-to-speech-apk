package com.example.ui

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioPlayerController
import com.example.audio.PlaybackUiState
import com.example.data.local.AppDatabase
import com.example.data.local.TtsHistoryEntity
import com.example.data.model.PresetScript
import com.example.data.model.TtsDefaults
import com.example.data.model.TtsEngineMode
import com.example.data.model.TtsLanguageOption
import com.example.data.model.TtsModelOption
import com.example.data.model.TtsVoice
import com.example.data.remote.CartesiaNetworkModule
import com.example.data.repository.TtsRepository
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppTab {
    STUDIO,
    VOICES,
    LIBRARY
}

@Immutable
data class TtsStudioUiState(
    val currentTab: AppTab = AppTab.STUDIO,
    val availableVoices: List<TtsVoice> = TtsDefaults.defaultVoices,
    val selectedVoice: TtsVoice = TtsDefaults.defaultVoices.first(),
    val selectedModel: TtsModelOption = TtsDefaults.models.first(),
    val selectedLanguage: TtsLanguageOption = TtsDefaults.languages.first(), // Bangla default
    val speechSpeed: Float = 1.0f,
    val engineMode: TtsEngineMode = TtsEngineMode.AUTO_SMART,
    val isSynthesizing: Boolean = false,
    val isLoadingVoices: Boolean = false,
    val statusBannerMessage: String? = null,
    val errorBannerMessage: String? = null,
    val isApiKeyConfigured: Boolean = CartesiaNetworkModule.isApiKeyConfigured(),
    val maskedKeyLabel: String = CartesiaNetworkModule.getMaskedKeyStatus(),
    val showApiKeyDialog: Boolean = false,
    val searchQuery: String = "",
    val showFavoritesOnly: Boolean = false
)

class TtsStudioViewModel(
    application: Application,
    private val repository: TtsRepository
) : AndroidViewModel(application) {

    private val audioPlayerController = AudioPlayerController(
        context = application.applicationContext,
        scope = viewModelScope
    )

    private val _uiState = MutableStateFlow(TtsStudioUiState())
    val uiState: StateFlow<TtsStudioUiState> = _uiState.asStateFlow()

    // Isolated transcript state so typing never triggers recomposition of the rest of the Studio screen
    private val _transcriptState = MutableStateFlow(TtsDefaults.presetScripts.first().transcript)
    val transcriptState: StateFlow<String> = _transcriptState.asStateFlow()

    val playbackState: StateFlow<PlaybackUiState> = audioPlayerController.playbackState

    val historyItems: StateFlow<List<TtsHistoryEntity>> = repository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        CartesiaNetworkModule.init(application)
        _uiState.update {
            it.copy(
                isApiKeyConfigured = CartesiaNetworkModule.isApiKeyConfigured(),
                maskedKeyLabel = CartesiaNetworkModule.getMaskedKeyStatus()
            )
        }
        if (CartesiaNetworkModule.isApiKeyConfigured()) {
            refreshRemoteVoices(silent = true)
        }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun updateTranscript(newText: String) {
        _transcriptState.value = newText
        if (_uiState.value.errorBannerMessage != null) {
            _uiState.update { it.copy(errorBannerMessage = null) }
        }
    }

    fun clearTranscript() {
        _transcriptState.value = ""
        _uiState.update {
            it.copy(
                errorBannerMessage = null,
                statusBannerMessage = null
            )
        }
    }

    fun applyPresetScript(preset: PresetScript) {
        val matchedLang = TtsDefaults.languages.find { it.code == preset.languageCode }
            ?: TtsDefaults.languages.first()
        _transcriptState.value = preset.transcript
        _uiState.update {
            it.copy(
                selectedLanguage = matchedLang,
                errorBannerMessage = null,
                statusBannerMessage = "'${preset.titleBangla}' টেমপ্লেট লোড করা হয়েছে"
            )
        }
    }

    fun selectVoice(voice: TtsVoice) {
        _uiState.update {
            it.copy(
                selectedVoice = voice,
                statusBannerMessage = "নির্বাচিত ভয়েস: ${voice.name}"
            )
        }
    }

    fun selectModel(model: TtsModelOption) {
        _uiState.update {
            it.copy(
                selectedModel = model,
                statusBannerMessage = "মডেল: ${model.title}"
            )
        }
    }

    fun selectLanguage(language: TtsLanguageOption) {
        _uiState.update {
            it.copy(selectedLanguage = language)
        }
    }

    fun updateSpeechSpeed(speed: Float) {
        val rounded = (Math.round(speed * 10f) / 10f).coerceIn(0.6f, 1.5f)
        if (kotlin.math.abs(_uiState.value.speechSpeed - rounded) >= 0.05f) {
            _uiState.update { it.copy(speechSpeed = rounded) }
        }
        audioPlayerController.setPlaybackSpeed(rounded)
    }

    fun selectEngineMode(mode: TtsEngineMode) {
        _uiState.update {
            it.copy(
                engineMode = mode,
                errorBannerMessage = null,
                statusBannerMessage = "ইঞ্জিন মোড: ${mode.titleBangla}"
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleShowFavoritesOnly() {
        _uiState.update { it.copy(showFavoritesOnly = !it.showFavoritesOnly) }
    }

    fun openApiKeyDialog() {
        _uiState.update { it.copy(showApiKeyDialog = true) }
    }

    fun dismissApiKeyDialog() {
        _uiState.update { it.copy(showApiKeyDialog = false) }
    }

    fun savePermanentApiKey(rawKey: String) {
        val clean = rawKey.trim()
        CartesiaNetworkModule.saveCustomApiKey(getApplication(), clean)
        val configured = CartesiaNetworkModule.isApiKeyConfigured()
        _uiState.update {
            it.copy(
                isApiKeyConfigured = configured,
                maskedKeyLabel = CartesiaNetworkModule.getMaskedKeyStatus(),
                showApiKeyDialog = false,
                errorBannerMessage = null,
                statusBannerMessage = if (configured) {
                    "Cartesia API Key ডিভাইসে পার্মানেন্টভাবে সেভ করা হয়েছে!"
                } else {
                    "কাস্টম API Key মুছে ফেলা হয়েছে।"
                }
            )
        }
        if (configured) {
            refreshRemoteVoices(silent = true)
        }
    }

    fun refreshRemoteVoices(silent: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingVoices = true, errorBannerMessage = null) }
            val result = repository.fetchCartesiaVoices()
            result.onSuccess { voices ->
                val currentSelectedId = _uiState.value.selectedVoice.id
                val updatedSelected = voices.find { it.id == currentSelectedId } ?: voices.first()
                _uiState.update {
                    it.copy(
                        availableVoices = voices,
                        selectedVoice = updatedSelected,
                        isLoadingVoices = false,
                        statusBannerMessage = if (silent) {
                            it.statusBannerMessage
                        } else {
                            "Cartesia থেকে ${voices.size}টি ভয়েস লোড হয়েছে!"
                        }
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingVoices = false,
                        errorBannerMessage = if (silent) null else (err.message ?: "ভয়েস তালিকা লোড করা যায়নি।")
                    )
                }
            }
        }
    }

    fun synthesizeAndPlay() {
        val currentState = _uiState.value
        if (currentState.isSynthesizing) return
        val text = _transcriptState.value.trim()
        if (text.isEmpty()) {
            _uiState.update {
                it.copy(errorBannerMessage = "অনুগ্রহ করে ভয়েসে রূপান্তর করার জন্য কিছু টেক্সট লিখুন।")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSynthesizing = true,
                    errorBannerMessage = null,
                    statusBannerMessage = "অডিও সিন্থেসিস চলছে…"
                )
            }

            val result = repository.synthesizeSpeech(
                transcript = text,
                voice = currentState.selectedVoice,
                modelId = currentState.selectedModel.id,
                languageCode = currentState.selectedLanguage.code,
                speed = currentState.speechSpeed,
                engineMode = currentState.engineMode,
                audioPlayerController = audioPlayerController
            )

            result.onSuccess { synthesisResult ->
                val item = synthesisResult.historyItem
                _uiState.update {
                    it.copy(
                        isSynthesizing = false,
                        statusBannerMessage = synthesisResult.statusNotice,
                        errorBannerMessage = null
                    )
                }
                audioPlayerController.loadAndPlay(
                    filePath = item.audioFilePath,
                    title = item.transcript.take(48),
                    subtitle = "${item.voiceName} • ${item.engineUsed}",
                    speed = item.playbackSpeed,
                    autoPlay = true
                )
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSynthesizing = false,
                        statusBannerMessage = null,
                        errorBannerMessage = error.message ?: "ভয়েস তৈরি করতে সমস্যা হয়েছে।"
                    )
                }
            }
        }
    }

    fun previewVoice(voice: TtsVoice) {
        selectVoice(voice)
        val currentState = _uiState.value
        val currentTranscript = _transcriptState.value
        val sampleText = if (currentState.selectedLanguage.code == "bn" ||
            AudioPlayerController.containsBanglaCharacters(currentTranscript)
        ) {
            "নমস্কার, আমি ${voice.name}। সনিক ভয়েস স্টুডিওতে আপনাকে স্বাগতম।"
        } else {
            "Hello! I am ${voice.name} from Cartesia Sonic Studio. Ready to bring your words to life."
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSynthesizing = true,
                    errorBannerMessage = null,
                    statusBannerMessage = "${voice.name} ভয়েস প্রিভিউ তৈরি হচ্ছে…"
                )
            }

            val result = repository.synthesizeSpeech(
                transcript = sampleText,
                voice = voice,
                modelId = currentState.selectedModel.id,
                languageCode = currentState.selectedLanguage.code,
                speed = currentState.speechSpeed,
                engineMode = currentState.engineMode,
                audioPlayerController = audioPlayerController
            )

            result.onSuccess { synthesisResult ->
                val item = synthesisResult.historyItem
                _uiState.update {
                    it.copy(
                        isSynthesizing = false,
                        statusBannerMessage = "${voice.name} প্রিভিউ প্লে হচ্ছে"
                    )
                }
                audioPlayerController.loadAndPlay(
                    filePath = item.audioFilePath,
                    title = item.transcript.take(48),
                    subtitle = "${item.voiceName} • ${item.engineUsed}",
                    speed = item.playbackSpeed,
                    autoPlay = true
                )
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSynthesizing = false,
                        errorBannerMessage = err.message ?: "প্রিভিউ তৈরি করা যায়নি।"
                    )
                }
            }
        }
    }

    fun playHistoryItem(item: TtsHistoryEntity) {
        val currentActive = playbackState.value.activeFilePath
        if (currentActive == item.audioFilePath) {
            audioPlayerController.togglePlayPause()
        } else {
            audioPlayerController.loadAndPlay(
                filePath = item.audioFilePath,
                title = item.transcript.take(48),
                subtitle = "${item.voiceName} • ${item.engineUsed}",
                speed = item.playbackSpeed,
                autoPlay = true
            )
        }
    }

    fun togglePlayPause() {
        audioPlayerController.togglePlayPause()
    }

    fun seekToFraction(fraction: Float) {
        audioPlayerController.seekToFraction(fraction)
    }

    fun toggleFavorite(item: TtsHistoryEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(item)
        }
    }

    fun deleteHistoryItem(item: TtsHistoryEntity) {
        viewModelScope.launch {
            if (playbackState.value.activeFilePath == item.audioFilePath) {
                audioPlayerController.stopInternal()
            }
            repository.deleteHistoryItem(item)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            audioPlayerController.stopInternal()
            repository.clearAllHistory(historyItems.value)
            _uiState.update {
                it.copy(statusBannerMessage = "সব অডিও ইতিহাস মুছে ফেলা হয়েছে।")
            }
        }
    }

    fun loadHistoryTranscriptToStudio(item: TtsHistoryEntity) {
        val voice = _uiState.value.availableVoices.find { it.id == item.voiceId }
            ?: _uiState.value.selectedVoice
        _transcriptState.value = item.transcript
        _uiState.update {
            it.copy(
                currentTab = AppTab.STUDIO,
                selectedVoice = voice,
                statusBannerMessage = "টেক্সট স্টুডিও এডিটরে লোড করা হয়েছে"
            )
        }
    }

    fun shareAudioFile(filePath: String, transcriptPreview: String) {
        val appContext = getApplication<Application>().applicationContext
        val file = File(filePath)
        if (!file.exists()) {
            _uiState.update { it.copy(errorBannerMessage = "অডিও ফাইলটি পাওয়া যায়নি।") }
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "SonicVoice TTS Audio: $transcriptPreview")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Audio (.wav)").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(chooser)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(errorBannerMessage = "শেয়ার চালু করা যায়নি: ${e.localizedMessage}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayerController.release()
    }

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val database = AppDatabase.getInstance(application)
                    val repository = TtsRepository(
                        context = application.applicationContext,
                        ttsDao = database.ttsDao()
                    )
                    return TtsStudioViewModel(application, repository) as T
                }
            }
        }
    }
}
