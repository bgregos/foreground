package me.bgregos.foreground.data.taskfilter

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import me.bgregos.foreground.model.TaskFilter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskFilterRepository @Inject constructor(private val taskFilterDao: TaskFilterDao){

    init{
        MainScope().launch {
            taskFilters.value = getAll()
        }
    }

    val taskFilters: MutableStateFlow<List<TaskFilter>> = MutableStateFlow(listOf())

    suspend fun getAll(): List<TaskFilter> {
        val filters = taskFilterDao.getAll()
        taskFilters.value = filters
        return filters
    }

    suspend fun insertAll(vararg filters: TaskFilter){
        taskFilterDao.insertAll(*filters)
        getAll()
    }

    suspend fun replace(new: TaskFilter){
        taskFilterDao.updateAll(new)
        getAll()
    }

    suspend fun delete(filter: TaskFilter){
        taskFilterDao.delete(filter)
        getAll()
    }

}