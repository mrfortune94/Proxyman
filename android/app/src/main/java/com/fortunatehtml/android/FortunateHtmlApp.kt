package com.fortunatehtml.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.fortunatehtml.android.data.TrafficRepository
import com.fortunatehtml.android.data.db.AppDatabase
import com.fortunatehtml.android.data.db.entity.LogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FortunateHtmlApp : Application() {

    /** Application-wide coroutine scope (cancelled when process dies). */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Room database instance – initialized once on first access. */
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    /** In-memory live traffic repository (still used for UI observation). */
    lateinit var trafficRepository: TrafficRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        trafficRepository = TrafficRepository()
        createNotificationChannel()

        // Log app startup event
        applicationScope.launch {
            database.logDao().insert(
                LogEntry(
                    category = "startup",
                    message  = "Pocket API Inspector started",
                    detail   = "versionName=2.0"
                )
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Capture Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active API capture session notification"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "pocket_api_inspector_session"
        lateinit var instance: FortunateHtmlApp
            private set
    }
}
