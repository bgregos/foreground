package me.bgregos.foreground

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.bgregos.foreground.data.taskfilter.TaskFilterRepository
import me.bgregos.foreground.data.tasks.TaskRepository
import me.bgregos.foreground.model.Task
import me.bgregos.foreground.tasklist.MainActivity
import me.bgregos.foreground.tasklist.getVisibleTasks
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject


class ForegroundListWidgetProvider : AppWidgetProvider() {

    companion  object {
        lateinit var manager: AppWidgetManager
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // There may be multiple widgets active, so update all of them

        manager = appWidgetManager
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }
}

internal fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
    val views = RemoteViews(
            context.packageName,
            R.layout.list_widget
    )
    val intent = Intent(context, WidgetRemoteViewsService::class.java)
    intent.putExtra("app_id", appWidgetId)
     views.setOnClickPendingIntent(R.id.logo, PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), 0))
    views.setRemoteAdapter(R.id.widgetListView, intent)
    appWidgetManager.updateAppWidget(appWidgetId, views)
}



class WidgetRemoteViewsService : RemoteViewsService() {

    @Inject
    lateinit var taskRepository: TaskRepository
    @Inject
    lateinit var filterRepository: TaskFilterRepository

    override fun onGetViewFactory(intent: Intent?): RemoteViewsFactory {
        this.applicationContext.getApplicationComponent().inject(this)
        val appId = intent!!.getIntExtra("app_id", -1)
        return WidgetRemoteViewFactory(
                this.applicationContext,
                appId,
                this.taskRepository,
                this.filterRepository,
                ForegroundListWidgetProvider.manager)
    }

}


class WidgetRemoteViewFactory(
        val context: Context,
        private val appId: Int,
        private val taskRepo: TaskRepository,
        private val filtersRepository: TaskFilterRepository,
        private val appWidgetManager: AppWidgetManager) : RemoteViewsService.RemoteViewsFactory {

    private var tasks: List<Task> = emptyList()
    private val format = SimpleDateFormat("EEE, MMM d yyyy, HH:mm ", Locale.getDefault())
    private val taskJob : Job

    init {
        val me = this
        taskJob = GlobalScope.launch (Dispatchers.Main) {
            getVisibleTasks(taskRepo, filtersRepository).collect {
                me.tasks = it
                appWidgetManager.notifyAppWidgetViewDataChanged(appId, R.id.widgetListView)
            }
        }
    }

    override fun onCreate() {


    }

    override fun getLoadingView(): RemoteViews? {
        return null

    }

    override fun getItemId(position: Int): Long {
        return position.toLong()

    }

    override fun onDataSetChanged() {
    }

    override fun hasStableIds(): Boolean {
        return false
    }

    override fun getViewAt(position: Int): RemoteViews {
        val task = tasks[position]
        val rv = RemoteViews(context.packageName, R.layout.task_list_content_widget)
        rv.setTextViewText(R.id.title, task.name)

        // TODO: On-click open details menu instead

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
        return tasks.count()
    }

    override fun getViewTypeCount(): Int {
        return 1

    }

    override fun onDestroy() {
        taskJob.cancel()
    }

}