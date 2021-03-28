package me.bgregos.foreground.data.taskfilter

import android.util.Log
import me.bgregos.foreground.model.TaskFilter
import me.bgregos.foreground.model.TaskFiltersAvailable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskFilterRepository @Inject constructor(private val taskFilterDao: TaskFilterDao){

    suspend fun getAll(): List<TaskFilter> {
        return taskFilterDao.getAll().map {
            it.toTaskFilter()
        }
    }

    suspend fun updateAll(vararg filters: TaskFilter) {
        taskFilterDao.updateAll(*filters.map { it.toEntity() }.toTypedArray())
    }

    suspend fun insertAll(vararg filters: TaskFilter){
        taskFilterDao.insertAll(*filters.map { it.toEntity() }.toTypedArray())
    }

    suspend fun delete(filter: TaskFilter){
        Log.e("debug", getAll().toString())
        taskFilterDao.delete(filter.toEntity())
    }

    private fun TaskFilter.toEntity(): TaskFilterEntity =
            TaskFilterEntity(
                0,
                    this.type.id,
                    this.parameter,
                    this.enabled,
                    this.filterMatching
            )

    private fun TaskFilterEntity.toTaskFilter(): TaskFilter =
            TaskFilter(
                    TaskFiltersAvailable.filters.first { it.id == this.type },
                    this.parameter,
                    this.enabled,
                    this.filterMatching
            )


}