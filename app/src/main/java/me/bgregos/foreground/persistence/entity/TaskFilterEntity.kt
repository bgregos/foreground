package me.bgregos.foreground.persistence.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import me.bgregos.foreground.model.TaskFilterType

@Entity
data class TaskFilterEntity(
        @PrimaryKey(autoGenerate = true) val id: Int,
        val type: String,
        val parameter: String?,
        val 
)