package me.bgregos.foreground.filter

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import me.bgregos.foreground.data.taskfilter.TaskFilterRepository
import me.bgregos.foreground.model.TaskFilter
import me.bgregos.foreground.util.replace
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FiltersViewModel @Inject constructor(
        private val repository: TaskFilterRepository
): ViewModel() {

    val filters: MutableStateFlow<List<TaskFilter>> = MutableStateFlow(listOf())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            filters.value = repository.getAll()
        }
    }

    fun addFilter(taskFilter: TaskFilter): Boolean {
        if (filters.value.contains(taskFilter)){
            return false
        }
        filters.value += taskFilter
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertAll(taskFilter)
        }
        return true
    }

    fun removeFilter(taskFilter: TaskFilter) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(taskFilter)
        }
        filters.value -= taskFilter
    }

    fun toggleFilterEnable(taskFilter: TaskFilter) {
        val newFilter = taskFilter.copy(enabled = !taskFilter.enabled)
        filters.value = filters.value.replace(newFilter) {it == taskFilter}
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateAll(newFilter)
        }
    }

    fun generateFriendlyString(filter: TaskFilter): String {
        return "${if (filter.filterMatching) "Exclude" else "Include"} tasks with ${filter.type.name} \"${filter.parameter}\""
    }




}