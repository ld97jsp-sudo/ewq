package com.example.data

import com.example.models.LiveCategory
import com.example.models.LiveStream
import com.example.network.XtreamApiService
import kotlinx.coroutines.flow.Flow

class ChannelRepository(
    val apiService: XtreamApiService,
    private val channelDao: ChannelDao
) {
    val favorites: Flow<List<FavoriteChannel>> = channelDao.getAllFavorites()
    val recents: Flow<List<RecentChannel>> = channelDao.getAllRecents()

    suspend fun getLiveCategories(u: String, p: String): List<LiveCategory> {
        val stringBody = apiService.getLiveCategories(u, p).string()
        return XtreamApiService.parseLiveCategories(stringBody)
    }

    suspend fun getLiveStreams(u: String, p: String): List<LiveStream> {
        val stringBody = apiService.getLiveStreams(u, p).string()
        return XtreamApiService.parseLiveStreams(stringBody)
    }

    suspend fun addFavorite(channel: FavoriteChannel) {
        channelDao.insertFavorite(channel)
    }

    suspend fun removeFavorite(streamId: Int) {
        channelDao.removeFavorite(streamId)
    }

    suspend fun isFavorite(streamId: Int): Boolean {
        return channelDao.getFavoriteById(streamId) != null
    }

    suspend fun addRecent(channel: RecentChannel) {
        channelDao.insertRecent(channel)
    }

    suspend fun deleteRecent(streamId: Int) {
        channelDao.deleteRecent(streamId)
    }

    suspend fun clearRecents() {
        channelDao.clearAllRecents()
    }
}
