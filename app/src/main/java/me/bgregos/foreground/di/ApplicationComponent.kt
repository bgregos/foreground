package me.bgregos.foreground.di

import android.content.Context
import androidx.work.WorkerFactory
import dagger.BindsInstance
import dagger.Component
import me.bgregos.foreground.network.RemoteTaskSource
import me.bgregos.foreground.receiver.AlarmReceiver
import me.bgregos.foreground.settings.SettingsActivity
import me.bgregos.foreground.tasklist.TaskRepository
import me.bgregos.foreground.tasklist.MainActivity
import me.bgregos.foreground.tasklist.TaskDetailFragment
import me.bgregos.foreground.tasklist.TaskListFragment
import me.bgregos.foreground.util.NotificationRepository
import javax.inject.Singleton

@Singleton
@Component(modules= [
    ViewModelBindingModule::class,
    WorkerBindingModule::class,
    TaskModule::class
] )
interface ApplicationComponent {

    var localTasksRepository: TaskRepository
    var remoteTaskSource: RemoteTaskSource
    var notificationRepository: NotificationRepository
    var workerFactory: WorkerFactory

    @Component.Builder
    abstract class Builder {
        @BindsInstance
        abstract fun bindContext(context: Context): Builder
        abstract fun build(): ApplicationComponent
    }

    interface Owner{
        fun getApplicationComponent(): ApplicationComponent
    }

    fun inject(activity: MainActivity)
    fun inject(activity: SettingsActivity)

    fun inject(fragment: TaskDetailFragment)
    fun inject(fragment: TaskListFragment)

    fun inject(broadcastReceiver: AlarmReceiver)

}
