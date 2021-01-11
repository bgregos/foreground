package me.bgregos.foreground.persistence.taskfilter

import androidx.room.*

@Dao
interface TaskFilterDao {
    @Query("SELECT * FROM TaskFilterEntity")
    fun getAll(): List<TaskFilterEntity>

    @Update
    fun updateAll(vararg filters: TaskFilterEntity)

    @Insert
    fun insertAll(vararg filters: TaskFilterEntity)

    @Delete
    fun delete(filter: TaskFilterEntity)
}