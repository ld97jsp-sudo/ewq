package com.example.network

import android.util.Log
import com.example.models.FirebaseChannel
import com.example.models.FirebaseCategory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

object FirebaseService {
    private const val BASE_URL = "https://loop-7e3d9-default-rtdb.firebaseio.com"
    private val client = OkHttpClient()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // Verifies activation code and returns Triple(host, username, password) if successful
    suspend fun checkActivationCode(code: String): Triple<String, String, String>? = withContext(Dispatchers.IO) {
        try {
            val cleanCode = code.trim()
            val url = "https://firestore.googleapis.com/v1/projects/loop-7e3d9/databases/(default)/documents/activation_codes/$cleanCode"
            val request = Request.Builder().url(url).build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("FirebaseService", "Firestore response unsuccessful: ${response.code}")
                    return@withContext null
                }
                val bodyString = response.body?.string() ?: return@withContext null
                if (bodyString.isBlank()) return@withContext null
                
                val json = JSONObject(bodyString)
                val fields = json.optJSONObject("fields") ?: return@withContext null
                
                val hostObj = fields.optJSONObject("host")
                val host = hostObj?.optString("stringValue", "")?.trim() ?: ""
                
                val userObj = fields.optJSONObject("username")
                val user = userObj?.optString("stringValue", "")?.trim() ?: ""
                
                val passObj = fields.optJSONObject("password")
                val pass = passObj?.optString("stringValue", "")?.trim() ?: ""
                
                if (host.isNotEmpty() && user.isNotEmpty() && pass.isNotEmpty()) {
                    return@withContext Triple(host, user, pass)
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "Error validating activation code", e)
        }
        return@withContext null
    }

    // Load custom Firebase Categories
    suspend fun fetchCategories(): List<FirebaseCategory> = withContext(Dispatchers.IO) {
        val list = mutableListOf<FirebaseCategory>()
        try {
            val url = "$BASE_URL/main_categories.json"
            val request = Request.Builder().url(url).build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyString = response.body?.string() ?: return@withContext emptyList()
                if (bodyString == "null" || bodyString.isBlank()) return@withContext emptyList()
                
                val root = JSONObject(bodyString)
                val keys = root.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val item = root.getJSONObject(id)
                    list.add(
                        FirebaseCategory(
                            id = id,
                            name = item.optString("name", "Unknown"),
                            imageUrl = item.optString("imageUrl", "")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "Error loading firebase categories", e)
        }
        return@withContext list
    }

    // Load custom Firebase Channels
    suspend fun fetchChannels(): List<FirebaseChannel> = withContext(Dispatchers.IO) {
        val list = mutableListOf<FirebaseChannel>()
        try {
            val url = "$BASE_URL/main_channels.json"
            val request = Request.Builder().url(url).build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyString = response.body?.string() ?: return@withContext emptyList()
                if (bodyString == "null" || bodyString.isBlank()) return@withContext emptyList()
                
                val root = JSONObject(bodyString)
                val keys = root.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val item = root.getJSONObject(id)
                    list.add(
                        FirebaseChannel(
                            id = id,
                            name = item.optString("name", ""),
                            logoUrl = item.optString("logoUrl", ""),
                            streamUrl = item.optString("streamUrl", ""),
                            streamType = item.optString("streamType", "Smart View"),
                            userAgent = item.optString("userAgent", ""),
                            categoryId = item.optString("categoryId", "")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "Error loading firebase channels", e)
        }
        return@withContext list
    }

    // Write a category to Firebase RTDB via PUT
    suspend fun publishCategory(name: String, imageUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val id = "cat_${System.currentTimeMillis()}"
            val url = "$BASE_URL/main_categories/$id.json"
            
            val json = JSONObject().apply {
                put("name", name)
                put("imageUrl", imageUrl)
            }
            
            val body = json.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder().url(url).put(body).build()
            
            client.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "Failed to publish category", e)
            return@withContext false
        }
    }

    // Write a channel to Firebase RTDB via PUT
    suspend fun publishChannel(channel: FirebaseChannel): Boolean = withContext(Dispatchers.IO) {
        try {
            val id = if (channel.id.isEmpty()) "chan_${System.currentTimeMillis()}" else channel.id
            val url = "$BASE_URL/main_channels/$id.json"
            
            val json = JSONObject().apply {
                put("name", channel.name)
                put("logoUrl", channel.logoUrl)
                put("streamUrl", channel.streamUrl)
                put("streamType", channel.streamType)
                put("userAgent", channel.userAgent)
                put("categoryId", channel.categoryId)
            }
            
            val body = json.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder().url(url).put(body).build()
            
            client.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "Failed to publish channel", e)
            return@withContext false
        }
    }

    // Delete a Category from Firebase RTDB
    suspend fun deleteCategory(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/main_categories/$id.json"
            val request = Request.Builder().url(url).delete().build()
            
            client.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "Failed to delete category", e)
            return@withContext false
        }
    }

    // Delete a Channel from Firebase RTDB
    suspend fun deleteChannel(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/main_channels/$id.json"
            val request = Request.Builder().url(url).delete().build()
            
            client.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("FirebaseService", "Failed to delete channel", e)
            return@withContext false
        }
    }
}
