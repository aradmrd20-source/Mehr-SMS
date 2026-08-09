package com.example.mehrsms

import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsetsController
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // تنظیم رنگ استاتوس‌بار
        window.statusBarColor = Color.parseColor("#F5F5F3")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else @Suppress("DEPRECATION") {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F3"))
            setPadding(32, 32, 32, 32)
        }

        // Header
        val header = RelativeLayout(this).apply {
            setPadding(0, 16, 0, 32)
        }

        val backBtn = TextView(this).apply {
            text = "➔"
            textSize = 22f
            setOnClickListener { finish() }
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply { addRule(RelativeLayout.ALIGN_PARENT_RIGHT) }
            layoutParams = params
        }

        val title = TextView(this).apply {
            text = "تنظیمات"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1C1C1E"))
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply { addRule(RelativeLayout.CENTER_IN_PARENT) }
            layoutParams = params
        }

        header.addView(backBtn)
        header.addView(title)
        root.addView(header)

        // Items
        val switchDark = Switch(this).apply {
            text = "حالت شب (تیره)"
            textSize = 15f
            gravity = Gravity.RIGHT
            setPadding(0, 24, 0, 24)
        }

        val switchNotify = Switch(this).apply {
            text = "اعلان پیامک جدید"
            isChecked = true
            textSize = 15f
            gravity = Gravity.RIGHT
            setPadding(0, 24, 0, 24)
        }

        val appVersion = TextView(this).apply {
            text = "نسخه برنامه: 1.0"
            textSize = 13f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 60, 0, 0)
        }

        root.addView(switchDark)
        root.addView(switchNotify)
        root.addView(appVersion)

        setContentView(root)
    }
}
