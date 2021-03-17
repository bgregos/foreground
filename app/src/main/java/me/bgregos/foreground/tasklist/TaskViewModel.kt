package me.bgregos.foreground.tasklist

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import me.bgregos.foreground.model.SyncResult
import me.bgregos.foreground.model.Task
import me.bgregos.foreground.util.NotificationRepository
import me.bgregos.foreground.util.sendUpdate
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.NoSuchElementException

/**
 * This is the shared ViewModel for the task list and task detail screens.
 * Because they share information between each other in real time on
 * tablets/expanded foldables, their viewmodels are combined.
 */
class TaskViewModel @Inject constructor(private val taskRepository: TaskRepository, private val notificationRepository: NotificationRepository): ViewModel() {
    var tasks: MutableLiveData<List<Task>> = MutableLiveData(taskRepository.tasks)

    private val writeFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val displayTimeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())


    lateinit var currentUUID: UUID
        private set

    var currentTask: Task = Task("Loading...")
        set(value) {
            field = value
            tasks.sendUpdate()
        }

        get() {
            return tasks.value?.firstOrNull() { it.uuid == currentUUID } ?: throw NoSuchElementException("Could not find task to open.")
        }

    val visibleTasks: List<Task>
        get() {
            return taskRepository.visibleTasks(tasks.value ?: listOf())
        }

    init {
        writeFormat.timeZone= TimeZone.getDefault()
        displayDateFormat.timeZone= TimeZone.getDefault()
        displayTimeFormat.timeZone= TimeZone.getDefault()
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
        toComplete.status = "completed"
        toComplete.modifiedDate = Date() //update modified date
        toComplete.endDate = Date()
        tasks.value = tasks.value?.minus(toComplete)
        taskUpdated(toComplete)
    }

    fun removeUnnamedTasks() {
        tasks.value?.map {
            if (it != currentTask && it.name.isBlank()){
                tasks.value = tasks.value?.minus(it)
                taskUpdated(currentTask)
            }
        }
    }

    private fun taskUpdated(task: Task){
        if(!taskRepository.localChanges.contains(task)){
            taskRepository.localChanges.plus(task)
        }
        tasks.sendUpdate()
    }

    suspend fun sync(): SyncResult{
        save()
        removeUnnamedTasks()
        return taskRepository.taskwarriorSync()
    }

    fun setTask(uuid: UUID) {
        currentUUID = uuid
    }

    fun setTaskName(name: String) {
        currentTask.name = name
        taskUpdated(currentTask)
    }

    fun setTaskTags(enteredTags: String) {
        currentTask.tags = enteredTags.split(", ",",") as ArrayList<String>
        taskUpdated(currentTask)
    }

    fun setTaskProject(project: String) {
        currentTask.project = project
        taskUpdated(currentTask)
    }

    fun setTaskPriority(priority: String) {
        currentTask.priority = priority
        taskUpdated(currentTask)
    }

    fun setTaskDueDate(date: String) {
        currentTask.dueDate = writeFormat.parse("$date ${displayTimeFormat.format(currentTask.dueDate ?: Date())}")
        taskUpdated(currentTask)
    }

    fun setTaskDueTime(time: String) {
        currentTask.dueDate = writeFormat.parse("${displayDateFormat.format(currentTask.dueDate ?: Date())} $time")
        taskUpdated(currentTask)
    }

    fun setTaskWaitDate(date: String) {
        currentTask.waitDate = writeFormat.parse("$date ${displayTimeFormat.format(currentTask.waitDate ?: Date())}")
        taskUpdated(currentTask)
    }

    fun setTaskWaitTime(time: String){
        currentTask.waitDate = writeFormat.parse("${displayDateFormat.format(currentTask.waitDate ?: Date())} $time")
        taskUpdated(currentTask)
    }
}
