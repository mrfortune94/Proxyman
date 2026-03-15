package com.fortunatehtml.android.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.fortunatehtml.android.data.db.entity.Project

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllLive(): LiveData<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: String): Project?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: Project)

    @Update
    suspend fun update(project: Project)

    @Delete
    suspend fun delete(project: Project)
}
