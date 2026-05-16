package com.cylldby.blocker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.cylldby.blocker.data.db.BlockerDatabase
import com.cylldby.blocker.data.repository.BlockerRepository

class BlockerApp : Application() {
    val database by lazy { BlockerDatabase.getInstance(this) }
    val repository by lazy {
        BlockerRepository(database.appRuleDao(), database.siteRuleDao(), database.siteUsageDao())
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Blocker VPN", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while site blocking is active"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BLOCKED_ID, "Site Blocked", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Shown when a website is blocked"
            }
        )
    }

    companion object {
        const val CHANNEL_ID         = "blocker_vpn"
        const val CHANNEL_BLOCKED_ID = "blocker_blocked"
    }
}
