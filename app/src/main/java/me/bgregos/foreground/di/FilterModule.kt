package me.bgregos.foreground.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import me.bgregos.foreground.data.ForegroundDatabase
import me.bgregos.foreground.data.taskfilter.TaskFilterDao
import javax.inject.Singleton

@Module
abstract class FilterModule {
    companion object {

        @Singleton
        @Provides
        fun provideForegroundDatabase(context: Context): ForegroundDatabase {
            return Room.databaseBuilder(
                    context,
                    ForegroundDatabase::class.java, "ForegroundDatabase"
            ).build()
        }

        @Provides
        fun provideTaskFilterDao(database: ForegroundDatabase): TaskFilterDao {
            return database.taskFilterDao()
        }

    }
}