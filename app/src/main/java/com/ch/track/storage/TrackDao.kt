package com.ch.track.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Insert
    suspend fun insertPoints(points: List<PointEntity>)

    @Query("SELECT * FROM sessions ORDER BY endTime DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM points WHERE sessionId = :sessionId ORDER BY time ASC")
    suspend fun pointsBySession(sessionId: String): List<PointEntity>

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    suspend fun sessionById(sessionId: String): SessionEntity?

    @Query("DELETE FROM sessions")
    suspend fun clearSessions()

    @Query("UPDATE sessions SET isFavorite = :favorite WHERE id = :sessionId")
    suspend fun updateFavorite(sessionId: String, favorite: Boolean)
}
