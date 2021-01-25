package me.bgregos.foreground.tasklist

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import java.util.*
import com.google.gson.reflect.TypeToken
import me.bgregos.foreground.model.Task
import me.bgregos.foreground.model.TaskFilter
import me.bgregos.foreground.model.TaskFilterTypes
import java.text.SimpleDateFormat
import kotlin.collections.ArrayList

object LocalTasks {
    @Volatile
    var items:ArrayList<Task> = ArrayList()
    @Volatile
    var visibleTasks:ArrayList<Task> = ArrayList()
    @Volatile
    var localChanges:ArrayList<Task> = ArrayList()
    @Volatile
    var filters:ArrayList<TaskFilter> = ArrayList()

    var showWaiting:Boolean = false
    var initSync:Boolean = true

    @Volatile
    var syncKey:String = ""


    @Synchronized
    fun save(context: Context, synchronous: Boolean = false) {
        val prefs = context.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("LocalTasks", Gson().toJson(items))
        editor.putString("LocalTasks.localChanges", Gson().toJson(localChanges))
        editor.putString("LocalTasks.initSync", initSync.toString())
        editor.putString("LocalTasks.syncKey", syncKey)
        editor.putString("LocalTasks.showWaiting", showWaiting.toString())
        if(synchronous) editor.apply() else editor.commit()
        updateVisibleTasks()
    }

    @Synchronized
    fun load(context: Context, synchronous: Boolean = false) {
        val prefs = context.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
        val taskType = object : TypeToken<ArrayList<Task>>() {}.type
        items = Gson().fromJson(prefs.getString("LocalTasks", ""), taskType) ?: items
        localChanges = Gson().fromJson(prefs.getString("LocalTasks.localChanges", ""), taskType) ?: localChanges
        initSync =  prefs.getString("LocalTasks.initSync", "true")?.toBoolean()?:true
        showWaiting =  prefs.getString("LocalTasks.showWaiting", "true")?.toBoolean()?:false
        syncKey = prefs.getString("LocalTasks.syncKey", syncKey)?: syncKey
        val lastSeenVersion = prefs.getInt("lastSeenVersion", 1)
        runMigrationsIfRequired(lastSeenVersion, synchronous, context, prefs)
        updateVisibleTasks()
    }

    @Synchronized
    fun runMigrationsIfRequired(lastSeenVersion: Int, synchronous: Boolean, context: Context, prefs: SharedPreferences){
        //migration - breaking changes are versioned here
        var itemsModified = false
        if (lastSeenVersion<2){
            val editor = prefs.edit()
            itemsModified = true
            val dfLocal = SimpleDateFormat()
            dfLocal.setTimeZone(TimeZone.getDefault())
            val dfUtc = SimpleDateFormat()
            dfUtc.setTimeZone(TimeZone.getTimeZone("UTC"))
            for (i in items) {
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
            for (i in items) {
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
            items.forEach{
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
            items.forEach {
                it.waitDate = it.waitDate?.toLocal()
                it.dueDate = it.dueDate?.toLocal()
                it.modifiedDate = now
            }
            editor.putInt("lastSeenVersion", 5)
            if(synchronous) editor.apply() else editor.commit()
        }
        if (itemsModified) {
            save(context, synchronous)
            updateVisibleTasks()
        }
    }

    @Synchronized
    fun updateVisibleTasks(){
        visibleTasks.clear()
        visibleTasks.addAll(items)
        filters.forEach {
            if (it.enabled) visibleTasks = visibleTasks.filter { task -> it.type.filter(task, it.parameter) } as ArrayList<Task>
        }
        //visibleTasks = visibleTasks.filter { it.name.isNotBlank() } as ArrayList<Task>
        visibleTasks.sortWith(Task.DateCompare())
        items.sortWith(Task.DateCompare())
    }

    fun getTaskByUUID(uuid: UUID): Task?{
        for(task in items){
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