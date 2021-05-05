package me.bgregos.foreground.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bgregos.foreground.getApplicationComponent
import me.bgregos.foreground.tasklist.TaskViewModel
import java.util.*
import javax.inject.Inject

class TaskBroadcastReceiver: BroadcastReceiver() {

    @Inject
    lateinit var taskViewModel: TaskViewModel

    override fun onReceive(context: Context?, intent: Intent?) {
        context?.getApplicationComponent()?.inject(this)
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
                    CoroutineScope(Dispatchers.Main).launch {
                        taskViewModel.load()
                        val item = taskViewModel.getTaskByUUID(uuid)
                        if(item == null) {
                            Log.e("LocalTasks", "Failed to find a task with the given UUID")
                            return@launch
                        }
                        taskViewModel.markTaskComplete(item)
                        taskViewModel.save()
                    }
                } else {
                    Log.e("notif", "Failed to save task marked done- null context")
                }

            }
        }
    }
}