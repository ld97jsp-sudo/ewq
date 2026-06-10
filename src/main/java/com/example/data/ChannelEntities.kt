package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_channels")
data class FavoriteChannel(
    @PrimaryKey val streamId: Int,
    val name: String,
    val streamIcon: String?,
    val categoryId: String?
)

@Entity(tableName = "recent_channels")
data class RecentChannel(
    @PrimaryKey val streamId: Int,
    val name: String,
    val streamIcon: String?,
    val categoryId: String?,
    val timestamp: Long = System.currentTimeMillis()
)
