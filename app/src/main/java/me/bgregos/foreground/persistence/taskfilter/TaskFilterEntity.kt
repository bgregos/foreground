package me.bgregos.foreground.persistence.taskfilter

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class TaskFilterEntity(
        @PrimaryKey(autoGenerate = true) val id: Int,
        val type: String,
        val parameter: String?,
        val enabled: Boolean,
        val filterMatching: Boolean
)