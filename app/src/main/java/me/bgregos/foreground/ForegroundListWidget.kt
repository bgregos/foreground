package me.bgregos.foreground

import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import me.bgregos.foreground.model.Task
import me.bgregos.foreground.tasklist.MainActivity
import me.bgregos.foreground.tasklist.TaskViewModel
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.collections.HashSet

class ForegroundListWidgetProvider : AppWidgetProvider() {

    companion  object {
        var manager: AppWidgetManager? = null
    }
    private val known: HashSet<Int> = HashSet()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        manager = appWidgetManager
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)
        context?.let {
            val appWidgetManager = AppWidgetManager.getInstance(it)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(it, ForegroundListWidgetProvider::class.java))
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(known, it, appWidgetManager, appWidgetId)
            }
            it.startService(Intent(context, UpdateWidgetService::class.java))
        }
    }

    override fun onDisabled(context: Context) {
    }
}

internal fun updateAppWidget(
        known: HashSet<Int>,
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int) {
    if (known.contains((appWidgetId))) return;
    val views = RemoteViews(
            context.packageName,
            R.layout.list_widget
    )
    val intent = Intent(context, WidgetRemoteViewsService::class.java)
    intent.putExtra("app_id", appWidgetId)
    val flags = if (Build.VERSION.SDK_INT >= 23) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT
    }

    views.setOnClickPendingIntent(R.id.logo, PendingIntent.getActivity(context, flags, Intent(context, MainActivity::class.java), flags))
    views.setRemoteAdapter(R.id.widgetListView, intent)
    appWidgetManager.updateAppWidget(appWidgetId, views)
    known.add(appWidgetId)
}

class UpdateWidgetService : Service() {
    companion object {
        var tasks: List<Task> = emptyList()
    }

    @Inject lateinit var taskViewModel: TaskViewModel

    private var taskJob : Job? = null

    override fun onCreate() {
        getApplicationComponent().inject(this)
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val appWidgetManager = AppWidgetManager.getInstance(this
                .applicationContext)
        if(taskJob == null || taskJob!!.isCompleted) {
            taskJob = taskViewModel.viewModelScope.launch {
                taskViewModel.load()
                taskViewModel
                        .visibleTasks
                        .collect {
                            tasks = it
                            val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName((this@UpdateWidgetService).applicationContext, ForegroundListWidgetProvider::class.java))
                            appWidgetManager?.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widgetListView)
                        }
            }
        }


        return START_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

}

class WidgetRemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent?): RemoteViewsFactory {
        return WidgetRemoteViewFactory(applicationContext)
    }

}

class WidgetRemoteViewFactory(val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private val format = SimpleDateFormat("EEE, MMM d yyyy, HH:mm ", Locale.getDefault())

    override fun getLoadingView(): RemoteViews? {
        return null

    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun onCreate() {

    }

    override fun onDataSetChanged() {
    }

    override fun hasStableIds(): Boolean {
        return false
    }

    override fun getViewAt(position: Int): RemoteViews {

        val task = UpdateWidgetService.tasks[position]
        val rv = RemoteViews(context.packageName, R.layout.task_list_content_widget)
        rv.setTextViewText(R.id.title, task.name)


        if (task.dueDate != null) {
            val dueDate = task.dueDate
            rv.setTextViewText(R.id.due, format.format(dueDate))
            rv.setViewVisibility(R.id.due, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.due, View.GONE)
        }

        return rv
    }

    override fun getCount(): Int {
        return UpdateWidgetService.tasks.count()
    }

    override fun getViewTypeCount(): Int {
        return 1

    }

    override fun onDestroy() {
    }

}