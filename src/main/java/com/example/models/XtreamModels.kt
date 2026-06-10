package com.example.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LiveCategory(
    @Json(name = "category_id") val categoryId: String,
    @Json(name = "category_name") val categoryName: String,
    @Json(name = "parent_id") val parentId: Int = 0
)

@JsonClass(generateAdapter = true)
data class LiveStream(
    @Json(name = "num") val num: Int? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "stream_id") val streamId: Int,
    @Json(name = "stream_icon") val streamIcon: String? = null,
    @Json(name = "category_id") val categoryId: String? = null,
    @Json(name = "epg_channel_id") val epgChannelId: String? = null,
    @Json(name = "added") val added: String? = null,
    @Json(name = "custom_sid") val customSid: String? = null,
    @Json(name = "direct_source") val directSource: String? = null,
    
    // Series support JSON properties mapping
    @Json(name = "series_id") val seriesId: Int? = null,
    @Json(name = "cover") val cover: String? = null,
    
    // VoD container support mapping
    @Json(name = "container_extension") val containerExtension: String? = null
)

data class MediaActor(
    val name: String,
    val imageUrl: String? = null
)

data class VodMovieInfo(
    val name: String,
    val plot: String?,
    val cast: List<MediaActor>,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    val cover: String?,
    val duration: String?
)

data class SeriesEpisode(
    val id: String,
    val episodeNum: Int,
    val title: String,
    val plot: String?,
    val containerExtension: String
)

data class SeriesInfoDetail(
    val name: String,
    val plot: String?,
    val cast: List<MediaActor>,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    val cover: String?,
    val seasons: Map<Int, List<SeriesEpisode>> // Season numbering mapping
)

data class FirebaseChannel(
    val id: String = "",
    val name: String = "",
    val logoUrl: String = "",
    val streamUrl: String = "",
    val streamType: String = "Smart View", // Smart View, Proxy, TS, M3U8, Regular
    val userAgent: String = "",
    val categoryId: String = ""
)

data class FirebaseCategory(
    val id: String = "",
    val name: String = "",
    val imageUrl: String = ""
)

