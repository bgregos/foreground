package me.bgregos.foreground.di

import android.app.AlarmManager
import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import me.bgregos.foreground.network.RemoteTasksRepository
import me.bgregos.foreground.tasklist.LocalTasksRepository
import me.bgregos.foreground.util.NotificationRepository
import javax.inject.Singleton

@Module
abstract class TaskModule {
    companion object {

        @Singleton
        @Provides
        fun provideSharedPreferences(context: Context): SharedPreferences {
            return context.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
        }

        @Singleton
        @Provides
        fun provideLocalTasksRepository(sharedPreferences: SharedPreferences): LocalTasksRepository {
            return LocalTasksRepository(sharedPreferences)
        }

        @Singleton
        @Provides
        fun provideNotificationRepository(context: Context): NotificationRepository {
            return NotificationRepository(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager, context)
        }

        @Singleton
        @Provides
        fun provideRemoteTasksRepository(context: Context, notificationRepository: NotificationRepository, localTasksRepository: LocalTasksRepository, sharedPreferences: SharedPreferences): RemoteTasksRepository {
            return RemoteTasksRepository(context.filesDir, notificationRepository, localTasksRepository, sharedPreferences)
        }
    }
}