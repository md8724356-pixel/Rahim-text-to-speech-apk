package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TtsDao {
    @Query("SELECT * FROM tts_history ORDER BY createdAt DESC")
    fun getAllHistory(): Flow<List<TtsHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: TtsHistoryEntity): Long

    @Query("UPDATE tts_history SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("DELETE FROM tts_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM tts_history")
    suspend fun clearAll()
}
