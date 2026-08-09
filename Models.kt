package com.example.mehrsms

data class SmsMessage(
    val id: Long = -1L,
    val sender: String,
    val body: String,
    val date: String,
    val timestamp: Long
)

data class SmsThread(
    val sender: String,
    var contactName: String,
    val messages: MutableList<SmsMessage>,
    var category: String
)
