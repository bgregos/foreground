package me.bgregos.foreground.filter

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import me.bgregos.foreground.model.TaskFilter
import javax.inject.Inject

class FiltersViewModel @Inject constructor(): ViewModel() {

    val filters: LiveData<ArrayList<TaskFilter>> = MutableLiveData(arrayListOf())

    fun addFilter(taskFilter: TaskFilter) {
        filters.value?.add(taskFilter)
    }

    fun removeFilter(taskFilter: TaskFilter) {
        filters.value?.remove(taskFilter)
    }


}