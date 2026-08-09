package com.example.mehrsms

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar
import kotlin.concurrent.thread

data class SmsMessage(val id: Long, val sender: String, val body: String, val date: String, val timestamp: Long)

class ChatActivity : AppCompatActivity() {

    private var address: String = ""
    private var contactName: String = ""
    private val messagesList = ArrayList<SmsMessage>()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter
    private lateinit var inputMessage: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        address = intent.getStringExtra("ADDRESS") ?: ""
        contactName = intent.getStringExtra("NAME") ?: address

        window.statusBarColor = Color.parseColor("#F5F5F3")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F3"))
        }

        // Header
        val header = RelativeLayout(this).apply {
            setPadding(32, 32, 32, 24)
            setBackgroundColor(Color.WHITE)
        }

        val backBtn = TextView(this).apply {
            text = "➔"
            textSize = 22f
            setOnClickListener { finish() }
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            layoutParams = params
        }

        val nameTitle = TextView(this).apply {
            text = contactName
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1C1C1E"))
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.CENTER_IN_PARENT)
            }
            layoutParams = params
        }

        header.addView(backBtn)
        header.addView(nameTitle)
        root.addView(header)

        // RecyclerView
        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            setPadding(24, 16, 24, 16)
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        adapter = ChatAdapter(
            messagesList,
            onLongClick = { msg -> showOptionsDialog(msg) }
        )
        recyclerView.adapter = adapter
        root.addView(recyclerView)

        // Input Container
        val inputContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 16, 24, 24)
            setBackgroundColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }

        val sendBtn = Button(this).apply {
            text = "ارسال"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor("#007AFF"))
            }
            setOnClickListener {
                val text = inputMessage.text.toString().trim()
                if (text.isNotEmpty()) {
                    sendSms(text)
                }
            }
        }

        inputMessage = EditText(this).apply {
            hint = "پیام خود را بنویسید..."
            textSize = 14f
            setPadding(32, 20, 32, 20)
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor("#F2F2F7"))
            }
            gravity = Gravity.RIGHT
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(16, 0, 0, 0)
            }
        }

        inputContainer.addView(sendBtn)
        inputContainer.addView(inputMessage)
        root.addView(inputContainer)

        setContentView(root)
        loadChatHistory()
    }

    private fun loadChatHistory() {
        thread {
            messagesList.clear()
            try {
                val uri = Uri.parse("content://sms")
                val cursor = contentResolver.query(
                    uri,
                    null,
                    "address=?",
                    arrayOf(address),
                    "date ASC"
                )

                cursor?.use {
                    val idIndex = it.getColumnIndex("_id")
                    val bodyIndex = it.getColumnIndex("body")
                    val dateIndex = it.getColumnIndex("date")
                    val typeIndex = it.getColumnIndex("type")

                    while (it.moveToNext()) {
                        val id = if (idIndex != -1) it.getLong(idIndex) else -1L
                        val body = if (bodyIndex != -1 && !it.isNull(bodyIndex)) it.getString(bodyIndex) ?: "" else ""
                        val dateMillis = if (dateIndex != -1 && !it.isNull(dateIndex)) it.getLong(dateIndex) else System.currentTimeMillis()
                        val type = if (typeIndex != -1) it.getInt(typeIndex) else 1

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

                        val sender = if (type == 2) "ME" else address
                        messagesList.add(SmsMessage(id, sender, body, solarDate, dateMillis))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            runOnUiThread {
                adapter.notifyDataSetChanged()
                if (messagesList.isNotEmpty()) {
                    recyclerView.scrollToPosition(messagesList.size - 1)
                }
            }
        }
    }

    private fun sendSms(text: String) {
        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            smsManager.sendTextMessage(address, null, text, null, null)
            inputMessage.setText("")

            // ثبت پیامک ارسال‌شده در دیتابیس گوشی
            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put("address", address)
                put("body", text)
                put("date", now)
                put("type", 2)
            }
            contentResolver.insert(Uri.parse("content://sms/sent"), values)

            loadChatHistory()
            Toast.makeText(this, "پیام ارسال شد", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در ارسال پیام: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // منوی کپی و حذف پیام
    private fun showOptionsDialog(message: SmsMessage) {
        val options = arrayOf("کپی متن", "حذف پیام")
        AlertDialog.Builder(this)
            .setTitle("عملیات")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> copyToClipboard(message.body)
                    1 -> deleteSms(message)
                }
            }
            .show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SMS", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "متن کپی شد", Toast.LENGTH_SHORT).show()
    }

    private fun deleteSms(message: SmsMessage) {
        try {
            val uri = Uri.parse("content://sms/${message.id}")
            val rowsDeleted = contentResolver.delete(uri, null, null)
            if (rowsDeleted > 0) {
                messagesList.remove(message)
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "پیام حذف شد", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "برای حذف پیام باید برنامه پیش‌فرض SMS باشید", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در حذف: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

class ChatAdapter(
    private val items: List<SmsMessage>,
    private val onLongClick: (SmsMessage) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    class ViewHolder(val container: LinearLayout, val card: FrameLayout, val bodyText: TextView, val dateText: TextView) :
        RecyclerView.ViewHolder(container)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 8, 0, 8)
        }

        val card = FrameLayout(context)

        val innerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 20, 28, 20)
        }

        val bodyText = TextView(context).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#1C1C1E"))
        }

        val dateText = TextView(context).apply {
            textSize = 10f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, 8, 0, 0)
        }

        innerLayout.addView(bodyText)
        innerLayout.addView(dateText)
        card.addView(innerLayout)
        container.addView(card)

        return ViewHolder(container, card, bodyText, dateText)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = items[position]
        val isMe = msg.sender == "ME"

        holder.bodyText.text = msg.body
        holder.dateText.text = msg.date

        val bg = GradientDrawable().apply { cornerRadius = 24f }
        if (isMe) {
            holder.container.gravity = Gravity.LEFT
            bg.setColor(Color.parseColor("#DCF8C6"))
        } else {
            holder.container.gravity = Gravity.RIGHT
            bg.setColor(Color.WHITE)
        }
        holder.card.background = bg

        holder.card.setOnLongClickListener {
            onLongClick(msg)
            true
        }
    }

    override fun getItemCount(): Int = items.size
}
