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
import me.bgregos.foreground.data.tasks.TaskRepository
import me.bgregos.foreground.getApplicationComponent
import me.bgregos.foreground.util.replace
import java.util.*
import javax.inject.Inject

class TaskBroadcastReceiver: BroadcastReceiver() {

    @Inject
    lateinit var tasksRepository: TaskRepository

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
                        tasksRepository.load()
                        val item = tasksRepository.getTaskByUUID(UUID.fromString(uuid))
                        if(item == null) {
                            Log.e("LocalTasks", "Failed to find a task with the given UUID")
                            return@launch
                        }
                        val newTask = item.copy(modifiedDate = Date(), status = "completed")
                        tasksRepository.tasks = tasksRepository.tasks.replace(newTask) { it === item }
                        if (!tasksRepository.localChanges.contains(item)){
                            tasksRepository.localChanges.plus(item)

                        }
                        tasksRepository.tasks.minus(item)
                        tasksRepository.save()
                    }
                } else {
                    Log.e("notif", "Failed to save task marked done- null context")
                }

            }
        }
    }
}