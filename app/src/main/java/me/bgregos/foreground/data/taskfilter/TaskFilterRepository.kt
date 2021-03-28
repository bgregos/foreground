package me.bgregos.foreground.data.taskfilter

import android.util.Log
import me.bgregos.foreground.model.TaskFilter
import me.bgregos.foreground.model.TaskFiltersAvailable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskFilterRepository @Inject constructor(private val taskFilterDao: TaskFilterDao){

    suspend fun getAll(): List<TaskFilter> {
        return taskFilterDao.getAll()
    }

    suspend fun updateAll(vararg filters: TaskFilter) {
        taskFilterDao.updateAll(*filters)
    }

    suspend fun insertAll(vararg filters: TaskFilter){
        taskFilterDao.insertAll(*filters)
    }

    suspend fun delete(filter: TaskFilter){
        taskFilterDao.delete(filter)
    }

}