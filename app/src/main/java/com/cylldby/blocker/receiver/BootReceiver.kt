package com.cylldby.blocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cylldby.blocker.service.BlockerVpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("blocker_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("vpn_enabled", false)) return
        context.startForegroundService(
            Intent(context, BlockerVpnService::class.java).apply {
                action = BlockerVpnService.ACTION_START
            }
        )
    }
}
