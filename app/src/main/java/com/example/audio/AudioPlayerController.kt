package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Immutable
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Immutable
data class PlaybackUiState(
    val activeFilePath: String? = null,
    val activeTitle: String = "",
    val activeSubtitle: String = "",
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val waveformAmplitudes: List<Float> = DEFAULT_WAVEFORM
) {
    companion object {
        val DEFAULT_WAVEFORM: List<Float> = List(36) { index ->
            ((sin(index * 0.45) * 0.35 + 0.45).toFloat()).coerceIn(0.15f, 0.95f)
        }
    }
}

class AudioPlayerController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var mediaPlayer: MediaPlayer? = null
    private var audioTrack: AudioTrack? = null
    private var audioTrackJob: Job? = null
    private var progressJob: Job? = null
    private var loadJob: Job? = null
    private var deviceTts: TextToSpeech? = null
    @Volatile
    private var isDeviceTtsReady = false

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            initDeviceTts()
        }
    }

    private fun initDeviceTts() {
        try {
            deviceTts = TextToSpeech(context.applicationContext) { status ->
                isDeviceTtsReady = (status == TextToSpeech.SUCCESS)
            }
        } catch (_: Exception) {
            isDeviceTtsReady = false
        }
    }

    fun loadAndPlay(
        filePath: String,
        title: String,
        subtitle: String,
        speed: Float = _playbackState.value.playbackSpeed,
        autoPlay: Boolean = true
    ) {
        loadJob?.cancel()
        loadJob = scope.launch(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists() || file.length() <= 44L) return@launch

            stopInternal()

            val amplitudes = extractWaveformFast(file, barsCount = 36)
            val estimatedDur = estimateWavDurationMs(file)

            var mediaPlayerStarted = false
            try {
                val player = MediaPlayer()
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                player.setDataSource(file.absolutePath)
                player.prepare()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && abs(speed - 1.0f) > 0.05f) {
                    try {
                        player.playbackParams = PlaybackParams().setSpeed(speed.coerceIn(0.5f, 2.0f))
                    } catch (_: Exception) {
                    }
                }
                player.setOnCompletionListener {
                    progressJob?.cancel()
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        currentPositionMs = _playbackState.value.durationMs
                    )
                }

                mediaPlayer = player
                val totalDuration = player.duration.toLong().takeIf { it > 100L } ?: estimatedDur

                if (autoPlay) {
                    player.start()
                }

                _playbackState.value = PlaybackUiState(
                    activeFilePath = filePath,
                    activeTitle = title,
                    activeSubtitle = subtitle,
                    isPlaying = player.isPlaying,
                    currentPositionMs = 0L,
                    durationMs = totalDuration,
                    playbackSpeed = speed,
                    waveformAmplitudes = amplitudes
                )

                if (player.isPlaying) {
                    startProgressTicker()
                }
                mediaPlayerStarted = true
            } catch (_: Exception) {
                mediaPlayerStarted = false
            }

            if (!mediaPlayerStarted) {
                playWavViaAudioTrack(
                    file = file,
                    title = title,
                    subtitle = subtitle,
                    speed = speed,
                    amplitudes = amplitudes,
                    startFraction = 0f
                )
            }
        }
    }

    private fun playWavViaAudioTrack(
        file: File,
        title: String,
        subtitle: String,
        speed: Float,
        amplitudes: List<Float>,
        startFraction: Float
    ) {
        audioTrackJob?.cancel()
        audioTrackJob = scope.launch(Dispatchers.IO) {
            try {
                val allBytes = file.readBytes()
                if (allBytes.size <= 44) return@launch
                val sampleRate = readWavSampleRate(allBytes).coerceIn(8000, 48000)
                val pcmOffset = 44
                val totalPcmBytes = (allBytes.size - pcmOffset) and -2
                val totalDurationMs = ((totalPcmBytes * 1000L) / (sampleRate * 2)).coerceAtLeast(500L)

                val minBufSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4096)

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && abs(speed - 1.0f) > 0.05f) {
                    try {
                        track.playbackParams = PlaybackParams().setSpeed(speed.coerceIn(0.6f, 1.5f))
                    } catch (_: Exception) {
                    }
                }

                audioTrack = track
                track.play()

                var currentByteOffset = ((totalPcmBytes * startFraction).toInt() and -2).coerceIn(0, totalPcmBytes)
                _playbackState.value = PlaybackUiState(
                    activeFilePath = file.absolutePath,
                    activeTitle = title,
                    activeSubtitle = subtitle,
                    isPlaying = true,
                    currentPositionMs = ((currentByteOffset.toLong() * 1000L) / (sampleRate * 2)),
                    durationMs = totalDurationMs,
                    playbackSpeed = speed,
                    waveformAmplitudes = amplitudes
                )

                val chunkSize = 4096
                while (isActive && currentByteOffset < totalPcmBytes) {
                    val toWrite = minOf(chunkSize, totalPcmBytes - currentByteOffset)
                    val written = track.write(allBytes, pcmOffset + currentByteOffset, toWrite)
                    if (written <= 0) break
                    currentByteOffset += written
                    val posMs = ((currentByteOffset.toLong() * 1000L) / (sampleRate * 2)).coerceAtMost(totalDurationMs)
                    if (abs(posMs - _playbackState.value.currentPositionMs) >= 140L) {
                        _playbackState.value = _playbackState.value.copy(
                            isPlaying = true,
                            currentPositionMs = posMs
                        )
                    }
                }

                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    currentPositionMs = totalDurationMs
                )
            } catch (_: Exception) {
                _playbackState.value = _playbackState.value.copy(isPlaying = false)
            } finally {
                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (_: Exception) {
                }
                audioTrack = null
            }
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer
        if (player != null) {
            try {
                if (player.isPlaying) {
                    player.pause()
                    progressJob?.cancel()
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        currentPositionMs = player.currentPosition.toLong()
                    )
                } else {
                    if (player.currentPosition >= player.duration - 150) {
                        player.seekTo(0)
                    }
                    player.start()
                    _playbackState.value = _playbackState.value.copy(isPlaying = true)
                    startProgressTicker()
                }
                return
            } catch (_: Exception) {
            }
        }

        val current = _playbackState.value
        val activePath = current.activeFilePath ?: return
        if (current.isPlaying) {
            stopInternal()
            _playbackState.value = current.copy(isPlaying = false)
        } else {
            loadAndPlay(
                filePath = activePath,
                title = current.activeTitle,
                subtitle = current.activeSubtitle,
                speed = current.playbackSpeed,
                autoPlay = true
            )
        }
    }

    fun seekToFraction(fraction: Float) {
        val player = mediaPlayer
        if (player != null) {
            try {
                val targetMs = (player.duration * fraction.coerceIn(0f, 1f)).toInt()
                player.seekTo(targetMs)
                _playbackState.value = _playbackState.value.copy(
                    currentPositionMs = targetMs.toLong()
                )
                return
            } catch (_: Exception) {
            }
        }

        val current = _playbackState.value
        val activePath = current.activeFilePath ?: return
        val file = File(activePath)
        if (file.exists()) {
            stopInternal()
            playWavViaAudioTrack(
                file = file,
                title = current.activeTitle,
                subtitle = current.activeSubtitle,
                speed = current.playbackSpeed,
                amplitudes = current.waveformAmplitudes,
                startFraction = fraction.coerceIn(0f, 1f)
            )
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.6f, 1.5f)
        if (abs(_playbackState.value.playbackSpeed - clamped) < 0.02f) return
        _playbackState.value = _playbackState.value.copy(playbackSpeed = clamped)
        val player = mediaPlayer ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val wasPlaying = player.isPlaying
                player.playbackParams = PlaybackParams().setSpeed(clamped)
                if (!wasPlaying && player.isPlaying) {
                    player.pause()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val player = mediaPlayer
                if (player != null && player.isPlaying) {
                    val pos = player.currentPosition.toLong()
                    val current = _playbackState.value
                    if (abs(pos - current.currentPositionMs) >= 120L || !current.isPlaying) {
                        _playbackState.value = current.copy(
                            isPlaying = true,
                            currentPositionMs = pos
                        )
                    }
                } else {
                    break
                }
                delay(180L)
            }
        }
    }

    suspend fun synthesizeWithDeviceTtsToFile(
        text: String,
        languageCode: String,
        speed: Float,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val tts = deviceTts
        if (tts != null && isDeviceTtsReady) {
            val locale = when (languageCode) {
                "bn" -> Locale.forLanguageTag("bn-BD")
                "hi" -> Locale.forLanguageTag("hi-IN")
                "ar" -> Locale.forLanguageTag("ar-SA")
                "es" -> Locale.forLanguageTag("es-ES")
                "fr" -> Locale.forLanguageTag("fr-FR")
                "ja" -> Locale.JAPAN
                "en" -> Locale.US
                else -> if (containsBanglaCharacters(text)) {
                    Locale.forLanguageTag("bn-BD")
                } else {
                    Locale.US
                }
            }

            try {
                tts.language = locale
            } catch (_: Exception) {
                tts.language = Locale.US
            }
            tts.setSpeechRate(speed.coerceIn(0.6f, 1.5f))

            val utteranceId = "utt_${UUID.randomUUID()}"
            val tempRawFile = File(outputFile.parentFile, "raw_${outputFile.name}")
            val synthesized = withTimeoutOrNull(2_500L) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onDone(id: String?) {
                            if (id == utteranceId && cont.isActive) {
                                cont.resume(true)
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(id: String?) {
                            if (id == utteranceId && cont.isActive) {
                                cont.resume(false)
                            }
                        }

                        override fun onError(id: String?, errorCode: Int) {
                            if (id == utteranceId && cont.isActive) {
                                cont.resume(false)
                            }
                        }
                    })

                    val params = Bundle()
                    val result = tts.synthesizeToFile(text, params, tempRawFile, utteranceId)
                    if (result != TextToSpeech.SUCCESS && cont.isActive) {
                        cont.resume(false)
                    }
                }
            } ?: false

            if (synthesized && tempRawFile.exists() && tempRawFile.length() > 128L) {
                val rawBytes = tempRawFile.readBytes()
                tempRawFile.delete()
                writeRawPcmOrWavToStandardWavFile(rawBytes, defaultSampleRate = 22050, outputFile = outputFile)
                // Also trigger live speaker output via TTS engine directly
                try {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "live_$utteranceId")
                } catch (_: Exception) {
                }
                return@withContext outputFile.exists() && outputFile.length() > 44L
            }
            tempRawFile.delete()
        }

        generateSyllabicCadenceWav(text, speed, outputFile)
        outputFile.exists() && outputFile.length() > 44L
    }

    fun stopInternal() {
        progressJob?.cancel()
        audioTrackJob?.cancel()
        val track = audioTrack
        audioTrack = null
        if (track != null) {
            try {
                track.stop()
            } catch (_: Exception) {
            }
            try {
                track.release()
            } catch (_: Exception) {
            }
        }

        val player = mediaPlayer
        mediaPlayer = null
        if (player != null) {
            try {
                if (player.isPlaying) player.stop()
            } catch (_: Exception) {
            }
            try {
                player.release()
            } catch (_: Exception) {
            }
        }
    }

    fun release() {
        loadJob?.cancel()
        stopInternal()
        try {
            deviceTts?.stop()
            deviceTts?.shutdown()
        } catch (_: Exception) {
        }
    }

    companion object {
        fun containsBanglaCharacters(text: String): Boolean {
            for (i in 0 until text.length) {
                val code = text[i].code
                if (code in 0x0980..0x09FF) return true
            }
            return false
        }

        fun countWordsFast(text: String): Int {
            var count = 0
            var inWord = false
            for (i in 0 until text.length) {
                if (text[i].isWhitespace()) {
                    inWord = false
                } else if (!inWord) {
                    inWord = true
                    count++
                }
            }
            return count
        }

        private fun readWavSampleRate(wavBytes: ByteArray): Int {
            if (wavBytes.size < 28) return 24000
            return ByteBuffer.wrap(wavBytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
        }

        /**
         * Converts either raw 16-bit PCM bytes OR a streaming WAV (with 0xFFFFFFFF chunk sizes)
         * into a 100% canonical 44-byte header RIFF WAVE file with exact chunk sizes so Android
         * MediaPlayer, AudioTrack, and MediaMetadataRetriever play it without errors.
         */
        fun writeRawPcmOrWavToStandardWavFile(
            inputBytes: ByteArray,
            defaultSampleRate: Int = 24000,
            outputFile: File
        ) {
            var sampleRate = defaultSampleRate
            var pcmStartOffset = 0

            // Check if inputBytes starts with "RIFF"
            if (inputBytes.size > 44 &&
                inputBytes[0] == 'R'.code.toByte() &&
                inputBytes[1] == 'I'.code.toByte() &&
                inputBytes[2] == 'F'.code.toByte() &&
                inputBytes[3] == 'F'.code.toByte()
            ) {
                sampleRate = ByteBuffer.wrap(inputBytes, 24, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .int
                    .takeIf { it in 8000..96000 } ?: defaultSampleRate

                // Locate the "data" chunk marker inside the first 256 bytes
                val searchLimit = minOf(inputBytes.size - 8, 256)
                var foundDataOffset = 44
                for (i in 12..searchLimit) {
                    if (inputBytes[i] == 'd'.code.toByte() &&
                        inputBytes[i + 1] == 'a'.code.toByte() &&
                        inputBytes[i + 2] == 't'.code.toByte() &&
                        inputBytes[i + 3] == 'a'.code.toByte()
                    ) {
                        foundDataOffset = i + 8
                        break
                    }
                }
                pcmStartOffset = foundDataOffset
            }

            val pcmLength = ((inputBytes.size - pcmStartOffset).coerceAtLeast(0)) and -2
            val totalDataLen = pcmLength + 36
            val byteRate = sampleRate * 2

            outputFile.parentFile?.mkdirs()
            BufferedOutputStream(FileOutputStream(outputFile), 32 * 1024).use { bos ->
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                    put("RIFF".toByteArray(Charsets.US_ASCII))
                    putInt(totalDataLen)
                    put("WAVE".toByteArray(Charsets.US_ASCII))
                    put("fmt ".toByteArray(Charsets.US_ASCII))
                    putInt(16) // Subchunk1Size for PCM
                    putShort(1) // AudioFormat 1 = PCM
                    putShort(1) // NumChannels 1 = Mono
                    putInt(sampleRate)
                    putInt(byteRate)
                    putShort(2) // BlockAlign = 2 bytes per sample
                    putShort(16) // BitsPerSample = 16
                    put("data".toByteArray(Charsets.US_ASCII))
                    putInt(pcmLength)
                }.array()
                bos.write(header)
                if (pcmLength > 0) {
                    bos.write(inputBytes, pcmStartOffset, pcmLength)
                }
            }
        }

        fun resolveAudioDurationMs(context: Context, file: File): Long {
            if (!file.exists() || file.length() <= 44L) return 0L
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(file.absolutePath)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val parsed = durStr?.toLongOrNull() ?: 0L
                if (parsed > 0L) parsed else estimateWavDurationMs(file)
            } catch (_: Exception) {
                estimateWavDurationMs(file)
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
        }

        fun estimateWavDurationMs(file: File): Long {
            val dataBytes = (file.length() - 44L).coerceAtLeast(0L)
            val sampleRate = try {
                RandomAccessFile(file, "r").use { raf ->
                    if (raf.length() >= 28L) {
                        val buf = ByteArray(4)
                        raf.seek(24L)
                        raf.readFully(buf)
                        ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).int.takeIf { it in 8000..96000 } ?: 24000
                    } else {
                        24000
                    }
                }
            } catch (_: Exception) {
                24000
            }
            return ((dataBytes * 1000L) / (sampleRate * 2L)).coerceAtLeast(600L)
        }

        fun extractWaveformFast(file: File, barsCount: Int = 36): List<Float> {
            val length = file.length()
            if (!file.exists() || length <= 128L) {
                return PlaybackUiState.DEFAULT_WAVEFORM
            }
            return try {
                val payloadLength = length - 44L
                val stepBytes = (payloadLength / barsCount).coerceAtLeast(2L)
                val rawPeaks = FloatArray(barsCount)
                var maxPeak = 0.01f
                val windowBuffer = ByteArray(64)

                RandomAccessFile(file, "r").use { raf ->
                    for (bar in 0 until barsCount) {
                        val seekPos = (44L + (bar * stepBytes)) and -2L
                        raf.seek(seekPos)
                        val bytesRead = raf.read(windowBuffer)
                        var sumAbs = 0L
                        var sampleCount = 0
                        var idx = 0
                        while (idx + 1 < bytesRead) {
                            val low = windowBuffer[idx].toInt() and 0xFF
                            val high = windowBuffer[idx + 1].toInt()
                            val sample = (high shl 8) or low
                            sumAbs += abs(sample)
                            sampleCount++
                            idx += 2
                        }
                        val avg = if (sampleCount > 0) (sumAbs.toFloat() / sampleCount) / 32768f else 0.1f
                        rawPeaks[bar] = avg
                        if (avg > maxPeak) maxPeak = avg
                    }
                }

                rawPeaks.map { peak ->
                    ((peak / maxPeak) * 0.84f + 0.14f).coerceIn(0.14f, 0.98f)
                }
            } catch (_: Exception) {
                PlaybackUiState.DEFAULT_WAVEFORM
            }
        }

        private fun generateSyllabicCadenceWav(text: String, speed: Float, outputFile: File) {
            val sampleRate = 24000
            val wordCount = countWordsFast(text).coerceAtLeast(1)
            val durationSeconds = ((wordCount * 0.28f) / speed.coerceIn(0.6f, 1.5f)).coerceIn(1.0f, 5.5f)
            val numSamples = (sampleRate * durationSeconds).toInt()
            val pcmData = ByteArray(numSamples * 2)

            val chars = text.ifEmpty { "SonicVoice" }
            val twoPiOverRate = (2.0 * Math.PI) / sampleRate
            for (i in 0 until numSamples) {
                val syllableIndex = ((i * chars.length) / numSamples).coerceIn(0, chars.lastIndex)
                val charSeed = (chars[syllableIndex].code % 12)
                val baseFreq = 165.0 + charSeed * 14.0
                val phase = i * twoPiOverRate
                val syllableEnv = sin(phase * 1.5) * 0.5 + 0.5
                val fadeEnv = when {
                    i < 800 -> i / 800.0
                    i > numSamples - 1200 -> (numSamples - i) / 1200.0
                    else -> 1.0
                }
                val wave = sin(baseFreq * phase) * 0.7 + sin(baseFreq * 1.5 * phase) * 0.3
                val sampleVal = (wave * syllableEnv * fadeEnv * 14000.0).toInt().coerceIn(-32767, 32767)
                pcmData[i * 2] = (sampleVal and 0xFF).toByte()
                pcmData[i * 2 + 1] = ((sampleVal shr 8) and 0xFF).toByte()
            }

            writeRawPcmOrWavToStandardWavFile(pcmData, defaultSampleRate = sampleRate, outputFile = outputFile)
        }
    }
}
