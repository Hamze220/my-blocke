package com.cylldby.blocker.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class BlockerDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Blocker Protection Activated: Uninstall & Freeze locked", Toast.LENGTH_LONG).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Warning shown when user tries to deactivate admin to uninstall/freeze
        return "Warning: Disabling Device Admin will disable your self-protection shield against adult content and addictive apps."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "Protection disabled", Toast.LENGTH_SHORT).show()
    }
}
