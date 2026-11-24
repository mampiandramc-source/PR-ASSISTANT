package com.prassistant.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.prassistant.app.PrAssistantApp
import com.prassistant.app.R
import com.prassistant.app.data.AppSettingsEntity
import com.prassistant.app.data.PrDatabase
import com.prassistant.app.data.RestrictionLogEntity
import com.prassistant.app.data.SessionEntity
import com.prassistant.app.data.TaskItemEntity
import com.prassistant.app.ui.MainActivity
import kotlinx.coroutines.launch
import kotlin.random.Random

class FloatingAssistantService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    private val handler = Handler(Looper.getMainLooper())
    private var settings: AppSettingsEntity = AppSettingsEntity()

    private var sessionId: Long = 0L
    private var sessionTasksCount: Int = 0
    private var workSeconds: Long = 0L
    private var restSeconds: Long = 0L

    private var remainingDelaySec: Int = 0
    private var remainingBreakSec: Int = 0
    private var isWaitingDelay: Boolean = false
    private var isOnBreak: Boolean = false
    private var isManualPause: Boolean = false
    private var isRestrictedLock: Boolean = false
    private var isCompact: Boolean = false

    private var tvTimer: TextView? = null
    private var tvCounter: TextView? = null
    private var tvStateLabel: TextView? = null
    private var statusDot: View? = null
    private var expandedPanel: LinearLayout? = null
    private var btnPauseResume: Button? = null

    private val tickerRunnable = object : Runnable {
        override fun run() {
            onTickSecond()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        startForeground(SERVICE_NOTIFICATION_ID, buildForegroundNotification("PR ASSISTANT actif"))

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        lifecycleScope.launch {
            val db = PrDatabase.getInstance(this@FloatingAssistantService)
            settings = db.dao().getSettingsOnce() ?: AppSettingsEntity().also {
                db.dao().saveSettings(it)
            }
            isCompact = settings.isCompactOverlay
            sessionId = db.dao().insertSession(
                SessionEntity(startedAt = System.currentTimeMillis())
            )
            setupOverlayView()
            updateUiState()
            handler.postDelayed(tickerRunnable, 1000L)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlayView() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_assistant, null)
        overlayView = view

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 180
        }

        tvTimer = view.findViewById(R.id.tvTimer)
        tvCounter = view.findViewById(R.id.tvCounter)
        tvStateLabel = view.findViewById(R.id.tvStateLabel)
        statusDot = view.findViewById(R.id.statusDot)
        expandedPanel = view.findViewById(R.id.expandedPanel)
        btnPauseResume = view.findViewById(R.id.btnPauseResume)

        val btnToggleSize = view.findViewById<Button>(R.id.btnToggleSize)
        val btnCloseOverlay = view.findViewById<Button>(R.id.btnCloseOverlay)
        val btnMarkDone = view.findViewById<Button>(R.id.btnMarkDone)
        val btnReportRestriction = view.findViewById<Button>(R.id.btnReportRestriction)

        // Déplacement fluide de la mini-fenêtre sans bloquer les autres applis
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, layoutParams)
                    true
                }
                else -> false
            }
        }

        btnToggleSize.setOnClickListener {
            isCompact = !isCompact
            btnToggleSize.text = if (isCompact) "+" else "−"
            expandedPanel?.visibility = if (isCompact) View.GONE else View.VISIBLE
            windowManager.updateViewLayout(view, layoutParams)
        }

        btnCloseOverlay.setOnClickListener {
            stopSelf()
        }

        btnMarkDone.setOnClickListener {
            if (isRestrictedLock || isOnBreak) return@setOnClickListener
            onFollowCompleted()
        }

        btnPauseResume.setOnClickListener {
            if (isRestrictedLock) {
                // Lever manuellement le blocage de restriction et reprendre
                isRestrictedLock = false
                isManualPause = false
                isOnBreak = false
                updateUiState()
                return@setOnClickListener
            }

            if (isOnBreak) {
                isOnBreak = false
                remainingBreakSec = 0
                isManualPause = false
                updateUiState()
                return@setOnClickListener
            }

            isManualPause = !isManualPause
            updateUiState()
        }

        btnReportRestriction.setOnClickListener {
            onRestrictionReported()
        }

        expandedPanel?.visibility = if (isCompact) View.GONE else View.VISIBLE
        btnToggleSize.text = if (isCompact) "+" else "−"

        windowManager.addView(view, layoutParams)
    }

    private fun onFollowCompleted() {
        sessionTasksCount += 1
        tvCounter?.text = sessionTasksCount.toString()

        lifecycleScope.launch {
            val db = PrDatabase.getInstance(this@FloatingAssistantService)
            db.dao().insertTask(
                TaskItemEntity(
                    targetLabel = "Follow #$sessionTasksCount",
                    status = "DONE",
                    completedAt = System.currentTimeMillis()
                )
            )
            persistSessionProgress()
        }

        // Pause automatique par palier si activée
        if (settings.autoBreakEnabled &&
            settings.autoBreakEveryTasks > 0 &&
            sessionTasksCount % settings.autoBreakEveryTasks == 0
        ) {
            isOnBreak = true
            isWaitingDelay = false
            remainingBreakSec = settings.breakDurationSec.coerceAtLeast(10)
            updateUiState()
            return
        }

        // Délai aléatoire configurable si activé
        if (settings.randomDelayEnabled) {
            val minD = settings.minDelaySec.coerceAtLeast(1)
            val maxD = settings.maxDelaySec.coerceAtLeast(minD)
            remainingDelaySec = Random.nextInt(minD, maxD + 1)
            isWaitingDelay = true
        } else {
            remainingDelaySec = 0
            isWaitingDelay = false
        }

        updateUiState()
    }

    private fun onRestrictionReported() {
        isRestrictedLock = true
        isOnBreak = false
        isWaitingDelay = false
        isManualPause = true

        lifecycleScope.launch {
            val db = PrDatabase.getInstance(this@FloatingAssistantService)
            db.dao().insertRestriction(
                RestrictionLogEntity(
                    occurredAt = System.currentTimeMillis(),
                    durationSeconds = 0L,
                    notes = "Restriction signalée après $sessionTasksCount actions de session"
                )
            )
            persistSessionProgress()
        }

        updateUiState()
    }

    private fun onTickSecond() {
        when {
            isRestrictedLock -> {
                restSeconds += 1
                return
            }
            isOnBreak -> {
                restSeconds += 1
                remainingBreakSec -= 1
                if (remainingBreakSec <= 0) {
                    remainingBreakSec = 0
                    isOnBreak = false
                    if (settings.notificationsEnabled) {
                        notifyBreakEnded()
                    }
                }
                updateUiState()
            }
            isManualPause -> {
                restSeconds += 1
            }
            isWaitingDelay -> {
                workSeconds += 1
                remainingDelaySec -= 1
                if (remainingDelaySec <= 0) {
                    remainingDelaySec = 0
                    isWaitingDelay = false
                }
                updateUiState()
            }
            else -> {
                workSeconds += 1
            }
        }
    }

    private fun updateUiState() {
        tvCounter?.text = sessionTasksCount.toString()

        when {
            isRestrictedLock -> {
                statusDot?.setBackgroundColor(getColor(R.color.alert_red))
                tvTimer?.text = "BLOQUÉ"
                tvStateLabel?.text = "Restriction signalée • Session suspendue"
                btnPauseResume?.text = "Débloquer"
            }
            isOnBreak -> {
                statusDot?.setBackgroundColor(getColor(R.color.alert_red))
                tvTimer?.text = formatDuration(remainingBreakSec)
                tvStateLabel?.text = "Pause en cours • Repos"
                btnPauseResume?.text = "Fin pause"
            }
            isManualPause -> {
                statusDot?.setBackgroundColor(getColor(R.color.alert_red))
                tvTimer?.text = "PAUSE"
                tvStateLabel?.text = "Session en pause manuelle"
                btnPauseResume?.text = "Reprendre"
            }
            isWaitingDelay -> {
                statusDot?.setBackgroundColor(getColor(R.color.text_white))
                tvTimer?.text = "${remainingDelaySec}s"
                tvStateLabel?.text = "Délai variable actif • Patientez"
                btnPauseResume?.text = "Pause"
            }
            else -> {
                statusDot?.setBackgroundColor(getColor(R.color.accent_green))
                tvTimer?.text = "PRÊT"
                tvStateLabel?.text = "Prêt pour le prochain Follow"
                btnPauseResume?.text = "Pause"
            }
        }
    }

    private fun formatDuration(totalSec: Int): String {
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun persistSessionProgress() {
        if (sessionId == 0L) return
        lifecycleScope.launch {
            val db = PrDatabase.getInstance(this@FloatingAssistantService)
            db.dao().updateSession(
                SessionEntity(
                    id = sessionId,
                    endedAt = null,
                    tasksCompleted = sessionTasksCount,
                    workSeconds = workSeconds,
                    restSeconds = restSeconds
                )
            )
        }
    }

    private fun buildForegroundNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, PrAssistantApp.CHANNEL_SERVICE_ID)
            .setContentTitle("PR ASSISTANT")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun notifyBreakEnded() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, PrAssistantApp.CHANNEL_BREAK_ID)
            .setContentTitle("Pause terminée")
            .setContentText("La période de repos est finie. Vous pouvez reprendre vos tâches.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(BREAK_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickerRunnable)
        persistSessionProgress()
        overlayView?.let {
            runCatching { windowManager.removeView(it) }
        }
        overlayView = null
        super.onDestroy()
    }

    companion object {
        const val SERVICE_NOTIFICATION_ID = 1001
        const val BREAK_NOTIFICATION_ID = 1002
    }
}
