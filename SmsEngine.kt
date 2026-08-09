package com.example.mehrsms

enum class SmsCategory {
    BANK,       // بانکی
    OTP,        // کد تأیید
    ORDER,      // سفارش و مرسوله
    PERSONAL    // شخصی و عمومی
}

object SmsEngine {

    // موتور دسته‌بندی محلی و آفلاین (Rule Engine)
    fun classify(message: String): SmsCategory {
        val text = message.lowercase()
        return when {
            text.contains("رمز") || text.contains("کد") || text.contains("otp") || text.contains("تایید") -> SmsCategory.OTP
            text.contains("برداشت") || text.contains("واریز") || text.contains("مبلغ") || text.contains("موجودی") || text.contains("بانک") -> SmsCategory.BANK
            text.contains("بسته") || text.contains("پست") || text.contains("کد رهگیری") || text.contains("سفارش") -> SmsCategory.ORDER
            else -> SmsCategory.PERSONAL
        }
    }

    // مبدل دقیق تاریخ میلادی به شمسی (آفلاین)
    fun toSolarHijri(year: Int, month: Int, day: Int): String {
        var gYear = year
        var gMonth = month
        var gDay = day

        val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

        val isLeapG = (gYear % 4 == 0 && gYear % 100 != 0) || (gYear % 400 == 0)
        if (isLeapG) gDaysInMonth[1] = 29

        var gDayOfYear = 0
        for (i in 0 until gMonth - 1) {
            gDayOfYear += gDaysInMonth[i]
        }
        gDayOfYear += gDay

        var jYear: Int
        var jDayOfYear: Int

        if (gDayOfYear > 79) {
            jDayOfYear = gDayOfYear - 79
            jYear = gYear - 621
        } else {
            val prevLeapG = ((gYear - 1) % 4 == 0 && (gYear - 1) % 100 != 0) || ((gYear - 1) % 400 == 0)
            jDayOfYear = gDayOfYear + if (prevLeapG) 287 else 286
            jYear = gYear - 622
        }

        val isLeapJ = ((jYear + 38) * 31) % 128 < 31
        if (isLeapJ) jDaysInMonth[11] = 30

        var jMonth = 0
        while (jMonth < 12 && jDayOfYear > jDaysInMonth[jMonth]) {
            jDayOfYear -= jDaysInMonth[jMonth]
            jMonth++
        }

        val monthName = arrayOf(
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
        )[jMonth]

        return "$jDayOfYear $monthName $jYear"
    }
}

