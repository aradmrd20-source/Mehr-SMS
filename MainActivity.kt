package com.example.mehrsms

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Telephony
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar
import kotlin.concurrent.thread

data class SmsMessage(val sender: String, val body: String, val date: String, val timestamp: Long)
data class SmsThread(val sender: String, var contactName: String, val messages: MutableList<SmsMessage>, var category: String)

class MainActivity : AppCompatActivity() {

    private val PERMISSION_CODE = 101
    private lateinit var recyclerView: RecyclerView
    private lateinit var chipsLayout: LinearLayout
    private lateinit var progressBar: ProgressBar
    private val threadsList = ArrayList<SmsThread>()
    private val allThreadsMap = LinkedHashMap<String, SmsThread>()
    private val contactsMap = HashMap<String, String>()
    private var selectedCategory = "ALL"
    private lateinit var adapter: SmsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // تنظیم رنگ استاتوس‌بار
        window.statusBarColor = Color.parseColor("#F5F5F3")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F3"))
        }

        // Header Top Bar
        val headerBar = RelativeLayout(this).apply {
            setPadding(48, 48, 48, 16)
        }

        val settingsBtn = TextView(this).apply {
            text = "⚙️"
            textSize = 22f
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            layoutParams = params
        }

        val titleText = TextView(this).apply {
            text = "MehrSMS"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1C1C1E"))
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            layoutParams = params
        }

        headerBar.addView(settingsBtn)
        headerBar.addView(titleText)
        root.addView(headerBar)

        // Horizontal Chips
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
        adapter = SmsAdapter(threadsList) { thread ->
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("ADDRESS", thread.sender)
                putExtra("NAME", thread.contactName)
            }
            startActivity(intent)
        }
        recyclerView.adapter = adapter
        root.addView(recyclerView)

        setContentView(root)
        checkPermissions()
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_CODE)
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
                        startActivityForResult(intent, 202)
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
        loadAllData()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        checkDefaultSmsRole()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        loadAllData()
    }

    private fun loadAllData() {
        progressBar.visibility = View.VISIBLE
        thread {
            loadContacts()
            loadSmsMessages()
            runOnUiThread {
                progressBar.visibility = View.GONE
                renderCategoryChips()
                filterThreads()
            }
        }
    }

    private fun loadContacts() {
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )
            val cursor = contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                while (it.moveToNext()) {
                    val rawNumber = if (numberIdx != -1) it.getString(numberIdx) else ""
                    val name = if (nameIdx != -1) it.getString(nameIdx) else ""
                    val cleanNumber = normalizePhoneNumber(rawNumber)
                    if (cleanNumber.isNotEmpty()) {
                        contactsMap[cleanNumber] = name
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun normalizePhoneNumber(phone: String): String {
        var clean = phone.replace("\\s+".toRegex(), "").replace("-", "").replace("(", "").replace(")", "")
        if (clean.startsWith("+98")) clean = "0" + clean.substring(3)
        if (clean.startsWith("0098")) clean = "0" + clean.substring(4)
        return clean
    }

    private fun loadSmsMessages() {
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
                    val rawAddress = if (addressIndex != -1 && !it.isNull(addressIndex)) it.getString(addressIndex) ?: "ناشناس" else "ناشناس"
                    val dateMillis = if (dateIndex != -1 && !it.isNull(dateIndex)) it.getLong(dateIndex) else System.currentTimeMillis()

                    val cleanAddress = normalizePhoneNumber(rawAddress)
                    val contactName = contactsMap[cleanAddress] ?: rawAddress

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

                    val message = SmsMessage(rawAddress, body, solarDate, dateMillis)

                    if (!allThreadsMap.containsKey(rawAddress)) {
                        val category = classifyAccurate(body, rawAddress)
                        allThreadsMap[rawAddress] = SmsThread(rawAddress, contactName, mutableListOf(message), category)
                    } else {
                        allThreadsMap[rawAddress]?.messages?.add(message)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
}

class SmsAdapter(
    private val items: List<SmsThread>,
    private val onItemClick: (SmsThread) -> Unit
) : RecyclerView.Adapter<SmsAdapter.ViewHolder>() {

    class ViewHolder(val card: FrameLayout, val senderText: TextView, val dateText: TextView, val previewText: TextView) :
        RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val card = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = 36f
                setColor(Color.WHITE)
            }
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

        holder.senderText.text = thread.contactName
        holder.dateText.text = lastMessage.date
        holder.previewText.text = lastMessage.body

        holder.card.setOnClickListener { onItemClick(thread) }
    }

    override fun getItemCount(): Int = items.size
}
