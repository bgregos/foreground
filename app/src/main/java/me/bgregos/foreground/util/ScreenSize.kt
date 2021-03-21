package me.bgregos.foreground.util

import android.content.Context

/*
    Returns true if the screen should be in tablet mode
 */
fun Context.isSideBySide(): Boolean {
    return resources.configuration.screenWidthDp >= 900
}