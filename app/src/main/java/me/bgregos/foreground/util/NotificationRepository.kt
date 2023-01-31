package me.bgregos.foreground.util

import android.app.*
import android.app.NotificationManager
import android.app.PendingIntent.FLAG_CANCEL_CURRENT
import android.app.PendingIntent.getBroadcast
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import me.bgregos.foreground.*
import me.bgregos.foreground.model.Task
import me.bgregos.foreground.receiver.AlarmReceiver
import me.bgregos.foreground.receiver.TaskBroadcastReceiver
import me.bgregos.foreground.tasklist.MainActivity
import javax.inject.Inject


class NotificationRepository @Inject constructor(private val mgr: AlarmManager, private val context: Context) {

    val ACTION = "me.bgregos.brighttask.SEND_NOTIFICATION"

    var lastAssignedNotificationId: Int = 0
    var scheduledNotifications = ArrayList<Pair<Task, PendingIntent>>()

    private fun clearNotifications() {
        scheduledNotifications.forEach {
            if ( it.second.creatorPackage != null) {
                // The pendingIntent will sometimes have null data - ignore these
                mgr.cancel(it.second)
            }
        }
        scheduledNotifications.clear()
    }

    fun scheduleNotificationForTasks(tasks: List<Task>) {
        clearNotifications()
        tasks.forEach { scheduleNotificationForTask(it) }
        save()
    }

    private fun scheduleNotificationForTask(task: Task) {
        val due = task.dueDate
        if (due != null) {
            if (due.time < System.currentTimeMillis()) {
                return
            }
            val sendIntent: Intent = Intent(ACTION).apply {
                setClass(context, AlarmReceiver::class.java)
                putExtra("uuid", task.uuid.toString())
            }
            val flags = if (Build.VERSION.SDK_INT >= 23) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
            val pi = getBroadcast(context, task.hashCode(), sendIntent, flags)
            scheduledNotifications.add(Pair(task, pi))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due.time, pi)
            } else {
                mgr.setExact(AlarmManager.RTC_WAKEUP, due.time, pi)
            }
            Log.d("notif", "scheduled notification for ${task.name}: ${due.time} (current time is ${System.currentTimeMillis()})")
        }
    }

    fun save() {
        //TODO: replace json serialization/sharedprefs with Room
        val prefs = context.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putInt("TaskNotification.lastAssignedNotificationId", lastAssignedNotificationId)
        editor.putString("TaskNotification.scheduledNotifications", Gson().toJson(scheduledNotifications))
        editor.putInt("TaskNotification.version", 2)
        editor.apply()
        Log.d("notif", "saved notification service state")
    }

    fun load() {
        //TODO: replace json serialization/sharedprefs with Room
        val prefs = context.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
        val taskType = object : TypeToken<ArrayList<Task>>() {}.type
        val notificationDataVersion: Int = prefs.getInt("TaskNotification.version", 1)
        if(notificationDataVersion == 1) {
            val notificationService: NotificationRepository? = Gson().fromJson(prefs.getString("NotificationService", ""), NotificationRepository::class.java)
            scheduledNotifications = notificationService?.scheduledNotifications ?: ArrayList()
            lastAssignedNotificationId =notificationService?.lastAssignedNotificationId ?: 0
            val editor = prefs.edit()
            editor.putInt("TaskNotification.lastAssignedNotificationId", lastAssignedNotificationId)
            editor.putString("TaskNotification.scheduledNotifications", Gson().toJson(scheduledNotifications))
            editor.putInt("TaskNotification.version", 2)
            editor.apply()
        } else {
            val typeToken = object: TypeToken<ArrayList<Pair<Task, PendingIntent>>>(){}.type
            lastAssignedNotificationId = prefs.getInt("TaskNotification.lastAssignedNotificationId", 0)
            scheduledNotifications = Gson().fromJson(prefs.getString("TaskNotification.scheduledNotifications", ""), typeToken)
        }

        Log.d("notif", "restored notification service state")
    }

    fun showDueNotification(task: Task) {
        Log.d("notif", "attempting to show notification for: ${task.name}")

        lastAssignedNotificationId += 1

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val doneIntent = Intent(context, MainActivity::class.java).apply {
            action = "BRIGHTTASK_MARK_TASK_DONE"
            setClass(context, TaskBroadcastReceiver::class.java)
            putExtra("notification_id", lastAssignedNotificationId)
            putExtra("uuid", task.uuid.toString())
        }

        val flags = if (Build.VERSION.SDK_INT >= 23) {
            FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            FLAG_CANCEL_CURRENT
        }

        val donePendingIntent = getBroadcast(context, Int.MAX_VALUE- lastAssignedNotificationId, doneIntent, flags)

        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, lastAssignedNotificationId, intent, flags)

        val builder = NotificationCompat.Builder(context, "me.bgregos.foreground.tasksdue")
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(task.name)
                //.setContentText(task.dueDate.toString())
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .addAction(R.drawable.ic_check_black_24dp, "Done", donePendingIntent)
                // Set the intent that will fire when the user taps the notification
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            // notificationId is a unique int for each notification that you must define
            notify(lastAssignedNotificationId, builder.build())
        }

    }

    fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Foreground Tasks Due"
            val descriptionText = "Notifications when Foreground tasks are due"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("me.bgregos.foreground.tasksdue", name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            Log.d("notif", "created notification channel")
        }
    }

}