package com.alex.a2ndbrain.core.senseofday

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SenseOfDaySnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: SenseOfDaySnapshotEntity)

    @Query("SELECT * FROM sense_of_day_snapshots ORDER BY date DESC LIMIT :limit")
    fun getRecentFlow(limit: Int): Flow<List<SenseOfDaySnapshotEntity>>

    @Query("SELECT * FROM sense_of_day_snapshots WHERE date >= :sinceDate ORDER BY date ASC")
    suspend fun getSince(sinceDate: String): List<SenseOfDaySnapshotEntity>

    @Query("SELECT COUNT(*) FROM sense_of_day_snapshots")
    suspend fun getCount(): Int
}
