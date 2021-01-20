package me.bgregos.foreground.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import me.bgregos.foreground.tasklist.LocalTasks
import me.bgregos.foreground.tasklist.MainActivity


object NotificationService {

    val ACTION = "me.bgregos.brighttask.SEND_NOTIFICATION"

    var lastAssignedNotificationId: Int = 0
    var scheduledNotifications = ArrayList<Pair<Task, PendingIntent>>()

    private fun clearNotifications(context: Context) {
        val mgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduledNotifications.forEach {
            mgr.cancel(it.second)
        }
        scheduledNotifications.clear()

        Log.d("notif", "cleared scheduled alarms")
    }

    fun scheduleNotificationForTasks(tasks: List<Task>, context: Context) {
        clearNotifications(context)
        tasks.forEach { scheduleNotificationForTask(it, context) }
    }

    private fun scheduleNotificationForTask(task: Task, context: Context) {
        val due = task.dueDate
        if (due != null) {
            if (due.time < System.currentTimeMillis()) {
                return
            }
            val sendIntent: Intent = Intent(ACTION).apply {
                setClass(context, AlarmReceiver::class.java)
                putExtra("uuid", task.uuid.toString())
            }
            val mgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = getBroadcast(context, task.hashCode(), sendIntent, 0)
            scheduledNotifications.add(Pair(task, pi))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                mgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due.time, pi)
            } else {
                mgr.setExact(AlarmManager.RTC_WAKEUP, due.time, pi)
            }
            Log.d("notif", "scheduled notification for ${task.name}: ${due.time} (current time is ${System.currentTimeMillis()})")
        }
    }

    fun save(context: Context) {
        LocalTasks.updateVisibleTasks()
        val prefs = context.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("NotificationService", Gson().toJson(this))
        editor.apply()
        Log.d("notif", "saved notification service state")
    }

    fun load(context: Context) {
        val prefs = context.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
        val taskType = object : TypeToken<ArrayList<Task>>() {}.type
        val notificationService: NotificationService? = Gson().fromJson(prefs.getString("NotificationService", ""), NotificationService.javaClass)
        scheduledNotifications = scheduledNotifications ?: ArrayList()
        lastAssignedNotificationId = lastAssignedNotificationId ?: 0
        Log.d("notif", "restored notification service state")
    }

    fun showDueNotification(task: Task, context: Context) {
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
        val donePendingIntent = getBroadcast(context, Int.MAX_VALUE- lastAssignedNotificationId, doneIntent, FLAG_CANCEL_CURRENT)

        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, lastAssignedNotificationId, intent, FLAG_CANCEL_CURRENT)

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

    fun createNotificationChannel(context: Context) {
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