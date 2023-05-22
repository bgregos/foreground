package me.bgregos.foreground.di

import android.app.AlarmManager
import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import me.bgregos.foreground.data.taskfilter.TaskFilterRepository
import me.bgregos.foreground.data.tasks.TaskFileStorage
import me.bgregos.foreground.network.RemoteTaskSource
import me.bgregos.foreground.network.RemoteTaskSourceImpl
import me.bgregos.foreground.data.tasks.TaskRepository
import me.bgregos.foreground.util.NotificationRepository
import java.io.File
import javax.inject.Named
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
        fun provideTaskRepository(
            sharedPreferences: SharedPreferences,
            remoteTaskSource: RemoteTaskSource,
            taskFileStorage: TaskFileStorage
        ): TaskRepository {
            return TaskRepository(sharedPreferences, remoteTaskSource, taskFileStorage)
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

        @Singleton
        @Provides
        fun provideTaskFileStorage(
            @Named("InternalFiles") internalFiles: File,
            @Named("ExternalFiles") externalFiles: File?
        ): TaskFileStorage {
            return TaskFileStorage(internalFiles, externalFiles)
        }

        @Singleton
        @Provides
        @Named("InternalFiles")
        fun provideInternalFileDir(context: Context): File =
            context.filesDir

        @Singleton
        @Provides
        @Named("ExternalFiles")
        fun provideExternalFilesDir(context: Context): File? =
            context.getExternalFilesDir(null)
    }
}