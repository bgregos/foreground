package me.bgregos.foreground.data.tasks

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.google.gson.Gson
import java.util.*
import com.google.gson.reflect.TypeToken
import me.bgregos.foreground.model.SyncResult
import me.bgregos.foreground.model.Task
import me.bgregos.foreground.model.TaskFilter
import me.bgregos.foreground.network.RemoteTaskSource
import java.text.SimpleDateFormat
import javax.inject.Inject
import kotlin.collections.ArrayList

class TaskRepository @Inject constructor(val prefs: SharedPreferences, val remoteTaskSource: RemoteTaskSource) {
    var tasks: List<Task> = listOf()
    var localChanges: List<Task> = listOf()
    var filters: List<TaskFilter> = listOf()

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

    suspend fun save() {
        remoteTaskSource.save()
        val editor = prefs.edit()
        editor.putString("LocalTasks", Gson().toJson(tasks))
        editor.putString("LocalTasks.localChanges", Gson().toJson(localChanges))
        editor.apply()
    }

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
        val taskList = tasks
        if (lastSeenVersion<2){
            val editor = prefs.edit()
            itemsModified = true
            val dfLocal = SimpleDateFormat()
            dfLocal.setTimeZone(TimeZone.getDefault())
            val dfUtc = SimpleDateFormat()
            dfUtc.setTimeZone(TimeZone.getTimeZone("UTC"))
            for (i in taskList) {
                //convert all Dates from local time to GMT
                i.createdDate=dfUtc.parse(dfLocal.format(i.createdDate))
                if (i.modifiedDate != null){
                    i.modifiedDate=dfUtc.parse(dfLocal.format(i.modifiedDate))
                }
                if (i.dueDate != null){
                    i.dueDate=dfUtc.parse(dfLocal.format(i.dueDate))
                }
            }
            editor.putInt("lastSeenVersion", 2)
            editor.apply()
        }
        if (lastSeenVersion<3){
            val editor = prefs.edit()
            itemsModified = true
            for (i in taskList) {
                //convert all Dates from local time to GMT
                if (i.others["wait"] != null) {
                    i.waitDate = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'").parse(i.others["wait"])
                    i.others.remove("wait")
                }

                val waitdate = i.waitDate
                if(waitdate != null && waitdate.after(Date())) {
                    i.status ="waiting"
                }
            }
            editor.putInt("lastSeenVersion", 3)
            editor.apply()
        }
        if(lastSeenVersion<4){
            //normalize tags
            val editor = prefs.edit()
            itemsModified = true
            taskList?.forEach{
                it.tags.removeAll { tag -> tag.isBlank() }
                it.tags = it.tags.map{ tag -> tag.trim() } as ArrayList<String>
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
                it.waitDate = it.waitDate?.toLocal()
                it.dueDate = it.dueDate?.toLocal()
                it.modifiedDate = now
            }
            editor.putInt("lastSeenVersion", 5)
            editor.apply()
        }
        if (itemsModified) {
            tasks = taskList
            save()
        }
    }

    fun visibleTasks(tasks: List<Task>): ArrayList<Task>{
        var out: ArrayList<Task> = arrayListOf<Task>().apply { addAll(tasks) }
        filters.forEach {
            if (it.enabled) out = out.filter { task -> it.type.filter(task, it.parameter) } as ArrayList<Task>
        }
        //visibleTasks = visibleTasks.filter { it.name.isNotBlank() } as ArrayList<Task>
        out.sortWith(Task.DateCompare())
        return out
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