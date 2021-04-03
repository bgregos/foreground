package me.bgregos.foreground.filter

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.bgregos.foreground.data.taskfilter.TaskFilterRepository
import me.bgregos.foreground.model.TaskFilter
import me.bgregos.foreground.util.replace
import javax.inject.Inject
import javax.inject.Singleton

class FiltersViewModel @Inject constructor(
        private val repository: TaskFilterRepository
): ViewModel() {

    val filters: MutableStateFlow<List<TaskFilter>> = repository.taskFilters

    fun addFilter(taskFilter: TaskFilter): Boolean {
        if (filters.value.contains(taskFilter)){
            return false
        }
        viewModelScope.launch {
            repository.insertAll(taskFilter)
        }
        return true
    }

    fun removeFilter(taskFilter: TaskFilter) {
        viewModelScope.launch {
            repository.delete(taskFilter)
        }
    }

    fun toggleFilterEnable(taskFilter: TaskFilter) {
        val newFilter = taskFilter.copy(enabled = !taskFilter.enabled)
        viewModelScope.launch {
            repository.delete(taskFilter)
            repository.insertAll(newFilter)
        }
    }

    fun generateFriendlyString(filter: TaskFilter): String {
        val includeExclude = if (filter.filterMatching) "Exclude" else "Include"
        val filterType = if (filter.parameter.isNullOrBlank()) "tasks that are ${filter.type.name}" else "tasks containing ${filter.type.name}"
        val parameter = if (filter.parameter.isNullOrBlank()) "" else " \"${filter.parameter}\""
        return "$includeExclude $filterType$parameter"
    }

}