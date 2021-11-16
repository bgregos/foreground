package me.bgregos.foreground.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bgregos.foreground.data.tasks.TaskRepository
import me.bgregos.foreground.getApplicationComponent
import me.bgregos.foreground.tasklist.TaskViewModel
import me.bgregos.foreground.util.NotificationRepository
import java.util.*
import javax.inject.Inject

class AlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationRepository: NotificationRepository

    @Inject
    lateinit var taskViewModel: TaskViewModel

    override fun onReceive(context: Context?, intent: Intent?) {
        context?.getApplicationComponent()?.inject(this)
        when (intent?.action) {
            notificationRepository.ACTION -> {
                Log.i("notif", "Received notification intent")
                val uuid: String? = intent.extras?.get("uuid") as String?
                if (uuid == null) {
                    Log.e("notif", "Failed to display notification for task with null uuid")
                    return
                }
                if (context == null){
                    Log.e("notif", "Couldn't show notification - null context")
                }else{
                    CoroutineScope(Dispatchers.Main).launch {
                        taskViewModel.load()
                        val task = taskViewModel.getTaskByUUID(uuid)
                        if (task != null) {
                            notificationRepository.showDueNotification(task)
                        } else {
                            Log.e("notif", "Couldn't show notification - null task")
                        }
                    }
                }
            }
        }

    }

}