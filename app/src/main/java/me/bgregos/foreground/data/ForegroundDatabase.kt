package me.bgregos.foreground.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import me.bgregos.foreground.data.taskfilter.TaskFilterDao
import me.bgregos.foreground.model.TaskFilter

@Database(entities = [TaskFilter::class], version = 1)
@TypeConverters(Converters::class)
abstract class ForegroundDatabase : RoomDatabase() {
    abstract fun taskFilterDao(): TaskFilterDao
}
