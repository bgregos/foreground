package me.bgregos.foreground.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import me.bgregos.foreground.persistence.taskfilter.TaskFilterDao
import me.bgregos.foreground.persistence.taskfilter.TaskFilterEntity

@Database(entities = [TaskFilterEntity::class], version = 1)
abstract class ForegroundDatabase : RoomDatabase() {
    abstract fun taskFilterDao(): TaskFilterDao
}
