package com.example.mehrsms

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private val SMS_PERMISSION_CODE = 101
    private lateinit var mainLayout: LinearLayout
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ریشه اصلی صفحه
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F7"))
        }

        // نوار بالای برنامه (Header)
        val header = TextView(this).apply {
            text = "MehrSMS | مدیریت پیامک‌ها"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(40, 40, 40, 40)
            gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
        }
        root.addView(header)

        // اسکرول برای کارت‌ها
        scrollView = ScrollView(this)
        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        scrollView.addView(mainLayout)
        root.addView(scrollView)

        setContentView(root)

        if (checkPermissions()) {
            loadSmsMessages()
        } else {
            requestPermissions()
        }
    }

    private fun checkPermissions(): Boolean {
        val readSms = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
        val receiveSms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
        return readSms == PackageManager.PERMISSION_GRANTED && receiveSms == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS),
            SMS_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadSmsMessages()
        } else {
            Toast.makeText(this, "مجوز دسترسی داده نشد!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSmsMessages() {
        mainLayout.removeAllViews()
        val uri = Uri.parse("content://sms/inbox")
        val cursor = contentResolver.query(uri, null, null, null, "date DESC")

        cursor?.use {
            val bodyIndex = it.getColumnIndex("body")
            val addressIndex = it.getColumnIndex("address")
            val dateIndex = it.getColumnIndex("date")

            var count = 0
            while (it.moveToNext() && count < 40) {
                val body = it.getString(bodyIndex) ?: ""
                val address = it.getString(addressIndex) ?: "ناشناس"
                val dateMillis = it.getLong(dateIndex)

                val calendar = Calendar.getInstance().apply { timeInMillis = dateMillis }
                val solarDate = SmsEngine.toSolarHijri(
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.DAY_OF_MONTH)
                )

                val category = classifyAccurate(body, address)
                val card = createSmsCard(address, body, solarDate, category)
                mainLayout.addView(card)
                count++
            }
        }
    }

    // الگوریتم دسته‌بندی هوشمند دقیق‌تر (جداسازی اسپم و تبلیغات)
    private fun classifyAccurate(message: String, sender: String): String {
        val text = message.lowercase()
        
        // ۱. بررسی تبلیغاتی بودن (پیش‌فرض اول)
        if (text.contains("تخفیف") || text.contains("لغو") || text.contains("خرید") || 
            text.contains("پیشنهاد") || text.contains("کد رهگیری") || text.contains("لینک") || 
            text.contains("شارژ") || text.contains("تومان") && !text.contains("برداشت") && !text.contains("واریز")) {
            return "PROMO"
        }

        // ۲. کدهای تایید واقعی (کوتاه و حاوی کلمات مشخص)
        if ((text.contains("کد ورود") || text.contains("رمز ورود") || text.contains("کد تایید") || text.contains("otp")) && message.length < 150) {
            return "OTP"
        }

        // ۳. پیامک‌های بانکی
        if (text.contains("برداشت") || text.contains("واریز") || text.contains("موجودی") || sender.contains("Bank", ignoreCase = true)) {
            return "BANK"
        }

        return "PERSONAL"
    }

    // ساخت کارت شکیل برای هر پیامک
    private fun createSmsCard(sender: String, body: String, date: String, category: String): View {
        val card = CardView(this).apply {
            radius = 24f
            cardElevation = 6f
            setCardBackgroundColor(Color.WHITE)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
            layoutParams = params
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 28)
        }

        // نوار بالای کارت (فرستنده و نشان دسته)
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val categoryTag = TextView(this).apply {
            when (category) {
                "BANK" -> { text = " 🏦 بانکی "; setBackgroundColor(Color.parseColor("#E0F2FE")); setTextColor(Color.parseColor("#0369A1")) }
                "OTP" -> { text = " 🔐 کد تأیید "; setBackgroundColor(Color.parseColor("#DCFCE7")); setTextColor(Color.parseColor("#15803D")) }
                "PROMO" -> { text = " 📢 تبلیغاتی "; setBackgroundColor(Color.parseColor("#FEF3C7")); setTextColor(Color.parseColor("#B45309")) }
                else -> { text = " 💬 شخصی "; setBackgroundColor(Color.parseColor("#F3E8FF")); setTextColor(Color.parseColor("#6B21A8")) }
            }
            textSize = 12f
            setPadding(16, 8, 16, 8)
        }

        val senderText = TextView(this).apply {
            text = sender
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            gravity = Gravity.RIGHT
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        topRow.addView(categoryTag)
        topRow.addView(senderText)

        // متن پیامک
        val bodyText = TextView(this).apply {
            text = body
            textSize = 13.5f
            setTextColor(Color.parseColor("#334155"))
            setPadding(0, 16, 0, 16)
            gravity = Gravity.RIGHT
            setLineSpacing(8f, 1f)
        }

        // تاریخ
        val dateText = TextView(this).apply {
            text = "📅 $date"
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.LEFT
        }

        container.addView(topRow)
        container.addView(bodyText)
        container.addView(dateText)

        card.addView(container)
        return card
    }
}
