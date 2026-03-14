package com.fortunatehtml.android.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.fortunatehtml.android.data.db.entity.TrafficEntryEntity

@Dao
interface TrafficEntryDao {
    @Query("SELECT * FROM traffic_entries ORDER BY timestamp DESC")
    fun getAllLive(): LiveData<List<TrafficEntryEntity>>

    @Query("SELECT * FROM traffic_entries ORDER BY timestamp DESC")
    suspend fun getAll(): List<TrafficEntryEntity>

    @Query("SELECT * FROM traffic_entries WHERE id = :id")
    suspend fun getById(id: String): TrafficEntryEntity?

    @Query("SELECT * FROM traffic_entries WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getByProjectLive(projectId: String): LiveData<List<TrafficEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TrafficEntryEntity)

    @Update
    suspend fun update(entry: TrafficEntryEntity)

    @Delete
    suspend fun delete(entry: TrafficEntryEntity)

    @Query("DELETE FROM traffic_entries")
    suspend fun deleteAll()

    @Query("DELETE FROM traffic_entries WHERE projectId IS NULL")
    suspend fun deleteUnassigned()
}
