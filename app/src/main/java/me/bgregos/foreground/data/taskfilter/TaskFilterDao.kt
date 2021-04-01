package me.bgregos.foreground.data.taskfilter

import androidx.room.*
import me.bgregos.foreground.model.TaskFilter

@Dao
interface TaskFilterDao {
    @Query("SELECT * FROM TaskFilter")
    suspend fun getAll(): List<TaskFilter>

    @Update
    suspend fun updateAll(vararg filters: TaskFilter)

    @Insert
    suspend fun insertAll(vararg filters: TaskFilter)

    @Delete
    suspend fun delete(filter: TaskFilter)
}