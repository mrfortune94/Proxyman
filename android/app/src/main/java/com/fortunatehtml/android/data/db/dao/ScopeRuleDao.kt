package com.fortunatehtml.android.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.fortunatehtml.android.data.db.entity.ScopeRule

@Dao
interface ScopeRuleDao {
    @Query("SELECT * FROM scope_rules ORDER BY environment, host")
    fun getAllLive(): LiveData<List<ScopeRule>>

    @Query("SELECT * FROM scope_rules WHERE isEnabled = 1")
    suspend fun getEnabled(): List<ScopeRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: ScopeRule)

    @Update
    suspend fun update(rule: ScopeRule)

    @Delete
    suspend fun delete(rule: ScopeRule)
}
