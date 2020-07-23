package me.bgregos.foreground.task

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import me.bgregos.foreground.R
import me.bgregos.foreground.TaskListActivity
import me.bgregos.foreground.toLocal
import kotlin.random.Random


object NotificationService {

    var lastAssignedNotificationId: Int = 0
    var activeNotifications = ArrayList<Pair<Int, Task>>()
    var scheduledNotifications = ArrayList<Pair<Task, PendingIntent>>()

    class DueNotificationWorker(private val context: Context, params: WorkerParameters, private val task: Task) : Worker(context, params) {
        override fun doWork(): Result {

            scheduleNotificationForTask(task, context)
            return Result.success()
        }
    }

    fun clearNotifications(context: Context) {
        val mgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduledNotifications.forEach {
            mgr.cancel(it.second)
        }
        scheduledNotifications.clear()
        Log.d("notif", "cleared scheduled alarms")
    }

    fun scheduleNotificationForTasks(tasks:List<Task>, context: Context){
        //tasks.forEach { scheduleNotificationForTask(it, context) }
    }

    fun scheduleNotificationForTask(task : Task, context: Context) {
        val due = task.dueDate
        if (due != null) {
            var sendIntent: Intent = Intent("BRIGHTTASK_SEND_NOTIFICATION") //Send local broadcast
            val mgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(context, task.hashCode(), sendIntent, 0)
            scheduledNotifications.add(Pair(task, pi))
            mgr.setExact(AlarmManager.RTC_WAKEUP, due.toLocal().time, pi)
            //TODO: make broadcast explicit: https://stackoverflow.com/questions/46304839/android-8-0-oreo-alarmmanager-with-broadcast-receiver-and-implicit-broadcast-ban
            Log.d("notif", "scheduled notification for ${task.name}: ${due.toLocal().toLocaleString()}")
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
        activeNotifications = Gson().fromJson(prefs.getString("NotificationService", ""), NotificationService.javaClass)?.activeNotifications ?: ArrayList()
        scheduledNotifications = Gson().fromJson(prefs.getString("NotificationService", ""), NotificationService.javaClass)?.scheduledNotifications ?: ArrayList()
        lastAssignedNotificationId = Gson().fromJson(prefs.getString("NotificationService", ""), NotificationService.javaClass)?.lastAssignedNotificationId ?: 0
        Log.d("notif", "restored notification service state")
    }

    fun showDueNotification(task: Task, context: Context){
        Log.d("notif", "attempting to show notification for: ${task.name}")

        val intent = Intent(context, TaskListActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, 0)

        val builder = NotificationCompat.Builder(context, "me.bgregos.foreground.tasksdue")
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle(task.name)
                .setContentText(task.dueDate.toString())
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                // Set the intent that will fire when the user taps the notification
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            // notificationId is a unique int for each notification that you must define
            lastAssignedNotificationId += 1
            activeNotifications.add(Pair(lastAssignedNotificationId, task))
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