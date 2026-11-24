package com.prassistant.app

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import android.view.View
import kotlin.random.Random

class FloatingPanelService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var panel: LinearLayout
    private lateinit var stats: TextView
    private lateinit var timer: TextView

    private var count = 0
    private var seconds = 0
    private var remaining = 0
    private var running = false

    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (!::panel.isInitialized) createPanel()
        return START_NOT_STICKY
    }

    private fun createPanel() {

        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(15, 12, 15, 12)
            setBackgroundColor(Color.rgb(25, 30, 38))
        }

        stats = TextView(this).apply {
            text = "FOLLOW : 0"
            textSize = 18f
            setTextColor(Color.WHITE)
        }

        timer = TextView(this).apply {
            text = "SESSION : 00:00"
            textSize = 14f
            setTextColor(Color.GREEN)
        }

        val add = Button(this).apply {
            text = "+1 TÂCHE"
            setOnClickListener {
                count++
                update()
            }
        }

        val start = Button(this).apply {
            text = "DÉMARRER"
            setOnClickListener {
                startTimer()
            }
        }

        val pause = Button(this).apply {
            text = "PAUSE"
            setOnClickListener {
                running = false
            }
        }

        val close = Button(this).apply {
            text = "FERMER"
            setOnClickListener {
                stopSelf()
            }
        }

        panel.addView(stats)
        panel.addView(timer)
        panel.addView(add)
        panel.addView(start)
        panel.addView(pause)
        panel.addView(close)

        val params = LayoutParams(
            300,
            LayoutParams.WRAP_CONTENT,
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.END
        params.x = 15
        params.y = 120

        wm.addView(panel, params)
    }

    private fun startTimer() {
        if (running) return

        running = true

        val task = object : Runnable {
            override fun run() {
                if (!running) return

                seconds++

                if (remaining > 0) remaining--
                else remaining = Random.nextInt(25, 56)

                update()
                handler.postDelayed(this, 1000)
            }
        }

        handler.post(task)
    }

    private fun update() {
        stats.text = "FOLLOW : $count"

        val minutes = seconds / 60
        val sec = seconds % 60

        timer.text = "SESSION : %02d:%02d".format(minutes, sec)
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)

        if (::panel.isInitialized && panel.parent != null) {
            wm.removeView(panel)
        }

        super.onDestroy()
    }
}
