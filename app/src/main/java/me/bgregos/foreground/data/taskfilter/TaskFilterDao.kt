package me.bgregos.foreground.data.taskfilter

import androidx.room.*

@Dao
interface TaskFilterDao {
    @Query("SELECT * FROM TaskFilterEntity")
    suspend fun getAll(): List<TaskFilterEntity>

    @Update
    suspend fun updateAll(vararg filters: TaskFilterEntity)

    @Insert
    suspend fun insertAll(vararg filters: TaskFilterEntity)

    @Delete
    suspend fun delete(filter: TaskFilterEntity)
}