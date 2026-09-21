package com.example.bpmonitor

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BpDao {
    @Query("SELECT * FROM bp_records ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<BpRecord>>

    @Query("SELECT * FROM bp_records WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp ASC")
    suspend fun getRange(from: Long, to: Long): List<BpRecord>

    @Insert suspend fun insert(record: BpRecord): Long
    @Delete suspend fun delete(record: BpRecord)
}
