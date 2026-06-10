package com.example.network

import android.util.Log
import com.example.models.LiveCategory
import com.example.models.LiveStream
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface XtreamApiService {
    @GET("player_api.php")
    suspend fun getLiveCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_categories"
    ): ResponseBody

    @GET("player_api.php")
    suspend fun getLiveStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_streams"
    ): ResponseBody

    @GET("player_api.php")
    suspend fun getVodCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_categories"
    ): ResponseBody

    @GET("player_api.php")
    suspend fun getVodStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_streams"
    ): ResponseBody

    @GET("player_api.php")
    suspend fun getSeriesCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_categories"
    ): ResponseBody

    @GET("player_api.php")
    suspend fun getSeries(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series"
    ): ResponseBody

    @GET("player_api.php")
    suspend fun getVodInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_info",
        @Query("vod_id") vodId: Int
    ): ResponseBody

    @GET("player_api.php")
    suspend fun getSeriesInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_info",
        @Query("series_id") seriesId: Int
    ): ResponseBody

    companion object {
        private fun sanitizeJsonString(raw: String?): String {
            if (raw == null) return ""
            var trimmed = raw.trim()
            // Strip UTF-8 BOM if present
            if (trimmed.startsWith("\uFEFF")) {
                trimmed = trimmed.substring(1).trim()
            }
            if (trimmed.startsWith("\\uFEFF")) {
                trimmed = trimmed.substring(6).trim()
            }
            
            // Look for first '{' or '[' to bypass any misconfigured PHP warnings/notices printed at the top
            val firstObject = trimmed.indexOf('{')
            val firstArray = trimmed.indexOf('[')
            val startIndex = if (firstObject != -1 && firstArray != -1) {
                java.lang.Math.min(firstObject, firstArray)
            } else if (firstObject != -1) {
                firstObject
            } else if (firstArray != -1) {
                firstArray
            } else {
                -1
            }
            
            if (startIndex > 0) {
                trimmed = trimmed.substring(startIndex)
            }
            
            // Strip trailing junk after the JSON closed bracket
            if (trimmed.startsWith("[")) {
                val lastBracket = trimmed.lastIndexOf(']')
                if (lastBracket != -1) {
                    trimmed = trimmed.substring(0, lastBracket + 1)
                }
            } else if (trimmed.startsWith("{")) {
                val lastBrace = trimmed.lastIndexOf('}')
                if (lastBrace != -1) {
                    trimmed = trimmed.substring(0, lastBrace + 1)
                }
            }
            return trimmed
        }

        fun create(baseUrl: String): XtreamApiService {
            val trimmedUrl = baseUrl.trim()
            val cleanUrl = if (trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")) {
                trimmedUrl
            } else {
                "http://$trimmedUrl"
            }
            val url = if (cleanUrl.endsWith("/")) cleanUrl else "$cleanUrl/"
            
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            
            // Build a trust-all SSL trust manager to support expired, local or self-signed IPTV SSL certificates
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
                object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                }
            )

            val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            
            val okHttpClient = OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .addInterceptor { chain ->
                    val original = chain.request()
                    val request = original.newBuilder()
                        .header("User-Agent", "IPTVSmarters/1.0.0 (iPad; iOS 14.4; Scale/2.00)")
                        .header("Accept", "*/*")
                        .build()
                    chain.proceed(request)
                }
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(url)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(XtreamApiService::class.java)
        }

        // Extremely robust parsing logic for categories to prevent Moshi's type strictness and potential schema incompatibility crashes
        fun parseLiveCategories(jsonStr: String?): List<LiveCategory> {
            val list = mutableListOf<LiveCategory>()
            if (jsonStr.isNullOrBlank()) return list
            try {
                val trimmed = sanitizeJsonString(jsonStr)
                if (trimmed.startsWith("[")) {
                    val arr = JSONArray(trimmed)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val catId = if (obj.has("category_id")) {
                            obj.optString("category_id", "")
                        } else {
                            ""
                        }
                        val name = obj.optString("category_name", "").trim()
                        if (name.isNotEmpty()) {
                            list.add(LiveCategory(categoryId = catId, categoryName = name))
                        }
                    }
                } else if (trimmed.startsWith("{")) {
                    val root = JSONObject(trimmed)
                    val keys = root.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val obj = root.optJSONObject(key) ?: continue
                        val catId = obj.optString("category_id", key).trim()
                        val name = obj.optString("category_name", "").trim()
                        if (name.isNotEmpty()) {
                            list.add(LiveCategory(categoryId = catId, categoryName = name))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("XtreamApiService", "Error parsing categories resiliently", e)
            }
            return list
        }

        // Resilient stream list parser to filter out corrupt/incomplete stream objects and dynamically handle int/string data forms
        fun parseLiveStreams(jsonStr: String?): List<LiveStream> {
            val list = mutableListOf<LiveStream>()
            if (jsonStr.isNullOrBlank()) return list
            try {
                val trimmed = sanitizeJsonString(jsonStr)
                if (trimmed.startsWith("[")) {
                    val arr = JSONArray(trimmed)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        
                        // Extract required Stream ID
                        var streamId = obj.optInt("stream_id", -1)
                        if (streamId == -1 && obj.has("series_id")) {
                            streamId = obj.optInt("series_id", -1)
                        }
                        if (streamId == -1) continue

                        val name = obj.optString("name", obj.optString("title", "Unknown")).trim()
                        val icon = obj.optString("stream_icon", obj.optString("cover", "")).trim()
                        
                        // Extract category ID (might be String or Int)
                        var catId = ""
                        if (obj.has("category_id")) {
                            catId = obj.optString("category_id", "")
                        }

                        list.add(
                            LiveStream(
                                num = obj.optInt("num", i + 1),
                                name = name,
                                streamId = streamId,
                                streamIcon = if (icon.isNotEmpty()) icon else null,
                                categoryId = catId,
                                seriesId = if (obj.has("series_id")) obj.optInt("series_id") else null,
                                cover = if (obj.has("cover")) obj.optString("cover") else null,
                                containerExtension = if (obj.has("container_extension")) obj.optString("container_extension") else null
                            )
                        )
                    }
                } else if (trimmed.startsWith("{")) {
                    val root = JSONObject(trimmed)
                    val keys = root.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val obj = root.optJSONObject(key) ?: continue
                        
                        var streamId = obj.optInt("stream_id", -1)
                        if (streamId == -1 && obj.has("series_id")) {
                            streamId = obj.optInt("series_id", -1)
                        }
                        if (streamId == -1) {
                            streamId = key.toIntOrNull() ?: -1
                        }
                        if (streamId == -1) continue

                        val name = obj.optString("name", obj.optString("title", "Unknown")).trim()
                        val icon = obj.optString("stream_icon", obj.optString("cover", "")).trim()
                        
                        var catId = ""
                        if (obj.has("category_id")) {
                            catId = obj.optString("category_id", "")
                        }

                        list.add(
                            LiveStream(
                                num = obj.optInt("num", list.size + 1),
                                name = name,
                                streamId = streamId,
                                streamIcon = if (icon.isNotEmpty()) icon else null,
                                categoryId = catId,
                                seriesId = if (obj.has("series_id")) obj.optInt("series_id") else null,
                                cover = if (obj.has("cover")) obj.optString("cover") else null,
                                containerExtension = if (obj.has("container_extension")) obj.optString("container_extension") else null
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("XtreamApiService", "Error parsing streams resiliently", e)
            }
            return list
        }
    }
}
