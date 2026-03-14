package com.fortunatehtml.android.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.fortunatehtml.android.data.db.entity.LogEntry

@Dao
interface LogDao {
    @Query("SELECT * FROM log_entries ORDER BY timestamp DESC")
    fun getAllLive(): LiveData<List<LogEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LogEntry)

    @Query("DELETE FROM log_entries")
    suspend fun deleteAll()
}
