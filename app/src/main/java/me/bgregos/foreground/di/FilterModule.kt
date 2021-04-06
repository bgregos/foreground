package me.bgregos.foreground.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Database
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bgregos.foreground.data.ForegroundDatabase
import me.bgregos.foreground.data.taskfilter.TaskFilterDao
import javax.inject.Singleton

@Module
abstract class FilterModule {
    companion object {

        @Provides
        fun provideTaskFilterDao(database: ForegroundDatabase): TaskFilterDao {
            return database.taskFilterDao()
        }

    }
}