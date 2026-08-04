package com.simpleaccount.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DateUtil {

    private val ymd: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val ym: SimpleDateFormat = SimpleDateFormat("yyyy-MM", Locale.US)

    /** 今天的 yyyy-MM-dd */
    fun today(): String = ymd.format(Calendar.getInstance().time)

    /** 当前 yyyy-MM */
    fun thisMonth(): String = ym.format(Calendar.getInstance().time)

    /** 某月（yyyy-MM）的完整月份前缀，用于 LIKE 查询 */
    fun monthPrefix(month: String): String = month

    /** 某月的第一天 yyyy-MM-dd */
    fun monthStart(month: String): String = "$month-01"

    /** 某月的最后一天 yyyy-MM-dd（month 形如 yyyy-MM） */
    fun monthEnd(month: String): String {
        val parts = month.split("-")
        val year = parts[0].toInt()
        val mon = parts[1].toInt()
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, mon - 1)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val last = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(year, mon, last)
    }

    /** 最近 N 个月的 (yyyy-MM) 列表：从当前月往前数，含当前月。降序。 */
    fun recentMonths(n: Int = 12): List<String> {
        val cal = Calendar.getInstance()
        val list = mutableListOf<String>()
        repeat(n) {
            list.add("%04d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1))
            cal.add(Calendar.MONTH, -1)
        }
        return list
    }

    /** 校验 yyyy-MM-dd */
    fun isValidYmd(s: String): Boolean = try {
        val p = s.split("-").map { it.toInt() }
        p.size == 3 && p[0] in 1900..2200 && p[1] in 1..12 && p[2] in 1..31
    } catch (e: Exception) {
        false
    }

    /** 从多种常见日期字符串解析为 yyyy-MM-dd，解析失败返回 null */
    fun parseFlexible(input: String): String? {
        val s = input.trim()
        if (s.isEmpty()) return null

        // 处理可能的 "yyyy-MM-dd HH:mm:ss" / "yyyy/M/d HH:mm"
        val datePart = Regex("^([0-9]{4})[-/.年]([0-9]{1,2})[-/.月]([0-9]{1,2})").find(s)
        if (datePart != null) {
            val y = datePart.groupValues[1]
            val m = datePart.groupValues[2].padStart(2, '0')
            val d = datePart.groupValues[3].padStart(2, '0')
            return "$y-$m-$d"
        }
        // 纯 "yyyyMMdd"
        val compact = Regex("^([0-9]{4})([0-9]{2})([0-9]{2})$").find(s)
        if (compact != null) {
            return "${compact.groupValues[1]}-${compact.groupValues[2]}-${compact.groupValues[3]}"
        }
        // Excel 日期序列号（如 46227.88 → 2026-08-04；微信 xlsx 常见）
        val serial = Regex("^([0-9]{4,5})([.,][0-9]+)?$").find(s)
        if (serial != null) {
            return try {
                val d = org.apache.poi.ss.usermodel.DateUtil.getJavaDate(s.toDouble())
                val cal = Calendar.getInstance().apply { time = d }
                "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
            } catch (e: Exception) {
                null
            }
        }
        return null
    }
}
