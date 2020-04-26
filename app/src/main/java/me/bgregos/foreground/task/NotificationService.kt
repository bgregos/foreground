package me.bgregos.foreground.task

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import me.bgregos.foreground.R
import me.bgregos.foreground.TaskDetailActivity

object NotificationService {

    var lastAssignedNotificationId: Int = 0
    var activeNotifications = ArrayList<Triple<Int, String, String>>()

    class DueNotificationWorker(private val context: Context, params: WorkerParameters, private val uuid: String, private val taskName: String) : Worker(context, params) {
        override fun doWork(): Result { // Method to trigger an instant notification
            createNotificationChannel(context)
            showDueNotification(context, uuid, taskName)
            return Result.success()
        }
    }

    fun save(context: Context) {
        LocalTasks.updateVisibleTasks()
        val prefs = context.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("NotificationService", Gson().toJson(this))
        editor.apply()
    }

    fun load(context: Context) {
        val prefs = context.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
        val taskType = object : TypeToken<ArrayList<Task>>() {}.type
        activeNotifications = Gson().fromJson(prefs.getString("NotificationService", ""), NotificationService.javaClass)?.activeNotifications ?: ArrayList()
        lastAssignedNotificationId = Gson().fromJson(prefs.getString("NotificationService", ""), NotificationService.javaClass)?.lastAssignedNotificationId ?: 0
    }

    private fun showDueNotification(context: Context, uuid: String, taskName: String){
        val intent = Intent(context, TaskDetailActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        intent.putExtra("uuid", uuid)
        intent.putExtra("name", taskName)

        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, 0)

        val builder = NotificationCompat.Builder(context, "me.bgregos.foreground.tasksdue")
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle("My notification")
                .setContentText("Hello World!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                // Set the intent that will fire when the user taps the notification
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            // notificationId is a unique int for each notification that you must define
            lastAssignedNotificationId += 1
            activeNotifications.add(Triple(lastAssignedNotificationId, uuid, taskName))
            notify(lastAssignedNotificationId, builder.build())
        }

    }

    private fun createNotificationChannel(context: Context) {
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
        }
    }

}