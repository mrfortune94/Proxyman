package com.fortunatehtml.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fortunatehtml.android.data.db.converters.Converters
import com.fortunatehtml.android.data.db.dao.LogDao
import com.fortunatehtml.android.data.db.dao.ProjectDao
import com.fortunatehtml.android.data.db.dao.ScopeRuleDao
import com.fortunatehtml.android.data.db.dao.TrafficEntryDao
import com.fortunatehtml.android.data.db.entity.LogEntry
import com.fortunatehtml.android.data.db.entity.Project
import com.fortunatehtml.android.data.db.entity.ScopeRule
import com.fortunatehtml.android.data.db.entity.TrafficEntryEntity

@Database(
    entities = [TrafficEntryEntity::class, Project::class, LogEntry::class, ScopeRule::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trafficDao(): TrafficEntryDao
    abstract fun projectDao(): ProjectDao
    abstract fun logDao(): LogDao
    abstract fun scopeRuleDao(): ScopeRuleDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pocket_api_inspector.db"
                ).build().also { INSTANCE = it }
            }
    }
}
