package com.example.mehrsms

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Calendar
import kotlin.concurrent.thread

data class SmsMessage(val sender: String, val body: String, val date: String, val timestamp: Long)
data class SmsThread(val sender: String, val messages: MutableList<SmsMessage>, var category: String)

class MainActivity : AppCompatActivity() {

    private val SMS_PERMISSION_CODE = 101
    private lateinit var recyclerView: RecyclerView
    private lateinit var chipsLayout: LinearLayout
    private lateinit var progressBar: ProgressBar
    private val threadsList = ArrayList<SmsThread>()
    private val allThreadsMap = LinkedHashMap<String, SmsThread>()
    private var selectedCategory = "ALL"
    private lateinit var adapter: SmsAdapter

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        loadAllSmsAsync()
    }

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

        // Loading Progress
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

        // RecyclerView
        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            setPadding(32, 8, 32, 32)
            clipToPadding = false
        }
        adapter = SmsAdapter(threadsList) { thread -> showBottomSheetChat(thread) }
        recyclerView.adapter = adapter
        root.addView(recyclerView)

        setContentView(root)

        // بررسی و درخواست مجوزها
        requestDefaultSmsAppAndPermissions()
    }

    private fun requestDefaultSmsAppAndPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), SMS_PERMISSION_CODE)
        } else {
            checkDefaultSmsRole()
        }
    }

    private fun checkDefaultSmsRole() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                    if (!roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                        roleLauncher.launch(intent)
                        return
                    }
                }
            } else {
                val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
                if (defaultSmsPackage != packageName) {
                    val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                    intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                    startActivity(intent)
                    return
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        loadAllSmsAsync()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        checkDefaultSmsRole()
    }

    private fun loadAllSmsAsync() {
        progressBar.visibility = View.VISIBLE

        thread {
            try {
                allThreadsMap.clear()
                val uri = Uri.parse("content://sms/inbox")
                val cursor = contentResolver.query(uri, null, null, null, "date DESC")

                cursor?.use {
                    val bodyIndex = it.getColumnIndex("body")
                    val addressIndex = it.getColumnIndex("address")
                    val dateIndex = it.getColumnIndex("date")

                    while (it.moveToNext()) {
                        val body = if (bodyIndex != -1 && !it.isNull(bodyIndex)) it.getString(bodyIndex) ?: "" else ""
                        val address = if (addressIndex != -1 && !it.isNull(addressIndex)) it.getString(addressIndex) ?: "ناشناس" else "ناشناس"
                        val dateMillis = if (dateIndex != -1 && !it.isNull(dateIndex)) it.getLong(dateIndex) else System.currentTimeMillis()

                        val calendar = Calendar.getInstance().apply { timeInMillis = dateMillis }
                        val solarDate = try {
                            SmsEngine.toSolarHijri(
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH) + 1,
                                calendar.get(Calendar.DAY_OF_MONTH)
                            )
                        } catch (e: Exception) {
                            "${calendar.get(Calendar.YEAR)}/${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.DAY_OF_MONTH)}"
                        }

                        val message = SmsMessage(address, body, solarDate, dateMillis)

                        if (!allThreadsMap.containsKey(address)) {
                            val category = classifyAccurate(body, address)
                            allThreadsMap[address] = SmsThread(address, mutableListOf(message), category)
                        } else {
                            allThreadsMap[address]?.messages?.add(message)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            runOnUiThread {
                progressBar.visibility = View.GONE
                renderCategoryChips()
                filterThreads()
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

        val counts = mutableMapOf("ALL" to allThreadsMap.size, "BANK" to 0, "OTP" to 0, "PROMO" to 0, "PERSONAL" to 0)
        allThreadsMap.values.forEach { counts[it.category] = (counts[it.category] ?: 0) + 1 }

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
                    setColor(if (isSelected) Color.parseColor("#EAEAEA") else Color.WHITE)
                }
                background = shape
                setTextColor(Color.parseColor("#1C1C1E"))

                setOnClickListener {
                    selectedCategory = key
                    renderCategoryChips()
                    filterThreads()
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

    private fun filterThreads() {
        threadsList.clear()
        if (selectedCategory == "ALL") {
            threadsList.addAll(allThreadsMap.values)
        } else {
            threadsList.addAll(allThreadsMap.values.filter { it.category == selectedCategory })
        }
        adapter.notifyDataSetChanged()
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

        val scrollView = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val messagesLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

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

class SmsAdapter(
    private val items: List<SmsThread>,
    private val onItemClick: (SmsThread) -> Unit
) : RecyclerView.Adapter<SmsAdapter.ViewHolder>() {

    class ViewHolder(val card: CardView, val senderText: TextView, val dateText: TextView, val previewText: TextView) :
        RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val card = CardView(context).apply {
            radius = 36f
            cardElevation = 0f
            setCardBackgroundColor(Color.WHITE)
            val params = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 20) }
            layoutParams = params
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 32, 36, 32)
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val dateText = TextView(context).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#8E8E93"))
        }

        val senderText = TextView(context).apply {
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1C1C1E"))
            gravity = Gravity.RIGHT
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        topRow.addView(dateText)
        topRow.addView(senderText)

        val previewText = TextView(context).apply {
            textSize = 13f
            maxLines = 2
            setTextColor(Color.parseColor("#636366"))
            setPadding(0, 12, 0, 0)
            gravity = Gravity.RIGHT
        }

        container.addView(topRow)
        container.addView(previewText)
        card.addView(container)

        return ViewHolder(card, senderText, dateText, previewText)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val thread = items[position]
        val lastMessage = thread.messages.firstOrNull() ?: return

        holder.senderText.text = thread.sender
        holder.dateText.text = lastMessage.date
        holder.previewText.text = lastMessage.body

        holder.card.setOnClickListener { onItemClick(thread) }
    }

    override fun getItemCount(): Int = items.size
}
