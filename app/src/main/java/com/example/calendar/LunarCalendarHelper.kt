package com.example.calendar

import java.util.Calendar
import kotlin.math.floor
import kotlin.math.PI
import kotlin.math.sin

/**
 * Vietnamese Lunar Calendar Converter (Lịch Âm Việt Nam UTC+7)
 * Based on Ho Ngoc Duc's astronomical algorithm for accurate Vietnamese lunar dates.
 */
data class LunarDate(
    val day: Int,
    val month: Int,
    val year: Int,
    val isLeap: Boolean = false,
    val dayCanChi: String = "",
    val monthCanChi: String = "",
    val yearCanChi: String = "",
    val zodiacHours: String = ""
) {
    /** Short display format like "18/6" or "18/06" */
    fun toShortString(showAmLabel: Boolean = false, padZero: Boolean = false): String {
        val dStr = if (padZero && day < 10) "0$day" else "$day"
        val mStr = if (padZero && month < 10) "0$month" else "$month"
        val leapStr = if (isLeap) " (N)" else ""
        return if (showAmLabel) "$dStr/$mStr Âm lịch$leapStr" else "$dStr/$mStr$leapStr"
    }

    /** Full display format for detailed card view */
    fun toFullString(): String {
        val leapStr = if (isLeap) " (Tháng nhuận)" else ""
        return "Ngày $day tháng $month năm $yearCanChi$leapStr"
    }
}

object LunarCalendarHelper {
    private val CAN = arrayOf("Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý")
    private val CHI = arrayOf("Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi")
    private val ZODIACS = arrayOf(
        "Tý (23h-1h), Sửu (1h-3h), Mão (5h-7h), Tỵ (9h-11h), Thân (15h-17h), Dậu (17h-19h)",
        "Dần (3h-5h), Mão (5h-7h), Tỵ (9h-11h), Thân (15h-17h), Tuất (19h-21h), Hợi (21h-23h)",
        "Tý (23h-1h), Sửu (1h-3h), Thìn (7h-9h), Tỵ (9h-11h), Mùi (13h-15h), Tuất (19h-21h)",
        "Dần (3h-5h), Thìn (7h-9h), Tỵ (9h-11h), Thân (15h-17h), Dậu (17h-19h), Hợi (21h-23h)",
        "Tý (23h-1h), Dần (3h-5h), Mão (5h-7h), Ngọ (11h-13h), Mùi (13h-15h), Dậu (17h-19h)",
        "Dần (3h-5h), Mão (5h-7h), Tỵ (9h-11h), Thân (15h-17h), Tuất (19h-21h), Hợi (21h-23h)"
    )

    private const val TIME_ZONE = 7.0

    /**
     * Get Julian Day Number from Solar date (Day, Month, Year)
     */
    fun jdnFromDate(dd: Int, mm: Int, yy: Int): Int {
        val a = (14 - mm) / 12
        val y = yy + 4800 - a
        val m = mm + 12 * a - 3
        return dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
    }

    /**
     * Compute Sun longitude in degrees (0..360) at Julian day
     */
    private fun getSunLongitude(jdn: Int, timeZone: Double): Double {
        val t = (jdn - 2451545.5 - timeZone / 24.0) / 36525.0
        val dr = PI / 180.0
        val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t * t
        val m = 357.52910 + 35999.05030 * t - 0.0001559 * t * t - 0.00000048 * t * t * t
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(m * dr) +
                (0.019993 - 0.000101 * t) * sin(2 * m * dr) +
                0.000289 * sin(3 * m * dr)
        var sunLong = l0 + c
        sunLong %= 360.0
        if (sunLong < 0) sunLong += 360.0
        return sunLong
    }

    /**
     * Calculate Julian Day of the K-th New Moon
     */
    private fun getNewMoonDay(k: Int, timeZone: Double): Int {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        val dr = PI / 180.0

        var jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
        jd1 += 0.00033 * sin((166.56 + 132.87 * t - 0.009173 * t2) * dr)

        // Solar anomaly
        val m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
        // Lunar anomaly
        val mprime = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
        // Lunar latitude argument
        val f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3

        var deltaJd = (0.1734 - 0.000393 * t) * sin(m * dr) +
                0.0021 * sin(2 * m * dr) -
                0.4068 * sin(mprime * dr) +
                0.0161 * sin(2 * mprime * dr) -
                0.0004 * sin(3 * mprime * dr) +
                0.0104 * sin(2 * f * dr) -
                0.0051 * sin((m + mprime) * dr) -
                0.0074 * sin((m - mprime) * dr) +
                0.0004 * sin((2 * f + m) * dr) -
                0.0004 * sin((2 * f - m) * dr) -
                0.0006 * sin((2 * f + mprime) * dr) +
                0.0010 * sin((2 * f - mprime) * dr) +
                0.0005 * sin((m + 2 * mprime) * dr)

        val jd2 = jd1 + deltaJd
        return floor(jd2 + 0.5 + timeZone / 24.0).toInt()
    }

