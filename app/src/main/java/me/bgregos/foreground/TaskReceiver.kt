package me.bgregos.foreground

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import me.bgregos.foreground.task.LocalTasks
import java.util.*

class TaskReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when(intent?.action) {
            "BRIGHTTASK_MARK_TASK_DONE" -> {
                Log.i("notif", "Received task done")
                val notifID: Int? = intent.extras?.getInt("notification_id")
                if(notifID != null){
                    val mgr = context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    mgr.cancel(notifID)
                }
                val uuid: String = (intent.extras?.get("uuid") ?: "") as String
                if(uuid == "") {
                    Log.e("notif", "Failed to mark task done- no UUID")
                }
                if(context != null){
                    LocalTasks.load(context)
                    val item = LocalTasks.getTaskByUUID(UUID.fromString(uuid))
                    if(item == null) {
                        Log.e("LocalTasks", "Failed to find a task with the given UUID")
                        return
                    }
                    item.modifiedDate=Date().toUtc() //update modified date
                    item.status = "completed"
                    if (!LocalTasks.localChanges.contains(item)){
                        LocalTasks.localChanges.add(item)
                    }
                    LocalTasks.save(context)
                    var localIntent: Intent = Intent("BRIGHTTASK_REMOTE_TASK_UPDATE") //Send local broadcast
                    LocalBroadcastManager.getInstance(context).sendBroadcast(localIntent)
                } else {
                    Log.e("notif", "Failed to save task marked done- null context")
                }

            }
        }
    }
}