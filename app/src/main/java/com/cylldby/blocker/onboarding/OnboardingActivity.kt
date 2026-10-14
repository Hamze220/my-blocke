package com.cylldby.blocker.onboarding

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.cylldby.blocker.databinding.ActivityOnboardingBinding
import com.cylldby.blocker.receiver.BlockerDeviceAdminReceiver
import com.cylldby.blocker.util.PermissionHelper

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnUsageStats.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnOverlay.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        binding.btnDeviceAdmin.setOnClickListener {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    ComponentName(this@OnboardingActivity, BlockerDeviceAdminReceiver::class.java)
                )
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Ka-hortagga tirtiridda app-ka (Anti-Uninstall Protection)"
                )
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        updateButtonStates()
        if (PermissionHelper.allPermissionsGranted(this)) finish()
    }

    private fun updateButtonStates() {
        binding.btnUsageStats.isEnabled = !PermissionHelper.hasUsageStatsPermission(this)
        binding.btnAccessibility.isEnabled = !PermissionHelper.hasAccessibilityPermission(this)
        binding.btnOverlay.isEnabled = !PermissionHelper.hasOverlayPermission(this)
        binding.btnDeviceAdmin.isEnabled = !PermissionHelper.hasDeviceAdminPermission(this)
    }
}
