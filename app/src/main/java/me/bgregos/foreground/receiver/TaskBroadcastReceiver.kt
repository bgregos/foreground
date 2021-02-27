package me.bgregos.foreground.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.util.Log
import me.bgregos.foreground.tasklist.LocalTasksRepository
import me.bgregos.foreground.util.contentsChanged
import java.util.*
import javax.inject.Inject

class TaskBroadcastReceiver: BroadcastReceiver() {

    @Inject
    lateinit var localTasksRepository: LocalTasksRepository

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
                    localTasksRepository.load()
                    val item = localTasksRepository.getTaskByUUID(UUID.fromString(uuid))
                    if(item == null) {
                        Log.e("LocalTasks", "Failed to find a task with the given UUID")
                        return
                    }
                    item.modifiedDate=Date() //update modified date
                    item.status = "completed"
                    if (!localTasksRepository.localChanges.value.contains(item)){
                        localTasksRepository.localChanges.apply{
                            value.add(item)
                            contentsChanged()
                        }
                    }
                    localTasksRepository.save()
                    val localIntent: Intent = Intent("BRIGHTTASK_REMOTE_TASK_UPDATE") //Send local broadcast
                    LocalBroadcastManager.getInstance(context).sendBroadcast(localIntent)
                } else {
                    Log.e("notif", "Failed to save task marked done- null context")
                }

            }
        }
    }
}