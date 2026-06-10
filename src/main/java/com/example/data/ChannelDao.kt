package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM favorite_channels ORDER BY name ASC")
    fun getAllFavorites(): Flow<List<FavoriteChannel>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(channel: FavoriteChannel)

    @Query("DELETE FROM favorite_channels WHERE streamId = :streamId")
    suspend fun removeFavorite(streamId: Int)

    @Query("SELECT * FROM favorite_channels WHERE streamId = :streamId LIMIT 1")
    suspend fun getFavoriteById(streamId: Int): FavoriteChannel?

    @Query("SELECT * FROM recent_channels ORDER BY timestamp DESC LIMIT 30")
    fun getAllRecents(): Flow<List<RecentChannel>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecent(channel: RecentChannel)

    @Query("DELETE FROM recent_channels WHERE streamId = :streamId")
    suspend fun deleteRecent(streamId: Int)

    @Query("DELETE FROM recent_channels")
    suspend fun clearAllRecents()
}
