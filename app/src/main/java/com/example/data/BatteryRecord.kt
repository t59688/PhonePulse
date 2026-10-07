package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_records")
data class BatteryRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val level: Int, // 0..100
    val scale: Int = 100,
    val percentage: Int, // 0..100
    val isCharging: Boolean,
    val plugType: String, // "AC", "USB", "WIRELESS", "NONE"
    val health: String, // "GOOD", "OVERHEAT", "COLD", "UNKNOWN"
    val temperature: Float, // in Celsius
    val voltage: Int, // in mV
    val screenState: String, // "SCREEN_ON" or "SCREEN_OFF"
    val dateKey: String
)
