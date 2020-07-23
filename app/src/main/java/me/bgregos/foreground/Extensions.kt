package me.bgregos.foreground

import java.text.SimpleDateFormat
import java.util.*

fun Date.toLocal(): Date {
    val dfLocal = SimpleDateFormat()
    dfLocal.timeZone = TimeZone.getDefault()
    val dfUtc = SimpleDateFormat()
    dfUtc.timeZone = TimeZone.getTimeZone("UTC")
    return dfUtc.parse(dfLocal.format(this))
}

fun Date.toUtc(): Date {
    val dfLocal = SimpleDateFormat()
    dfLocal.timeZone = TimeZone.getDefault()
    val dfUtc = SimpleDateFormat()
    dfUtc.timeZone = TimeZone.getTimeZone("UTC")
    return dfLocal.parse(dfUtc.format(this))
}