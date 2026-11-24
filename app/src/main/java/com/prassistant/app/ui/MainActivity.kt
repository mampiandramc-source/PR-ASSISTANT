package com.prassistant.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.prassistant.app.data.AppSettingsEntity
import com.prassistant.app.data.PrDatabase
import com.prassistant.app.databinding.ActivityMainBinding
import com.prassistant.app.service.FloatingAssistantService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        checkOverlayPermissionAndStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        observeDatabase()
        setupListeners()
    }

    private fun observeDatabase() {
        val db = PrDatabase.getInstance(this)

        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        lifecycleScope.launch {
            db.dao().observeDoneTodayCount(startOfDay).collectLatest { count ->
                binding.tvStatsToday.text = "Tâches aujourd'hui : $count"
            }
        }

        lifecycleScope.launch {
            db.dao().observePendingTasksCount().collectLatest { count ->
                binding.tvStatsPending.text = "Tâches en attente : $count"
            }
        }

        lifecycleScope.launch {
            db.dao().observeRestrictions().collectLatest { list ->
                binding.tvStatsRestrictions.text = "Restrictions signalées : ${list.size}"
            }
        }

        lifecycleScope.launch {
            db.dao().observeSettings().collectLatest { settings ->
                val s = settings ?: AppSettingsEntity()
                if (!binding.etMinDelay.hasFocus()) {
                    binding.etMinDelay.setText(s.minDelaySec.toString())
                }
                if (!binding.etMaxDelay.hasFocus()) {
                    binding.etMaxDelay.setText(s.maxDelaySec.toString())
                }
                if (!binding.etBreakEvery.hasFocus()) {
                    binding.etBreakEvery.setText(s.autoBreakEveryTasks.toString())
                }
                if (!binding.etBreakDuration.hasFocus()) {
                    binding.etBreakDuration.setText(s.breakDurationSec.toString())
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnSaveSettings.setOnClickListener {
            val minDelay = binding.etMinDelay.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 20
            val maxDelay = binding.etMaxDelay.text.toString().toIntOrNull()?.coerceAtLeast(minDelay) ?: 60
            val breakEvery = binding.etBreakEvery.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 15
            val breakDuration = binding.etBreakDuration.text.toString().toIntOrNull()?.coerceAtLeast(10) ?: 300

            lifecycleScope.launch {
                val db = PrDatabase.getInstance(this@MainActivity)
                val current = db.dao().getSettingsOnce() ?: AppSettingsEntity()
                db.dao().saveSettings(
                    current.copy(
                        minDelaySec = minDelay,
                        maxDelaySec = maxDelay,
                        autoBreakEveryTasks = breakEvery,
                        breakDurationSec = breakDuration
                    )
                )
                Toast.makeText(this@MainActivity, "Paramètres enregistrés", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnStartOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@setOnClickListener
                }
            }
            checkOverlayPermissionAndStart()
        }

        binding.btnStopOverlay.setOnClickListener {
            stopService(Intent(this, FloatingAssistantService::class.java))
            Toast.makeText(this, "Fenêtre flottante arrêtée", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkOverlayPermissionAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Activez 'Afficher par-dessus d'autres applications' pour PR ASSISTANT",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        val serviceIntent = Intent(this, FloatingAssistantService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        Toast.makeText(this, "PR ASSISTANT démarré", Toast.LENGTH_SHORT).show()
    }
}
