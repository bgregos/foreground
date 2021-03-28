package me.bgregos.foreground.data

import androidx.room.Database
import androidx.room.RoomDatabase
import me.bgregos.foreground.data.taskfilter.TaskFilterDao
import me.bgregos.foreground.data.taskfilter.TaskFilterEntity

@Database(entities = [TaskFilterEntity::class], version = 1)
abstract class ForegroundDatabase : RoomDatabase() {
    abstract fun taskFilterDao(): TaskFilterDao
}
