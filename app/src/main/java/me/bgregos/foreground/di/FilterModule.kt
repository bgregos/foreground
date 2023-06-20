package me.bgregos.foreground.di

import dagger.Module
import dagger.Provides
import me.bgregos.foreground.data.ForegroundDatabase
import me.bgregos.foreground.data.taskfilter.TaskFilterDao

@Module
abstract class FilterModule {
    companion object {

        @Provides
        fun provideTaskFilterDao(database: ForegroundDatabase): TaskFilterDao {
            return database.taskFilterDao()
        }

    }
}