    /**
     * Find 11th Lunar Month (Tháng Tý) for a given solar year
     */
    private fun getLunarMonth11(yy: Int, timeZone: Double): Int {
        val off = jdnFromDate(31, 12, yy) - 2415021
        val k = floor(off / 29.53058886378).toInt()
        var nm = getNewMoonDay(k, timeZone)
        val sunLong = getSunLongitude(nm, timeZone)
        if (sunLong >= 270.0) {
            nm = getNewMoonDay(k - 1, timeZone)
        }
        return nm
    }

    /**
     * Main converter from Solar (Day, Month, Year) to LunarDate
     */
    fun convertSolarToLunar(dd: Int, mm: Int, yy: Int): LunarDate {
        val dayJd = jdnFromDate(dd, mm, yy)
        val k = floor((dayJd - 2415021) / 29.53058886378).toInt()
        var monthStart = getNewMoonDay(k, TIME_ZONE)
        if (monthStart > dayJd) {
            monthStart = getNewMoonDay(k - 1, TIME_ZONE)
        }

        val lunarDay = dayJd - monthStart + 1

        // Determine lunar year and 11th month
        var a11 = getLunarMonth11(yy, TIME_ZONE)
        var b11 = a11
        var lunarYear = yy

        if (a11 >= monthStart) {
            lunarYear = yy
            a11 = getLunarMonth11(yy - 1, TIME_ZONE)
        } else {
            lunarYear = yy + 1
            b11 = getLunarMonth11(yy + 1, TIME_ZONE)
        }

        val lunarMonth: Int
        var isLeap = false

        val kMonth = floor((monthStart - 2415021) / 29.53058886378 + 0.5).toInt()
        val kA11 = floor((a11 - 2415021) / 29.53058886378 + 0.5).toInt()
        var offMonths = kMonth - kA11

        val numberOfMonths = floor((b11 - a11) / 29.53058886378 + 0.5).toInt()

        if (numberOfMonths > 12) {
            // Leap year logic
            var leapIndex = -1
            for (i in 0 until numberOfMonths) {
                val nm1 = getNewMoonDay(kA11 + i, TIME_ZONE)
                val nm2 = getNewMoonDay(kA11 + i + 1, TIME_ZONE)
                val sl1 = getSunLongitude(nm1, TIME_ZONE).toInt() / 30
                val sl2 = getSunLongitude(nm2, TIME_ZONE).toInt() / 30
                if (sl1 == sl2) {
                    leapIndex = i
                    break
                }
            }

            if (leapIndex in 0 until offMonths) {
                if (offMonths == leapIndex + 1) {
                    isLeap = true
                }
                offMonths--
            }
        }

        lunarMonth = (offMonths + 10) % 12 + 1

        // Adjust lunar year for dates before Lunar New Year
        val finalYear = if (lunarMonth >= 11 && offMonths >= 10) lunarYear - 1 else lunarYear

        // Can Chi calculations
        val yearCan = CAN[(finalYear + 6) % 10]
        val yearChi = CHI[(finalYear + 8) % 12]
        val yearCanChi = "$yearCan $yearChi"

        val dayCan = CAN[(dayJd + 9) % 10]
        val dayChi = CHI[(dayJd + 1) % 12]
        val dayCanChi = "$dayCan $dayChi"

        val monthCan = CAN[(finalYear * 12 + lunarMonth + 3) % 10]
        val monthChi = CHI[(lunarMonth + 1) % 12]
        val monthCanChi = "$monthCan $monthChi"

        val zodiacIndex = (dayJd + 1) % 6
        val zodiacHours = ZODIACS[zodiacIndex]

        return LunarDate(
            day = lunarDay,
            month = lunarMonth,
            year = finalYear,
            isLeap = isLeap,
            dayCanChi = dayCanChi,
            monthCanChi = monthCanChi,
            yearCanChi = yearCanChi,
            zodiacHours = zodiacHours
        )
    }

    /**
     * Get Lunar date for today (current system time)
     */
    fun getTodayLunar(): LunarDate {
        val cal = Calendar.getInstance()
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val m = cal.get(Calendar.MONTH) + 1
        val y = cal.get(Calendar.YEAR)
        return convertSolarToLunar(d, m, y)
    }
}
