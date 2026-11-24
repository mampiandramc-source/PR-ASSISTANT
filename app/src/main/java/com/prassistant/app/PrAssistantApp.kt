package com.prassistant.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class PrAssistantApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                "Assistant flottant PR ASSISTANT",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintient la mini-fenêtre flottante et le suivi de session"
            }

            val breakChannel = NotificationChannel(
                CHANNEL_BREAK_ID,
                "Fin de pause et alertes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerte lorsque la période de repos est terminée"
            }

            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(breakChannel)
        }
    }

    companion object {
        const val CHANNEL_SERVICE_ID = "pr_assistant_service_channel"
        const val CHANNEL_BREAK_ID = "pr_assistant_break_channel"
    }
}
