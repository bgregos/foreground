package me.bgregos.foreground.data.taskfilter

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class TaskFilterEntity(
        @PrimaryKey(autoGenerate = true) val id: Int,
        val type: String,
        val parameter: String?,
        val enabled: Boolean,
        val filterMatching: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (other is TaskFilterEntity) {
            return this.filterMatching == other.filterMatching &&
                    this.type == other.type &&
                    this.parameter == other.parameter
        }
        return false
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + (parameter?.hashCode() ?: 0)
        result = 31 * result + filterMatching.hashCode()
        return result
    }
}