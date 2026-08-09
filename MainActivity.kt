package com.example.mehrsms

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private val SMS_PERMISSION_CODE = 101
    private lateinit var listView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ساخت رابط کاربری ساده در محیط کد
        listView = ListView(this)
        setContentView(listView)

        // بررسی مجوزهای خواندن پیامک
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
            Toast.makeText(this, "مجوز دسترسی به پیامک داده نشد!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSmsMessages() {
        val smsList = ArrayList<String>()
        val uri = Uri.parse("content://sms/inbox")
        val cursor = contentResolver.query(uri, null, null, null, "date DESC")

        cursor?.use {
            val bodyIndex = it.getColumnIndex("body")
            val addressIndex = it.getColumnIndex("address")
            val dateIndex = it.getColumnIndex("date")

            var count = 0
            while (it.moveToNext() && count < 50) { // خواندن ۵۰ پیامک اخیر
                val body = it.getString(bodyIndex) ?: ""
                val address = it.getString(addressIndex) ?: "ناشناس"
                val dateMillis = it.getLong(dateIndex)

                // تبدیل تاریخ به شمسی
                val calendar = Calendar.getInstance().apply { timeInMillis = dateMillis }
                val solarDate = SmsEngine.toSolarHijri(
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.DAY_OF_MONTH)
                )

                // دسته‌بندی هوشمند آفلاین
                val category = SmsEngine.classify(body)
                val categoryTag = when (category) {
                    SmsCategory.BANK -> "🏦 [بانکی]"
                    SmsCategory.OTP -> "🔐 [کد تأیید]"
                    SmsCategory.ORDER -> "📦 [سفارش]"
                    SmsCategory.PERSONAL -> "💬 [شخصی]"
                }

                smsList.add("$categoryTag $address\n📅 $solarDate\n$body")
                count++
            }
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, smsList)
        listView.adapter = adapter
    }
}
