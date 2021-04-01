package me.bgregos.foreground.data

import androidx.room.TypeConverter
import me.bgregos.foreground.model.TaskFilterType
import me.bgregos.foreground.model.TaskFiltersAvailable

class Converters {
    @TypeConverter
    fun fromTaskFilterType(taskFilterType: TaskFilterType): String {
        return taskFilterType.id
    }

    @TypeConverter
    fun toTaskFilterType(taskFilterTypeId: String): TaskFilterType {
        return TaskFiltersAvailable.filters.first { it.id == taskFilterTypeId }
    }
}
