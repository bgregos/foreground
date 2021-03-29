package me.bgregos.foreground.data.tasks

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.google.gson.Gson
import java.util.*
import com.google.gson.reflect.TypeToken
import me.bgregos.foreground.model.SyncResult
import me.bgregos.foreground.model.Task
import me.bgregos.foreground.network.RemoteTaskSource
import me.bgregos.foreground.util.replace
import java.text.SimpleDateFormat
import javax.inject.Inject
import kotlin.collections.ArrayList

class TaskRepository @Inject constructor(
        private val prefs: SharedPreferences,
        private val remoteTaskSource: RemoteTaskSource) {

    var tasks: List<Task> = listOf()
    var localChanges: List<Task> = listOf()

    suspend fun taskwarriorSync(): SyncResult {
        remoteTaskSource.tasks = tasks.toMutableList()
        remoteTaskSource.localChanges = localChanges.toMutableList()
        val result = remoteTaskSource.taskwarriorSync()
        tasks = remoteTaskSource.tasks
        localChanges = remoteTaskSource.localChanges
        return result
    }

    suspend fun testSync(): SyncResult {
        return remoteTaskSource.taskwarriorInitSync()
    }

    fun resetSync() {
        remoteTaskSource.resetSync()
        localChanges = remoteTaskSource.localChanges
    }

    /**
     *  Serializes the current task list and modified task list
     *  to JSON and saves it to SharedPrefs.
     *
     *  TODO: Refactor
     *  Refactor this save/load procedure into a new data source class
     *  that persists this data to the app's Room database instead of serializing
     *  to SharedPrefs. The current implementation is fragile and difficult to debug.
     *  An example implementation of a data source is available at
     *  [me.bgregos.foreground.network.RemoteTaskSourceImpl], and
     *  [me.bgregos.foreground.data.taskfilter.TaskFilterRepository] already
     *  makes use of Room.
     */
    suspend fun save() {
        remoteTaskSource.save()
        val editor = prefs.edit()
        editor.putString("LocalTasks", Gson().toJson(tasks))
        editor.putString("LocalTasks.localChanges", Gson().toJson(localChanges))
        editor.apply()
    }

    /**
     * Fetches and deserializes the task list and modified task list
     * from SharedPrefs.
     *
     * See the refactor note on [save] about planned changes.
     */
    suspend fun load() {
        remoteTaskSource.load()
        val taskType = object : TypeToken<ArrayList<Task>>() {}.type
        tasks = Gson().fromJson(prefs.getString("LocalTasks", ""), taskType) ?: tasks
        localChanges = Gson().fromJson(prefs.getString("LocalTasks.localChanges", ""), taskType) ?: localChanges
        val lastSeenVersion = prefs.getInt("lastSeenVersion", 1)
        runMigrationsIfRequired(lastSeenVersion)
    }

    @SuppressLint("SimpleDateFormat")
    @Synchronized
    suspend fun runMigrationsIfRequired(lastSeenVersion: Int){
        //migration - breaking changes are versioned here
        var itemsModified = false
        var taskList = tasks
        if (lastSeenVersion<2){
            val editor = prefs.edit()
            itemsModified = true
            val dfLocal = SimpleDateFormat()
            dfLocal.setTimeZone(TimeZone.getDefault())
            val dfUtc = SimpleDateFormat()
            dfUtc.setTimeZone(TimeZone.getTimeZone("UTC"))
            for (task in taskList) {
                //convert all Dates from local time to GMT
                val entryDate = dfUtc.parse(dfLocal.format(task.createdDate))
                var modifiedDate: Date? = null
                var dueDate: Date? = null
                if (task.modifiedDate != null){
                    modifiedDate=dfUtc.parse(dfLocal.format(task.modifiedDate))
                }
                if (task.dueDate != null){
                    dueDate=dfUtc.parse(dfLocal.format(task.dueDate))
                }
                val newTask = task.copy(
                        modifiedDate = modifiedDate,
                        dueDate = dueDate,
                        createdDate = entryDate!!
                )
                taskList = taskList.replace(newTask) {it === task}
            }
            editor.putInt("lastSeenVersion", 2)
            editor.apply()
        }
        if (lastSeenVersion<3){
            val editor = prefs.edit()
            itemsModified = true
            for (task in taskList) {
                //convert all Dates from local time to GMT
                var newTask: Task = task
                if (task.others["wait"] != null) {
                    newTask = newTask.copy(waitDate = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'").parse(task.others["wait"]), others = newTask.others.minus("wait"))
                }

                val waitdate = task.waitDate
                if(waitdate != null && waitdate.after(Date())) {
                    newTask = newTask.copy(status = "waiting")
                }
                if (newTask !== task){
                    taskList = taskList.replace(newTask) {it === task}
                }
            }
            editor.putInt("lastSeenVersion", 3)
            editor.apply()
        }
        if(lastSeenVersion<4){
            //normalize tags
            val editor = prefs.edit()
            itemsModified = true
            taskList.forEach{
                var tags = it.tags.filter { tag -> tag.isBlank() }
                tags = tags.map{ tag -> tag.trim() }
                val newTask = it.copy(tags = tags)
                taskList = taskList.replace(newTask) { foundTask -> foundTask === it}
            }
            editor.putInt("lastSeenVersion", 4)
            editor.apply()
        }
        if(lastSeenVersion<5){
            //time normalization
            val editor = prefs.edit()
            itemsModified = true
            val now = Date()
            taskList.forEach {
                val waitDate = it.waitDate?.toLocal()
                val dueDate = it.dueDate?.toLocal()
                val modifiedDate = now
                val newTask = it.copy(waitDate = waitDate, dueDate = dueDate, modifiedDate = modifiedDate)
                taskList = taskList.replace(newTask) {foundTask -> foundTask === it}
            }
            editor.putInt("lastSeenVersion", 5)
            editor.apply()
        }
        if (itemsModified) {
            tasks = taskList
            save()
        }
    }

    fun getTaskByUUID(uuid: UUID): Task?{
        val tasklist = tasks
        for(task in tasklist){
            if(task.uuid == uuid){
                return task
            }
        }
        return null
    }

    //helper for migrations
    private fun Date.toLocal(): Date {
        val dfLocal = SimpleDateFormat()
        dfLocal.timeZone = TimeZone.getDefault()
        val dfUtc = SimpleDateFormat()
        dfUtc.timeZone = TimeZone.getTimeZone("UTC")
        return dfUtc.parse(dfLocal.format(this))
    }
}