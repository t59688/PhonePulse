package com.aizeek.phonepulse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "screen_sessions")
data class ScreenSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // "SCREEN_ON" or "SCREEN_OFF"
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    val dateKey: String // Format: "yyyy-MM-dd"
) {
    val isScreenOn: Boolean get() = type == "SCREEN_ON"
}
