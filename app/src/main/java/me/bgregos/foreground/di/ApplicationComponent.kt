package me.bgregos.foreground.di

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.work.WorkerFactory
import dagger.BindsInstance
import dagger.Component
import me.bgregos.foreground.ForegroundListWidgetProvider
import me.bgregos.foreground.WidgetRemoteViewsService
import me.bgregos.foreground.filter.FiltersFragment
import me.bgregos.foreground.network.RemoteTaskSource
import me.bgregos.foreground.receiver.AlarmReceiver
import me.bgregos.foreground.settings.SettingsActivity
import me.bgregos.foreground.data.tasks.TaskRepository
import me.bgregos.foreground.receiver.TaskBroadcastReceiver
import me.bgregos.foreground.tasklist.MainActivity
import me.bgregos.foreground.tasklist.TaskDetailFragment
import me.bgregos.foreground.tasklist.TaskListFragment
import me.bgregos.foreground.util.NotificationRepository
import javax.inject.Singleton

@Singleton
@Component(modules= [
    ViewModelBindingModule::class,
    WorkerBindingModule::class,
    TaskModule::class,
    DatabaseModule::class,
    FilterModule::class
] )
interface ApplicationComponent {

    var workerFactory: WorkerFactory

    @Component.Builder
    abstract class Builder {
        @BindsInstance
        abstract fun bindContext(context: Context): Builder
        abstract fun build(): ApplicationComponent
    }

    fun inject(activity: MainActivity)
    fun inject(activity: SettingsActivity)

    fun inject(fragment: TaskDetailFragment)
    fun inject(fragment: TaskListFragment)
    fun inject(filtersFragment: FiltersFragment)

    fun inject(broadcastReceiver: AlarmReceiver)
    fun inject(broadcastReceiver: TaskBroadcastReceiver)
    fun inject(widgetRemoteViewsService: WidgetRemoteViewsService)
}
