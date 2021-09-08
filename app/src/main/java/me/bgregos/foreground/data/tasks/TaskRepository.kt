package me.bgregos.foreground.data.tasks

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import me.bgregos.foreground.model.SyncResult
import me.bgregos.foreground.model.Task
import me.bgregos.foreground.network.RemoteTaskSource
import me.bgregos.foreground.util.replace
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.ArrayList

@Singleton
class TaskRepository @Inject constructor(
        private val prefs: SharedPreferences,
        private val remoteTaskSource: RemoteTaskSource) {

    var tasks: MutableStateFlow<List<Task>> = MutableStateFlow(listOf())
    val localChanges: MutableStateFlow<List<Task>> = MutableStateFlow(listOf())

    suspend fun taskwarriorSync(): SyncResult {
        remoteTaskSource.tasks = tasks.value.toMutableList()
        remoteTaskSource.localChanges = localChanges.value.toMutableList()
        val result = remoteTaskSource.taskwarriorSync()
        tasks.value = remoteTaskSource.tasks
        localChanges.value = remoteTaskSource.localChanges
        return result
    }

    suspend fun testSync(): SyncResult {
        return remoteTaskSource.taskwarriorInitSync()
    }

    suspend fun resetSync(){
        localChanges.value = listOf()
        remoteTaskSource.resetSync()
        save()
    }

    suspend fun disableSync() {
        remoteTaskSource.disableSync()
    }

    fun addToLocalChanges(updated: Task) {
        if(localChanges.value.firstOrNull{ it.uuid == updated.uuid } == null){
            localChanges.value = localChanges.value.plus(updated)
        } else {
            localChanges.value = localChanges.value.replace(updated) { it.uuid == updated.uuid }
        }
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
        withContext(Dispatchers.IO) {
            remoteTaskSource.save()
            val editor = prefs.edit()
            editor.putString("LocalTasks", Gson().toJson(tasks.value))
            editor.putString("LocalTasks.localChanges", Gson().toJson(localChanges.value))
            editor.apply()
        }
    }

    /**
     * Fetches and deserializes the task list and modified task list
     * from SharedPrefs.
     *
     * See the refactor note on [save] about planned changes.
     */
    suspend fun load() {
        withContext(Dispatchers.IO) {
            remoteTaskSource.load()
            val taskType = object : TypeToken<ArrayList<Task>>() {}.type
            tasks.value = Gson().fromJson(prefs.getString("LocalTasks", ""), taskType) ?: tasks.value
            localChanges.value = Gson().fromJson(prefs.getString("LocalTasks.localChanges", ""), taskType) ?: localChanges.value
            val lastSeenVersion = prefs.getInt("lastSeenVersion", 6)
            runMigrationsIfRequired(lastSeenVersion)
        }
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
            for (task in taskList.value) {
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
                taskList.value = taskList.value.replace(newTask) {it === task}
            }
            editor.putInt("lastSeenVersion", 2)
            editor.apply()
        }
        if (lastSeenVersion<3){
            val editor = prefs.edit()
            itemsModified = true
            for (task in taskList.value) {
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
                    taskList.value = taskList.value.replace(newTask) {it === task}
                }
            }
            editor.putInt("lastSeenVersion", 3)
            editor.apply()
        }
        if(lastSeenVersion<4){
            //normalize tags
            val editor = prefs.edit()
            itemsModified = true
            taskList.value.forEach{
                var tags = it.tags.filter { tag -> tag.isBlank() }
                tags = tags.map{ tag -> tag.trim() }
                val newTask = it.copy(tags = tags)
                taskList.value = taskList.value.replace(newTask) { foundTask -> foundTask === it}
            }
            editor.putInt("lastSeenVersion", 4)
            editor.apply()
        }
        if(lastSeenVersion<5){
            //time normalization
            val editor = prefs.edit()
            itemsModified = true
            val now = Date()
            taskList.value.forEach {
                val waitDate = it.waitDate?.toLocal()
                val dueDate = it.dueDate?.toLocal()
                val modifiedDate = now
                val newTask = it.copy(waitDate = waitDate, dueDate = dueDate, modifiedDate = modifiedDate)
                taskList.value = taskList.value.replace(newTask) {foundTask -> foundTask === it}
            }
            editor.putInt("lastSeenVersion", 5)
            editor.apply()
        }
        if(lastSeenVersion<6){
            val timeFormatter = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'")
            timeFormatter.timeZone = TimeZone.getTimeZone("UTC")
            //annotations broken out into different field
            val editor = prefs.edit()
            itemsModified = true
            val now = Date()
            taskList.value.forEach {
                if(it.annotations == null){
                    val newTask = it.copy(annotations = listOf())
                    taskList.value = taskList.value.replace(newTask) {foundTask -> foundTask === it}
                }
            }
            taskList.value.forEach { task ->
                val annotations = arrayListOf<me.bgregos.foreground.model.Annotation>()

                val jsonannotations = JSONArray(task.others[annotations] ?: "")

                for (j in 0 until jsonannotations.length()) {
                    val obj = jsonannotations.getJSONObject(j)
                    val entry = obj.getString("entry")
                    val parsedEntry = timeFormatter.parse(entry)
                    val description = obj.getString("description")
                    annotations.add(me.bgregos.foreground.model.Annotation(description, parsedEntry))
                }
                val newTask = task.copy(annotations = listOf(), others = task.others.filterKeys { it != "annotations" })
                taskList.value = taskList.value.replace(newTask) {foundTask -> foundTask === task}
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
        for(task in tasklist.value){
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