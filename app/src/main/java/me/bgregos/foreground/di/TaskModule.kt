package me.bgregos.foreground.di

import android.app.AlarmManager
import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import me.bgregos.foreground.data.taskfilter.TaskFilterRepository
import me.bgregos.foreground.ForegroundListWidgetUpdater
import me.bgregos.foreground.data.tasks.TaskRepository
import me.bgregos.foreground.network.RemoteTaskSource
import me.bgregos.foreground.network.RemoteTaskSourceImpl
import me.bgregos.foreground.util.NotificationRepository
import javax.inject.Singleton

@Module
abstract class TaskModule {
    companion object {

        @Singleton
        @Provides
        fun provideForegroundListWidgetUpdater(context: Context): ForegroundListWidgetUpdater {
            return ForegroundListWidgetUpdater(context)
        }

        @Singleton
        @Provides
        fun provideSharedPreferences(context: Context): SharedPreferences {
            return context.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
        }

        @Singleton
        @Provides
        fun provideTaskRepository(sharedPreferences: SharedPreferences, remoteTaskSource: RemoteTaskSource): TaskRepository {
            return TaskRepository(sharedPreferences, remoteTaskSource)
        }

        @Singleton
        @Provides
        fun provideNotificationRepository(context: Context): NotificationRepository {
            return NotificationRepository(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager, context)
        }

        @Singleton
        @Provides
        fun provideRemoteTaskSource(context: Context, sharedPreferences: SharedPreferences): RemoteTaskSource {
            return RemoteTaskSourceImpl(context.filesDir, sharedPreferences)
        }
    }
}