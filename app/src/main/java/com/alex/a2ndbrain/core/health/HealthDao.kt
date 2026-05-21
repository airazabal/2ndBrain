package com.alex.a2ndbrain.core.health

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HealthDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: HealthSnapshotEntity)

    @Query("SELECT * FROM health_snapshots WHERE date = :date LIMIT 1")
    suspend fun getSnapshotForDate(date: String): HealthSnapshotEntity?

    @Query("SELECT * FROM health_snapshots WHERE date >= :sinceDate ORDER BY date DESC")
    suspend fun getSnapshotsSince(sinceDate: String): List<HealthSnapshotEntity>
}
