package me.bgregos.foreground.util

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.view.View
import androidx.appcompat.app.AlertDialog

public class ShowErrorDetail(val error: String, val activity: Activity): View.OnClickListener {
    override fun onClick(view: View?) {
        AlertDialog.Builder(activity).let {
            it.setTitle("Sync Failed")
            it.setMessage(error)
            it.setPositiveButton("Close", DialogInterface.OnClickListener { _, _ -> Unit })
            it.setNeutralButton("Copy", DialogInterface.OnClickListener { _, _ ->
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip: ClipData = ClipData.newPlainText("foreground_error", error)
                clipboard.setPrimaryClip(clip)
            })
            it.create()
        }.show()
    }
}