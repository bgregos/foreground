package me.bgregos.foreground.model

import java.util.*

/**
 * Represents a filter for Tasks. These filters can be instantiated and chained together by the user
 *
 * In the examples below, the user wants to filter to tasks that have "renovation" as the project field.
 *
 * @property type type of filter. ex: PROJECT
 * @property parameter optional user-supplied parameter. ex: "renovation"
 * @property enabled most filters should default to true, which is on by default since they are usually created explicitly by the user
 * @property invert filter out tasks that do match this filter, instead of those that do not.
 */
data class TaskFilter (
        val type: TaskFilterTypes,
        var parameter: String?,
        var enabled: Boolean = true,
        var invert: Boolean = false
)

/**
 * Represents an available type of filter. These are fixed ahead of time. The user
 * can select one of these types when creating a [TaskFilter]
 * @property name user-friendly name
 * @property id name used for persistence
 * @property requiresParameter whether this type of filter requires a parameter. A simple enable/disable filter would not.
 * @property filter how to apply this to a collection. ex { task: Task, param: String? -> task.project == param }
 */
data class TaskFilterType (
        val name: String,
        val id: String,
        val requiresParameter: Boolean,
        val filter: (Task, String?) -> Boolean
)

/**
 * Available filter types for the user to choose from
 */
enum class TaskFilterTypes (val taskFilterType: TaskFilterType) {
    PROJECT (TaskFilterType("Project", "project", true, filter = { task: Task, param: String? -> task.project == param })),
    TAG (TaskFilterType("Tag", "tag", true, filter = { task: Task, param: String? -> task.tags.contains(param) })),
    PRIORITY (TaskFilterType("Priority", "priority", true, filter = { task: Task, param: String? -> task.priority == param })),
    WAITING (TaskFilterType("Waiting", "waiting", false, filter = { task: Task, _ -> Date().before(task.waitDate) }))
}

