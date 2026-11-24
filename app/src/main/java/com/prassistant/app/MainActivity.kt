package com.prassistant.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 28)
            setBackgroundColor(Color.rgb(16, 19, 24))
        }

        val title = TextView(this).apply {
            text = "PR ASSISTANT"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val desc = TextView(this).apply {
            text = "Your personal task assistant"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            setPadding(0, 18, 0, 24)
        }

        val button = Button(this).apply {
            text = "ACTIVER LE MINI-ÉCRAN"
        }

        button.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                startService(
                    Intent(this, FloatingPanelService::class.java)
                )
            }
        }

        root.addView(title)
        root.addView(desc)
        root.addView(button)

        setContentView(root)
    }
}
