package com.example.bpmonitor

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bp_records")
data class BpRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int,
    val timestamp: Long,
    val period: String
)
