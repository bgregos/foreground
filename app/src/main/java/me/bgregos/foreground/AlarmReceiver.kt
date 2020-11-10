package me.bgregos.foreground

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import me.bgregos.foreground.task.LocalTasks
import me.bgregos.foreground.task.NotificationService
import java.util.*

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            NotificationService.ACTION -> {
                Log.i("notif", "Received notification intent")
                val uuid: String? = intent.extras?.get("uuid") as String?
                if (uuid == null) {
                    Log.e("notif", "Failed to display notification for task with null uuid")
                    return
                }
                if (context == null){
                    Log.e("notif", "Couldn't show notification - null context")
                }else{
                    LocalTasks.load(context, true)
                    val task = LocalTasks.getTaskByUUID(UUID.fromString(uuid))
                    if (task != null) {
                        NotificationService.showDueNotification(task, context)
                    } else {
                        Log.e("notif", "Couldn't show notification - null task")
                    }
                }
            }
        }

    }

}