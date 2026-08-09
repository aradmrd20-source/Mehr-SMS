package com.example.mehrsms

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

data class SmsMessage(val sender: String, val body: String, val date: String, val timestamp: Long)
data class SmsThread(val sender: String, val messages: MutableList<SmsMessage>, var category: String)

class MainActivity : AppCompatActivity() {

    private val SMS_PERMISSION_CODE = 101
    private lateinit var mainLayout: LinearLayout
    private lateinit var chipsLayout: LinearLayout
    private val threadsMap = LinkedHashMap<String, SmsThread>()
    private var selectedCategory = "ALL"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F8FAFC"))
        }

        // نوار بالایی برنامه
        val header = TextView(this).apply {
            text = "MehrSMS"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(48, 48, 48, 24)
            gravity = Gravity.RIGHT
        }
        root.addView(header)

        // اسکرول چیپ‌های دسته‌بندی بالا
        val chipsScrollView = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(32, 8, 32, 24)
        }
        chipsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        chipsScrollView.addView(chipsLayout)
        root.addView(chipsScrollView)

        // اسکرول کارت‌ها
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
            loadAndGroupSms()
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
            loadAndGroupSms()
        }
    }

    private fun loadAndGroupSms() {
        threadsMap.clear()
        val uri = Uri.parse("content://sms/inbox")
        val cursor = contentResolver.query(uri, null, null, null, "date DESC")

        cursor?.use {
            val bodyIndex = it.getColumnIndex("body")
            val addressIndex = it.getColumnIndex("address")
            val dateIndex = it.getColumnIndex("date")

            while (it.moveToNext()) {
                val body = it.getString(bodyIndex) ?: ""
                val address = it.getString(addressIndex) ?: "ناشناس"
                val dateMillis = it.getLong(dateIndex)

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

        renderCategoryChips()
        renderThreads()
    }

    private fun classifyAccurate(message: String, sender: String): String {
        val text = message.lowercase()
        if (text.contains("تخفیف") || text.contains("لغو") || text.contains("خرید") || text.contains("پیشنهاد") || text.contains("لینک")) return "PROMO"
        if ((text.contains("کد ورود") || text.contains("رمز ورود") || text.contains("کد تایید") || text.contains("otp")) && message.length < 150) return "OTP"
        if (text.contains("برداشت") || text.contains("واریز") || text.contains("موجودی") || sender.contains("Bank", ignoreCase = true)) return "BANK"
        return "PERSONAL"
    }

    // رندر دکمه‌های دسته‌بندی بالای صفحه
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
                text = "$label  $count"
                textSize = 13f
                setPadding(36, 20, 36, 20)

                val isSelected = selectedCategory == key
                val shape = GradientDrawable().apply {
                    cornerRadius = 50f
                    if (isSelected) {
                        setColor(Color.parseColor("#1E293B"))
                    } else {
                        setColor(Color.parseColor("#E2E8F0"))
                    }
                }
                background = shape
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#475569"))

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

    // رندر کارت‌های گفتگو
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
                radius = 32f
                cardElevation = 2f
                setCardBackgroundColor(Color.WHITE)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 20) }
                layoutParams = params

                setOnClickListener { showChatDialog(thread) }
            }

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(36, 32, 36, 32)
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val dateText = TextView(this@MainActivity).apply {
                text = lastMessage.date
                textSize = 11f
                setTextColor(Color.parseColor("#94A3B8"))
            }

            val senderText = TextView(this@MainActivity).apply {
                text = thread.sender
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#0F172A"))
                gravity = Gravity.RIGHT
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            topRow.addView(dateText)
            topRow.addView(senderText)

            val previewText = TextView(this).apply {
                text = lastMessage.body
                textSize = 13f
                maxLines = 2
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, 12, 0, 0)
                gravity = Gravity.RIGHT
            }

            container.addView(topRow)
            container.addView(previewText)
            card.addView(container)

            mainLayout.addView(card)
        }
    }

    // پنجره نمایش همه پیامک‌های یک مخاطب با کلیک روی کارت
    private fun showChatDialog(thread: SmsThread) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F8FAFC"))
            setPadding(32, 32, 32, 32)
        }

        val header = TextView(this).apply {
            text = "گفتگو با ${thread.sender}"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            gravity = Gravity.RIGHT
            setPadding(0, 0, 0, 24)
        }
        dialogLayout.addView(header)

        val scrollView = ScrollView(this)
        val messagesLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        thread.messages.reversed().forEach { msg ->
            val msgCard = CardView(this).apply {
                radius = 20f
                setCardBackgroundColor(Color.WHITE)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 16) }
                layoutParams = params
            }

            val msgContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 20, 24, 20)
            }

            val body = TextView(this).apply {
                text = msg.body
                textSize = 13f
                setTextColor(Color.parseColor("#1E293B"))
                gravity = Gravity.RIGHT
            }

            val date = TextView(this).apply {
                text = msg.date
                textSize = 10f
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.LEFT
                setPadding(0, 8, 0, 0)
            }

            msgContainer.addView(body)
            msgContainer.addView(date)
            msgCard.addView(msgContainer)
            messagesLayout.addView(msgCard)
        }

        scrollView.addView(messagesLayout)
        dialogLayout.addView(scrollView)

        dialog.setContentView(dialogLayout)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            (resources.displayMetrics.heightPixels * 0.75).toInt()
        )
        dialog.show()
    }
}
