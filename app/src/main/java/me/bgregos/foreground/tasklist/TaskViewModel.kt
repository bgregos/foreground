package me.bgregos.foreground.tasklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.bgregos.foreground.data.taskfilter.TaskFilterRepository
import me.bgregos.foreground.data.tasks.TaskRepository
import me.bgregos.foreground.model.SyncResult
import me.bgregos.foreground.model.Task
import me.bgregos.foreground.util.NotificationRepository
import me.bgregos.foreground.util.replace
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * This is the shared ViewModel for the task list and task detail screens.
 * Because they share information between each other in real time on
 * tablets/expanded foldables, their viewmodels are combined.
 */
class TaskViewModel @Inject constructor(private val taskRepository: TaskRepository, private val notificationRepository: NotificationRepository, filtersRepository: TaskFilterRepository): ViewModel() {
    var tasks: MutableStateFlow<List<Task>> = taskRepository.tasks
    val visibleTasks: Flow<List<Task>> =
            filtersRepository.taskFilters.combine(tasks) { filters, tasks ->
                var out: ArrayList<Task> = arrayListOf<Task>().apply { addAll(tasks) }
                filters.forEach {
                        if (it.enabled) out = out.filter { task ->
                            var filterResult = it.type.filter(task, it.parameter)
                        if (!it.includeMatching) filterResult = !filterResult
                        filterResult
                    } as ArrayList<Task>
                }
                //visibleTasks = visibleTasks.filter { it.name.isNotBlank() } as ArrayList<Task>
                out.sortWith(Task.DateCompare())
                out.toList()
            }

    //The detail fragment will listen to this and close when it receives an emission
    val closeDetailChannel: Channel<Unit> = Channel(Channel.RENDEZVOUS)

    private val writeFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())

    var currentUUID: UUID? = null
        private set

    var currentTask: Task? = null
        set(value) {
            field = value
            currentUUID = value?.uuid
        }
        get() {
            if (currentUUID == null){
                return null
            }
            return tasks.value?.firstOrNull() { it.uuid == currentUUID }
        }

    init {
        writeFormat.timeZone= TimeZone.getDefault()
        notificationRepository.load()
        notificationRepository.createNotificationChannel()
        viewModelScope.launch(Dispatchers.IO) {
            load()
        }
    }

    suspend fun load(){
        taskRepository.load()
        tasks.value = taskRepository.tasks.value
    }

    suspend fun save(){
        removeUnnamedTasks()
        taskRepository.save()
    }

    fun checkForTasksNoLongerWaiting(){
        tasks.value.map { task ->
            if(task.waitDate != null) {
                if (task.waitDate.before(Date()) && task.status=="waiting"){
                    val newTask = task.copy(status = "pending")
                    tasks.value = tasks.value.replace(newTask) { it === task}
                    postUpdatedTask(newTask)
                }
            }
        }
    }

    fun updatePendingNotifications() {
        notificationRepository.scheduleNotificationForTasks(tasks.value ?: ArrayList())
    }

    fun addTask(): Task {
        val newTask = Task("")
        tasks.value = tasks.value.plus(newTask)
        postUpdatedTask(newTask)
        return newTask
    }

    fun markTaskComplete(toComplete: Task) {
        if(toComplete == currentTask){
            closeDetailChannel.offer(Unit)
        }
        val completed = toComplete.copy(
                status = "completed",
                modifiedDate = Date(),
                endDate = Date()
        )
        tasks.value = tasks.value?.minus(toComplete)
        postUpdatedTask(completed)
        viewModelScope.launch {
            save()
        }
    }

    fun delete(toDelete: Task) {
        if(toDelete == currentTask){
            closeDetailChannel.offer(Unit)
            currentTask = null
        }
        val deleted = toDelete.copy(
                status = "deleted",
                modifiedDate = Date(),
                endDate = Date()
        )
        tasks.value = tasks.value?.minus(toDelete)
        postUpdatedTask(deleted)
        viewModelScope.launch {
            save()
        }
    }

    fun removeUnnamedTasks() {
        tasks.value.map {
            if (it != currentTask && it.name.isBlank()){
                tasks.value = tasks.value.minus(it)
            }
        }
        taskRepository.localChanges.value.map {
            if (it.name.isBlank()){
                taskRepository.localChanges.value = taskRepository.localChanges.value.minus(it)
            }
        }
    }

    private fun postUpdatedTask(task: Task) {
        val updated = task.copy(modifiedDate = Date())
        taskRepository.addToLocalChanges(updated)
        tasks.value = tasks.value.replace(updated) { it.uuid == task.uuid }
        checkForTasksNoLongerWaiting()
        updatePendingNotifications()
    }

    suspend fun sync(): SyncResult{
        save()
        removeUnnamedTasks()
        val syncResult = taskRepository.taskwarriorSync()
        if(tasks.value?.contains(currentTask) != true) {
            //close the detail fragment
            closeDetailChannel.offer(Unit)
        }
        return syncResult
    }

    fun setTask(uuid: UUID) {
        currentUUID = uuid
    }

    fun setTaskName(name: String) {
        currentTask?.let {
            postUpdatedTask(it.copy(name = name))
        }
    }

    fun setTaskTags(enteredTags: String) {
        var tags = enteredTags.split(", ",",") as ArrayList<String>
        tags.removeAll { tag -> tag.isBlank() }
        tags = tags.map{ tag -> tag.trim() } as ArrayList<String>
        currentTask?.let {
            postUpdatedTask(it.copy(tags = tags))
        }
    }

    fun setTaskProject(project: String) {
        currentTask?.let {
            postUpdatedTask(it.copy(project = project))
        }
    }

    fun setTaskPriority(priority: String) {
        currentTask?.let {
            postUpdatedTask(it.copy(priority = priority))
        }
    }

    fun setTaskDueDate(date: String, time: String) {
        currentTask?.let {
            postUpdatedTask(it.copy(dueDate = writeFormat.parse("$date $time")))
        }
    }

    fun setTaskWaitDate(date: String, time: String) {
        currentTask?.let {
            postUpdatedTask(it.copy(waitDate = writeFormat.parse("$date $time")))
        }
    }

    fun detailClosed() {
        currentUUID = null
    }
}
