package me.bgregos.foreground.util

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager


fun hideKeyboardFrom(context: Context, view: View) {
    val imm: InputMethodManager = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(view.getWindowToken(), 0)
}