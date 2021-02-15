package me.bgregos.foreground.di

import android.content.Context
import dagger.Module
import dagger.Provides
import me.bgregos.foreground.tasklist.LocalTasksRepository
import javax.inject.Singleton

@Module
abstract class TaskModule {
    companion object {
        @Singleton
        @Provides
        fun provideLocalTasksRepository(context: Context): LocalTasksRepository {
            return LocalTasksRepository(context.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE))
        }
    }
}