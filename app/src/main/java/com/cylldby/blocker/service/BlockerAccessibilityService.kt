package com.cylldby.blocker.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.PixelFormat
import android.os.CountDownTimer
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.TextView
import com.cylldby.blocker.BuildConfig
import com.cylldby.blocker.BlockerApp
import com.cylldby.blocker.R
import com.cylldby.blocker.data.model.AppRule
import com.cylldby.blocker.util.QuickBlockManager
import com.cylldby.blocker.util.ScheduleChecker
import com.cylldby.blocker.util.UsageStatsHelper
import kotlinx.coroutines.*

class BlockerAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var activeRules: List<AppRule> = emptyList()

    private var overlayView: View? = null
    private var dismissJob: Job? = null

    companion object {
        const val EXTRA_REASON    = "reason"
        const val REASON_QUOTA    = "quota"
        const val REASON_SCHEDULE = "schedule"
        const val REASON_QUICK_BLOCK = "quick_block"
        const val REASON_TAMPER_SHIELD = "tamper_shield"
        private const val TAG              = "BlockerService"
        private const val AUTO_DISMISS_MS  = 6_000L

        private val FREEZER_PACKAGES = setOf(
            "com.transsion.freezer",
            "com.transsion.phonemaster",
            "com.transsion.powerchecker",
            "com.transsion.resourcemanager",
            "com.oasisfeng.greenify",
            "com.catchingnow.icebox",
            "rikka.appops",
            "com.real.clearprocesses",
            "com.wakasoftware.appfreezer"
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceScope.launch {
            (application as BlockerApp).repository.allAppRules.collect { all ->
                activeRules = all.filter { it.isActive }
                Log.d(TAG, "Rules synced: ${activeRules.size} active")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return

        val pkg = event.packageName?.toString()?.lowercase() ?: return
        if (pkg == packageName.lowercase() || pkg == "android") return

        val className = event.className?.toString()?.lowercase() ?: ""
        val eventText = event.text?.joinToString(" ")?.lowercase() ?: ""
        val contentDesc = event.contentDescription?.toString()?.lowercase() ?: ""

        // --- FREEZER GUARD ---
        val isFreezerPkg = FREEZER_PACKAGES.contains(pkg)
        val isFreezerClass = className.contains("freezer") ||
                (pkg.contains("transsion") && className.contains("freeze"))
        val isFreezerText = eventText.contains("freezer") || contentDesc.contains("freezer") ||
                eventText.contains("apps have been frozen") || contentDesc.contains("apps have been frozen") ||
                (eventText.contains("saving") && eventText.contains("frozen")) ||
                (contentDesc.contains("saving") && contentDesc.contains("frozen"))

        val rootHasFreezer = if (pkg.contains("transsion") || pkg.contains("launcher") || pkg.contains("phonemaster")) {
            try {
                val root = rootInActiveWindow
                if (root != null) {
                    val m1 = root.findAccessibilityNodeInfosByText("Freezer")
                    val m2 = root.findAccessibilityNodeInfosByText("frozen")
                    val m3 = root.findAccessibilityNodeInfosByText("Freeze")
                    !m1.isNullOrEmpty() || !m2.isNullOrEmpty() || !m3.isNullOrEmpty()
                } else false
            } catch (_: Exception) { false }
        } else false

        if (isFreezerPkg || isFreezerClass || isFreezerText || rootHasFreezer) {
            Log.w(TAG, "Freezer screen detected! Blocking access: pkg=$pkg class=$className text=$eventText")
            goHome()
            showBlockingOverlay("Freezer Guard", REASON_TAMPER_SHIELD)
            return
        }

        // --- SETTINGS & PACKAGE INSTALLER SELF-DEFENSE ---
        if (pkg.contains("settings") || pkg.contains("packageinstaller")) {
            val isTargetingBlocker = eventText.contains("blocker") || contentDesc.contains("blocker") ||
                    className.contains("appdetails") || className.contains("deviceadmin")
            val rootHasBlocker = try {
                val root = rootInActiveWindow
                if (root != null) {
                    val blockerNodes = root.findAccessibilityNodeInfosByText("Blocker")
                    val actionNodes = root.findAccessibilityNodeInfosByText("Force stop")
                    val uninstallNodes = root.findAccessibilityNodeInfosByText("Uninstall")
                    val deactivateNodes = root.findAccessibilityNodeInfosByText("Deactivate")
                    !blockerNodes.isNullOrEmpty() && (!actionNodes.isNullOrEmpty() || !uninstallNodes.isNullOrEmpty() || !deactivateNodes.isNullOrEmpty())
                } else false
            } catch (_: Exception) { false }

            if (isTargetingBlocker || rootHasBlocker) {
                Log.w(TAG, "Settings tamper attempted against Blocker. Redirecting to Home.")
                goHome()
                showBlockingOverlay("Settings Shield", REASON_TAMPER_SHIELD)
                return
            }
        }

        if (overlayView != null && (pkg.contains("launcher") || pkg == "com.android.launcher3")) return

        // Skip coroutine launch entirely if neither Quick Block nor any rule applies.
        val quickBlockActive = QuickBlockManager.isActive(this)
        if (!quickBlockActive && activeRules.none { it.packageName == pkg }) return

        serviceScope.launch {
            // Quick Block check
            if (quickBlockActive &&
                !QuickBlockManager.isAllowed(this@BlockerAccessibilityService, pkg)) {
                val remaining = QuickBlockManager.getRemainingMs(this@BlockerAccessibilityService)
                val mins = (remaining / 60_000).toInt()
                val appName = try {
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(pkg, 0)
                    ).toString()
                } catch (_: Exception) { pkg }
                withContext(Dispatchers.Main) {
                    showBlockingOverlay(appName, REASON_QUICK_BLOCK, mins)
                }
                return@launch
            }

            val rule = activeRules.find { it.packageName == pkg } ?: return@launch

            val reason = checkBlocked(rule) ?: return@launch
            if (BuildConfig.DEBUG) Log.d(TAG, "Blocking $pkg — $reason")
            withContext(Dispatchers.Main) { showBlockingOverlay(rule.appName, reason) }
        }
    }

    private fun checkBlocked(rule: AppRule): String? {
        if (ScheduleChecker.isScheduleBlocked(rule)) return REASON_SCHEDULE
        val used = UsageStatsHelper.getTodayUsageMinutes(this, rule.packageName)
        if (BuildConfig.DEBUG) Log.d(TAG, "Usage for ${rule.packageName}: ${used}min / ${rule.dailyLimitMinutes}min limit")
        if (ScheduleChecker.isQuotaExceeded(rule, used)) return REASON_QUOTA
        return null
    }

    private fun showBlockingOverlay(appName: String, reason: String, remainingMins: Int = -1) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "No overlay permission — going home directly")
            goHome()
            return
        }

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        removeOverlay(wm)

        @SuppressLint("InflateParams")
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_block_warning, null)

        view.findViewById<TextView>(R.id.tvOverlayTitle).text = when (reason) {
            REASON_SCHEDULE -> "Schedule Limit"
            REASON_QUICK_BLOCK -> "Quick Block"
            REASON_TAMPER_SHIELD -> "Protection Shield"
            else -> "Daily Limit Reached"
        }
        view.findViewById<TextView>(R.id.tvOverlayMessage).text = when (reason) {
            REASON_SCHEDULE -> "$appName is blocked right now"
            REASON_QUICK_BLOCK -> if (remainingMins > 0) "Quick Block — ${remainingMins}m remaining"
                                   else "Quick Block active"
            REASON_TAMPER_SHIELD -> "Freezer and Settings are protected to keep your block active."
            else -> "$appName — daily quota reached"
        }

        val btnEmergency = view.findViewById<Button>(R.id.btnEmergencyStop)
        if (reason == REASON_QUICK_BLOCK) {
            btnEmergency?.visibility = View.VISIBLE
            btnEmergency?.setOnClickListener {
                showEmergencyStopDialog(wm)
            }
        } else {
            btnEmergency?.visibility = View.GONE
        }

        view.setOnClickListener {
            dismissJob?.cancel()
            val wm2 = getSystemService(WINDOW_SERVICE) as WindowManager
            removeOverlay(wm2)
            goHome()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        wm.addView(view, params)
        overlayView = view

        dismissJob?.cancel()
        dismissJob = serviceScope.launch {
            delay(AUTO_DISMISS_MS)
            withContext(Dispatchers.Main) {
                val wm2 = getSystemService(WINDOW_SERVICE) as WindowManager
                removeOverlay(wm2)
                goHome()
            }
        }
    }

    private fun showEmergencyStopDialog(wm: WindowManager) {
        dismissJob?.cancel()
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Emergency Stop?")
            .setMessage("Stop Quick Block? (10s)")
            .setPositiveButton("Stop (10s)") { _, _ ->
                QuickBlockManager.stop(this)
                removeOverlay(wm)
                goHome()
            }
            .setNegativeButton("Keep blocking", null)
            .create()
        dialog.show()

        val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        btn.isEnabled = false

        val timer = object : CountDownTimer(10_000, 1000) {
            override fun onTick(ms: Long) {
                val s = ms / 1000 + 1
                @SuppressLint("SetTextI18n")
                btn.text = "Stop (${s}s)"
            }
            override fun onFinish() {
                btn.isEnabled = true
                btn.setText(R.string.btn_emergency_stop)
            }
        }
        timer.start()
        dialog.setOnDismissListener { timer.cancel() }
    }

    private fun goHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        performGlobalAction(GLOBAL_ACTION_BACK)
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        } catch (_: Exception) {}
    }

    private fun removeOverlay(wm: WindowManager) {
        overlayView?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
            overlayView = null
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        removeOverlay(wm)
        serviceScope.cancel()
    }
}
