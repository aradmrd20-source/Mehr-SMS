package com.example.mehrsms

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Calendar
import kotlin.concurrent.thread

data class SmsMessage(val sender: String, val body: String, val date: String, val timestamp: Long)
data class SmsThread(val sender: String, val messages: MutableList<SmsMessage>, var category: String)

class MainActivity : AppCompatActivity() {

    private val SMS_PERMISSION_CODE = 101
    private lateinit var mainLayout: LinearLayout
    private lateinit var chipsLayout: LinearLayout
    private lateinit var progressBar: ProgressBar
    private val threadsMap = LinkedHashMap<String, SmsThread>()
    private var selectedCategory = "ALL"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F3"))
        }

        // Header
        val header = TextView(this).apply {
            text = "MehrSMS"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1C1C1E"))
            setPadding(48, 48, 48, 16)
            gravity = Gravity.RIGHT
        }
        root.addView(header)

        // Horizontal Category Chips
        val chipsScrollView = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(32, 8, 32, 24)
        }
        chipsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        chipsScrollView.addView(chipsLayout)
        root.addView(chipsScrollView)

        // Loading Indicator
        progressBar = ProgressBar(this).apply {
            visibility = View.VISIBLE
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 40, 0, 40)
            }
            layoutParams = params
        }
        root.addView(progressBar)

        // Scrollable List
        val mainScrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
        }
        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 32)
        }
        mainScrollView.addView(mainLayout)
        root.addView(mainScrollView)

        setContentView(root)

        if (checkPermissions()) {
            loadAllSmsAsync()
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
            loadAllSmsAsync()
        }
    }

    // خواندن تمام پیامک‌ها به صورت پس‌زمینه برای جلوگیری از کرش و کندی
    private fun loadAllSmsAsync() {
        progressBar.visibility = View.VISIBLE

        thread {
            threadsMap.clear()
            val uri = Uri.parse("content://sms/inbox")
            val cursor = contentResolver.query(uri, null, null, null, "date DESC")

            cursor?.use {
                val bodyIndex = it.getColumnIndex("body")
                val addressIndex = it.getColumnIndex("address")
                val dateIndex = it.getColumnIndex("date")

                // بدون هیچ محدودیت تعدادی - خواندن تمام پیامک‌ها
                while (it.moveToNext()) {
                    val body = if (bodyIndex != -1) it.getString(bodyIndex) ?: "" else ""
                    val address = if (addressIndex != -1) it.getString(addressIndex) ?: "ناشناس" else "ناشناس"
                    val dateMillis = if (dateIndex != -1) it.getLong(dateIndex) else System.currentTimeMillis()

                    val calendar = Calendar.getInstance().apply { timeInMillis = dateMillis }
                    val solarDate = SmsEngine.toSolarHijri(
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH) + 1,
                        calendar.get(Calendar.DAY_OF_MONTH)
                    )

                    val message = SmsMessage(address, body, solarDate, dateMillis)

                    if (!threadsMap.containsKey(address)) {
                        val category = classifyAccurate(body, address)
                        threadsMap[address] = SmsThread(address, mutableListOf(message), category)
                    } else {
                        threadsMap[address]?.messages?.add(message)
                    }
                }
            }

            // بازگشت به Thread اصلی جهت به‌روزرسانی UI
            runOnUiThread {
                progressBar.visibility = View.GONE
                renderCategoryChips()
                renderThreads()
            }
        }
    }

    private fun classifyAccurate(message: String, sender: String): String {
        val text = message.lowercase()
        if (text.contains("تخفیف") || text.contains("لغو") || text.contains("خرید") || text.contains("پیشنهاد") || text.contains("لینک")) return "PROMO"
        if ((text.contains("کد ورود") || text.contains("رمز ورود") || text.contains("کد تایید") || text.contains("otp")) && message.length < 150) return "OTP"
        if (text.contains("برداشت") || text.contains("واریز") || text.contains("موجودی") || sender.contains("Bank", ignoreCase = true)) return "BANK"
        return "PERSONAL"
    }

    private fun renderCategoryChips() {
        chipsLayout.removeAllViews()

        val counts = mutableMapOf("ALL" to threadsMap.size, "BANK" to 0, "OTP" to 0, "PROMO" to 0, "PERSONAL" to 0)
        threadsMap.values.forEach { counts[it.category] = (counts[it.category] ?: 0) + 1 }

        val categories = listOf(
            "ALL" to "همه",
            "BANK" to "بانکی",
            "OTP" to "کد تأیید",
            "PROMO" to "تبلیغاتی",
            "PERSONAL" to "شخصی"
        )

        categories.forEach { (key, label) ->
            val count = counts[key] ?: 0
            val chip = TextView(this).apply {
                text = "$label $count"
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setPadding(40, 20, 40, 20)

                val isSelected = selectedCategory == key
                val shape = GradientDrawable().apply {
                    cornerRadius = 40f
                    if (isSelected) {
                        setColor(Color.parseColor("#EAEAEA"))
                    } else {
                        setColor(Color.WHITE)
                    }
                }
                background = shape
                setTextColor(Color.parseColor("#1C1C1E"))

                setOnClickListener {
                    selectedCategory = key
                    renderCategoryChips()
                    renderThreads()
                }

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 16, 0) }
                layoutParams = params
            }
            chipsLayout.addView(chip)
        }
    }

    private fun renderThreads() {
        mainLayout.removeAllViews()

        val filteredThreads = if (selectedCategory == "ALL") {
            threadsMap.values.toList()
        } else {
            threadsMap.values.filter { it.category == selectedCategory }
        }

        filteredThreads.forEach { thread ->
            val lastMessage = thread.messages.first()

            val card = CardView(this).apply {
                radius = 36f
                cardElevation = 0f
                setCardBackgroundColor(Color.WHITE)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 20) }
                layoutParams = params

                setOnClickListener { showBottomSheetChat(thread) }
            }

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(36, 32, 36, 32)
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val dateText = TextView(this).apply {
                text = lastMessage.date
                textSize = 11f
                setTextColor(Color.parseColor("#8E8E93"))
            }

            val senderText = TextView(this).apply {
                text = thread.sender
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1C1C1E"))
                gravity = Gravity.RIGHT
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            topRow.addView(dateText)
            topRow.addView(senderText)

            val previewText = TextView(this).apply {
                text = lastMessage.body
                textSize = 13f
                maxLines = 2
                setTextColor(Color.parseColor("#636366"))
                setPadding(0, 12, 0, 0)
                gravity = Gravity.RIGHT
            }

            container.addView(topRow)
            container.addView(previewText)
            card.addView(container)

            mainLayout.addView(card)
        }
    }

    private fun showBottomSheetChat(thread: SmsThread) {
        val bottomSheetDialog = BottomSheetDialog(this)

        val sheetLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F3"))
            setPadding(40, 32, 40, 40)
        }

        val handleBar = View(this).apply {
            val params = LinearLayout.LayoutParams(96, 10).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, 24)
            }
            layoutParams = params
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#D1D1D6"))
            }
        }
        sheetLayout.addView(handleBar)

        val header = TextView(this).apply {
            text = thread.sender
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1C1C1E"))
            gravity = Gravity.RIGHT
            setPadding(0, 0, 0, 24)
        }
        sheetLayout.addView(header)

        val scrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
        }
        val messagesLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        thread.messages.reversed().forEach { msg ->
            val msgCard = CardView(this).apply {
                radius = 24f
                cardElevation = 0f
                setCardBackgroundColor(Color.WHITE)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 16) }
                layoutParams = params
            }

            val msgContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(28, 24, 28, 24)
            }

            val body = TextView(this).apply {
                text = msg.body
                textSize = 13.5f
                setTextColor(Color.parseColor("#1C1C1E"))
                gravity = Gravity.RIGHT
                setLineSpacing(6f, 1f)
            }

            val date = TextView(this).apply {
                text = msg.date
                textSize = 10.5f
                setTextColor(Color.parseColor("#8E8E93"))
                gravity = Gravity.LEFT
                setPadding(0, 8, 0, 0)
            }

            msgContainer.addView(body)
            msgContainer.addView(date)
            msgCard.addView(msgContainer)
            messagesLayout.addView(msgCard)
        }

        scrollView.addView(messagesLayout)
        sheetLayout.addView(scrollView)

        bottomSheetDialog.setContentView(sheetLayout)
        bottomSheetDialog.show()
    }
}
