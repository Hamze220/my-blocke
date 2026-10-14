package com.cylldby.blocker.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.cylldby.blocker.receiver.BlockerDeviceAdminReceiver
import com.cylldby.blocker.service.BlockerAccessibilityService

object PermissionHelper {

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasAccessibilityPermission(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val target = ComponentName(context, BlockerAccessibilityService::class.java)
        if (enabled.any {
            it.resolveInfo.serviceInfo.packageName == target.packageName &&
            it.resolveInfo.serviceInfo.name == target.className
        }) return true

        val flat = target.flattenToString()
        val raw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return raw.split(":").any { it.equals(flat, ignoreCase = true) }
    }

    fun hasOverlayPermission(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun hasDeviceAdminPermission(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val compName = ComponentName(context, BlockerDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(compName)
    }

    fun allPermissionsGranted(context: Context): Boolean =
        hasUsageStatsPermission(context) &&
        hasAccessibilityPermission(context) &&
        hasOverlayPermission(context) &&
        hasDeviceAdminPermission(context)
}
