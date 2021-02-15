package me.bgregos.foreground.tasklist

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import java.util.*
import android.util.Log
import com.google.gson.reflect.TypeToken
import me.bgregos.foreground.model.Task
import me.bgregos.foreground.model.TaskFilter
import me.bgregos.foreground.util.NonNullableMediatorLiveData
import me.bgregos.foreground.util.NonNullableMutableLiveData
import java.text.SimpleDateFormat
import javax.inject.Inject
import kotlin.collections.ArrayList

class LocalTasksRepository @Inject constructor(preferences: SharedPreferences) {
    @Volatile
    var tasks: NonNullableMutableLiveData<ArrayList<Task>> = NonNullableMutableLiveData(ArrayList())
    @Volatile
    var localChanges: NonNullableMutableLiveData<ArrayList<Task>> = NonNullableMutableLiveData(ArrayList())
    @Volatile
    var filters: NonNullableMutableLiveData<ArrayList<TaskFilter>> = NonNullableMutableLiveData(ArrayList())
    @Volatile
    var visibleTasks: NonNullableMediatorLiveData<ArrayList<Task>> = NonNullableMediatorLiveData(tasks.value)

    var showWaiting:Boolean = false
    var initSync:Boolean = true

    val prefs: SharedPreferences = preferences

    @Volatile
    var syncKey:String = ""

    fun init() {
        visibleTasks.addSource(tasks) {
            visibleTasks.value = updateVisibleTasks(it)
        }
        visibleTasks.value = updateVisibleTasks(tasks.value)
    }


    @Synchronized
    fun save(synchronous: Boolean = false) {
        val editor = prefs.edit()
        editor.putString("LocalTasks", Gson().toJson(tasks.value))
        editor.putString("LocalTasks.localChanges", Gson().toJson(localChanges.value))
        editor.putString("LocalTasks.initSync", initSync.toString())
        editor.putString("LocalTasks.syncKey", syncKey)
        editor.putString("LocalTasks.showWaiting", showWaiting.toString())
        if(synchronous) editor.apply() else editor.commit()
    }

    @Synchronized
    fun load(synchronous: Boolean = false) {
        val taskType = object : TypeToken<ArrayList<Task>>() {}.type
        Log.i("LocalTasks", prefs.getString("LocalTasks", "") ?: "")
        tasks.value = Gson().fromJson(prefs.getString("LocalTasks", ""), taskType) ?: tasks.value
        localChanges.value = Gson().fromJson(prefs.getString("LocalTasks.localChanges", ""), taskType) ?: localChanges.value
        initSync =  prefs.getString("LocalTasks.initSync", "true")?.toBoolean()?:true
        showWaiting =  prefs.getString("LocalTasks.showWaiting", "true")?.toBoolean()?:false
        syncKey = prefs.getString("LocalTasks.syncKey", syncKey)?: syncKey
        val lastSeenVersion = prefs.getInt("lastSeenVersion", 1)
        runMigrationsIfRequired(lastSeenVersion, synchronous)
        init()
    }

    @Synchronized
    fun runMigrationsIfRequired(lastSeenVersion: Int, synchronous: Boolean){
        //migration - breaking changes are versioned here
        var itemsModified = false
        if (lastSeenVersion<2){
            val editor = prefs.edit()
            itemsModified = true
            val dfLocal = SimpleDateFormat()
            dfLocal.setTimeZone(TimeZone.getDefault())
            val dfUtc = SimpleDateFormat()
            dfUtc.setTimeZone(TimeZone.getTimeZone("UTC"))
            for (i in tasks.value) {
                //convert all Dates from local time to GMT
                if (i.createdDate != null){
                    i.createdDate=dfUtc.parse(dfLocal.format(i.createdDate))
                }
                if (i.modifiedDate != null){
                    i.modifiedDate=dfUtc.parse(dfLocal.format(i.modifiedDate))
                }
                if (i.dueDate != null){
                    i.dueDate=dfUtc.parse(dfLocal.format(i.dueDate))
                }
            }
            editor.putInt("lastSeenVersion", 2)
            if(synchronous) editor.apply() else editor.commit()
        }
        if (lastSeenVersion<3){
            val editor = prefs.edit()
            itemsModified = true
            for (i in tasks.value) {
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
            if(synchronous) editor.apply() else editor.commit()
        }
        if(lastSeenVersion<4){
            //normalize tags
            val editor = prefs.edit()
            itemsModified = true
            tasks.value.forEach{
                it.tags.removeAll { tag -> tag.isBlank() }
                it.tags = it.tags.map{ tag -> tag.trim() } as ArrayList<String>
            }
            editor.putInt("lastSeenVersion", 4)
            if(synchronous) editor.apply() else editor.commit()
        }
        if(lastSeenVersion<5){
            //time normalization
            val editor = prefs.edit()
            itemsModified = true
            val now = Date()
            tasks.value.forEach {
                it.waitDate = it.waitDate?.toLocal()
                it.dueDate = it.dueDate?.toLocal()
                it.modifiedDate = now
            }
            editor.putInt("lastSeenVersion", 5)
            if(synchronous) editor.apply() else editor.commit()
        }
        if (itemsModified) {
            save(synchronous)
        }
    }

    @Synchronized
    fun updateVisibleTasks(tasks: ArrayList<Task>): ArrayList<Task>{
        var out: ArrayList<Task> = arrayListOf<Task>().apply { addAll(tasks) }
        filters.value.forEach {
            if (it.enabled) out = out.filter { task -> it.type.filter(task, it.parameter) } as ArrayList<Task>
        }
        //visibleTasks = visibleTasks.filter { it.name.isNotBlank() } as ArrayList<Task>
        out.sortWith(Task.DateCompare())
        return out
    }

    fun getTaskByUUID(uuid: UUID): Task?{
        for(task in tasks.value){
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