package com.example.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "tts_history")
data class TtsHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transcript: String,
    val voiceId: String,
    val voiceName: String,
    val modelId: String,
    val languageCode: String,
    val engineUsed: String,
    val playbackSpeed: Float,
    val audioFilePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
