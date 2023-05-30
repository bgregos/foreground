package me.bgregos.foreground

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.bgregos.foreground.model.Task
import me.bgregos.foreground.receiver.TaskBroadcastReceiver
import me.bgregos.foreground.tasklist.MainActivity
import me.bgregos.foreground.tasklist.MainActivity.Companion.BRIGHTTASK_OPEN_TASK
import me.bgregos.foreground.tasklist.MainActivity.Companion.BRIGHTTASK_OPEN_TASK_PARAM_UUID
import me.bgregos.foreground.tasklist.TaskViewModel
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject


class ForegroundListWidgetUpdater @Inject constructor(
    val context: Context
) {
    /**
     * Allows other parts of the application to refresh the widget on demand.
     */
    fun updateWidget(){
        Log.d("foreground widget", "update widget called")
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context.applicationContext, ForegroundListWidgetProvider::class.java))
        manager.notifyAppWidgetViewDataChanged(ids, R.id.widgetListView)
    }
}

class ForegroundListWidgetProvider : AppWidgetProvider() {

//    override fun onReceive(context: Context?, intent: Intent?) {
//        Log.d("foreground widget", "received broadcast action ${intent?.action}")
//        context?.let {
//            if(intent?.action == Intent.ACTION_BOOT_COMPLETED) {
//                val appWidgetManager = AppWidgetManager.getInstance(context)
//                val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context.applicationContext, ForegroundListWidgetProvider::class.java))
//                Log.d("foreground widget", "got ids ${ids.toList()}")
//                onUpdate(context, appWidgetManager, ids)
//            }
//        }
//        super.onReceive(context, intent)
//    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d("foreground widget", "onUpdated called with ids ${appWidgetIds.toList()}")
        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(
                context.packageName,
                R.layout.list_widget
            )
            val intent = Intent(context, WidgetRemoteViewsService::class.java).apply {
                // Add the widget ID to the intent extras.
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }

            val flags = if (Build.VERSION.SDK_INT >= 23) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            views.setOnClickPendingIntent(
                R.id.logo,
                PendingIntent.getActivity(
                    context, flags, Intent(context, MainActivity::class.java), flags
                )
            )

            val taskPendingIntent: PendingIntent = Intent(
                context,
                MainActivity::class.java
            ).run {
                setClass(context, TaskBroadcastReceiver::class.java)
                action = BRIGHTTASK_OPEN_TASK
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))

                val immutableFlag = if (Build.VERSION.SDK_INT >= 31) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }

                PendingIntent.getBroadcast(context, 0, this, PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag)
            }
            views.setPendingIntentTemplate(R.id.widgetListView, taskPendingIntent)

            views.setRemoteAdapter(R.id.widgetListView, intent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }
}

class WidgetRemoteViewsService : RemoteViewsService() {

    @Inject
    lateinit var taskViewModel: TaskViewModel

    override fun onCreate() {
        super.onCreate()
        applicationContext.getApplicationComponent().inject(this)
    }

    override fun onGetViewFactory(intent: Intent?): RemoteViewsFactory {
        return WidgetRemoteViewFactory(applicationContext, taskViewModel)
    }

}

class WidgetRemoteViewFactory(val context: Context, val taskViewModel: TaskViewModel) : RemoteViewsService.RemoteViewsFactory {

    private val format = SimpleDateFormat("EEE, MMM d yyyy, HH:mm ", Locale.getDefault())

    var tasks: List<Task> = emptyList()

    override fun getLoadingView(): RemoteViews? {
        return null
    }

    override fun getItemId(position: Int): Long {
        //not perfect but probability of collision between tasks is very low
        return tasks[position].uuid.leastSignificantBits
    }

    override fun onCreate() {

    }

    override fun onDataSetChanged() {
        runBlocking {
            taskViewModel.load()
            taskViewModel
                .visibleTasks
                .take(1)
                .collect {
                    tasks = it
                    Log.d("foreground widget", "collected ${tasks.map { it.name }} tasks")
                }
        }
    }

    override fun onDestroy() {

    }

    override fun hasStableIds(): Boolean {
        return true
    }

    override fun getViewAt(position: Int): RemoteViews {
        val task = tasks[position]
        val rv = RemoteViews(context.packageName, R.layout.task_list_content_widget)
        rv.setTextViewText(R.id.title, task.name)


        if (task.dueDate != null) {
            val dueDate = task.dueDate
            rv.setTextViewText(R.id.due, format.format(dueDate))
            rv.setViewVisibility(R.id.due, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.due, View.GONE)
        }

        val fillInIntent = Intent().apply {
            Bundle().also { extras ->
                extras.putString(BRIGHTTASK_OPEN_TASK_PARAM_UUID, task.uuid.toString())
                putExtras(extras)
            }
        }
        rv.setOnClickFillInIntent(R.id.widget_item, fillInIntent)

        return rv
    }

    override fun getCount(): Int {
        return tasks.size
    }

    override fun getViewTypeCount(): Int {
        return 1

    }

}