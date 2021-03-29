package me.bgregos.foreground.tasklist

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import me.bgregos.foreground.data.taskfilter.TaskFilterRepository
import me.bgregos.foreground.data.tasks.TaskRepository
import me.bgregos.foreground.model.SyncResult
import me.bgregos.foreground.model.Task
import me.bgregos.foreground.util.NotificationRepository
import me.bgregos.foreground.util.replace
import me.bgregos.foreground.util.sendUpdate
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * This is the shared ViewModel for the task list and task detail screens.
 * Because they share information between each other in real time on
 * tablets/expanded foldables, their viewmodels are combined.
 */
class TaskViewModel @Inject constructor(private val taskRepository: TaskRepository, private val notificationRepository: NotificationRepository, filtersRepository: TaskFilterRepository): ViewModel() {
    var tasks: MutableStateFlow<List<Task>> = MutableStateFlow(taskRepository.tasks)
    val visibleTasks: Flow<List<Task>> =
            filtersRepository.taskFilters.combine(tasks) { filters, tasks ->
                var out: ArrayList<Task> = arrayListOf<Task>().apply { addAll(tasks) }
                filters.forEach {
                    if (it.enabled) out = out.filter { task -> it.type.filter(task, it.parameter) } as ArrayList<Task>
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
    }

    suspend fun load(){
        taskRepository.load()
        tasks.value = taskRepository.tasks
    }

    suspend fun save(){
        taskRepository.tasks = tasks.value ?: listOf()
        removeUnnamedTasks()
        notificationRepository.scheduleNotificationForTasks(tasks.value ?: listOf())
        taskRepository.save()
    }

    fun checkForTasksNoLongerWaiting(){
        tasks.value.map { task ->
            if(task.waitDate != null) {
                if (task.waitDate.before(Date()) && task.status=="waiting"){
                    val newTask = task.copy(status = "pending")
                    tasks.value = tasks.value.replace(newTask) { it === task}
                    taskUpdated(newTask)
                }
            }
        }
    }

    fun updatePendingNotifications() {
        notificationRepository.scheduleNotificationForTasks(tasks.value ?: ArrayList())
    }

    fun addTask(): Task {
        val newTask = Task("")
        tasks.value = tasks.value?.plus(newTask)
        taskUpdated(newTask)
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
        taskUpdated(completed)
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
        taskUpdated(deleted)
    }

    fun removeUnnamedTasks() {
        tasks.value.map {
            if (it != currentTask && it.name.isBlank()){
                tasks.value = tasks.value.minus(it)
            }
        }
        taskRepository.localChanges.map {
            if (it.name.isBlank()){
                taskRepository.localChanges = taskRepository.localChanges.minus(it)
            }
        }
    }

    private fun taskUpdated(task: Task?){
        if (task != null){
            val updated = task.copy(modifiedDate = Date())
            if(!taskRepository.localChanges.contains(updated)){
                taskRepository.localChanges = taskRepository.localChanges.plus(task)
            } else {
                taskRepository.localChanges = taskRepository.localChanges.replace(updated) { it === task }
            }
        }
    }

    suspend fun sync(): SyncResult{
        save()
        removeUnnamedTasks()
        val syncResult = taskRepository.taskwarriorSync()
        tasks.value = taskRepository.tasks
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
        currentTask = currentTask?.copy(name = name)
        taskUpdated(currentTask)
    }

    fun setTaskTags(enteredTags: String) {
        var tags = enteredTags.split(", ",",") as ArrayList<String>
        tags.removeAll { tag -> tag.isBlank() }
        tags = tags.map{ tag -> tag.trim() } as ArrayList<String>
        currentTask = currentTask?.copy(tags = tags)
        taskUpdated(currentTask)
    }

    fun setTaskProject(project: String) {
        currentTask = currentTask?.copy(project = project)
        taskUpdated(currentTask)
    }

    fun setTaskPriority(priority: String) {
        currentTask = currentTask?.copy(priority = priority)
        taskUpdated(currentTask)
    }

    fun setTaskDueDate(date: String, time: String) {
        currentTask = currentTask?.copy(dueDate = writeFormat.parse("$date $time") )
        taskUpdated(currentTask)
    }

    fun setTaskWaitDate(date: String, time: String) {
        currentTask = currentTask?.copy( waitDate = writeFormat.parse("$date $time") )
        taskUpdated(currentTask)
    }

    fun detailClosed() {
        currentUUID = null
    }
}
