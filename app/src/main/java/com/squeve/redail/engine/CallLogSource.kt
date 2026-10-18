package com.squeve.redail.engine

import android.annotation.SuppressLint
import android.content.Context
import android.provider.CallLog

/** One entry per number (merged by its last 9 digits), most recent call first. */
data class RecentCall(
    val number: String,
    val name: String,
    val type: Int,
    val date: Long,
    val count: Int,
) {
    val key: String get() = number.filter { it.isDigit() }.takeLast(9)
}

@SuppressLint("MissingPermission")
fun loadRecentCalls(ctx: Context, maxRows: Int = 400): List<RecentCall> {
    val byKey = LinkedHashMap<String, RecentCall>()
    ctx.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE),
        null, null, "${CallLog.Calls.DATE} DESC",
    )?.use { c ->
        var rows = 0
        while (rows < maxRows && c.moveToNext()) {
            rows++
            val number = c.getString(0).orEmpty().filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
            if (number.count { it.isDigit() } < 3) continue          // private / unknown numbers
            val key = number.filter { it.isDigit() }.takeLast(9)
            val prev = byKey[key]
            byKey[key] = if (prev == null) {
                RecentCall(number, c.getString(1).orEmpty(), c.getInt(2), c.getLong(3), 1)
            } else {
                prev.copy(count = prev.count + 1)
            }
        }
    }
    return byKey.values.toList()
}
