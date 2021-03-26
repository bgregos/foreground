package me.bgregos.foreground.filter

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.bgregos.foreground.model.TaskFilter
import me.bgregos.foreground.util.replace
import javax.inject.Inject

class FiltersViewModel @Inject constructor(): ViewModel() {

    val filters: MutableStateFlow<List<TaskFilter>> = MutableStateFlow(listOf())

    fun addFilter(taskFilter: TaskFilter) {
        filters.value += taskFilter
    }

    fun removeFilter(taskFilter: TaskFilter) {
        filters.value -= taskFilter
    }

    fun toggleFilterEnable(taskFilter: TaskFilter) {
        val newFilter = taskFilter.copy(enabled = !taskFilter.enabled)
        filters.value = filters.value.replace(newFilter) {it == taskFilter}
    }

    fun generateFriendlyString(filter: TaskFilter): String {
        return "${if (filter.filterMatching) "Exclude" else "Include"} tasks with ${filter.type.name} \"${filter.parameter}\""
    }


}