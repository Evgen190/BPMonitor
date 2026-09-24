package com.example.bpmonitor

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bp_records")
data class BpRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systolic: Int = 0,
    val diastolic: Int = 0,
    val pulse: Int = 0,
    val timestamp: Long,
    val period: String,
    val alcohol: Boolean = false,
    val hookah: Boolean = false
)
