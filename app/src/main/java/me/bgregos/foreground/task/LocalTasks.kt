package me.bgregos.foreground.task

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.util.*
import com.google.gson.reflect.TypeToken
import me.bgregos.foreground.toUtc
import java.text.SimpleDateFormat
import kotlin.collections.ArrayList

object LocalTasks {
    var items:ArrayList<Task> = ArrayList()
    var visibleTasks:ArrayList<Task> = ArrayList()
    var localChanges:ArrayList<Task> = ArrayList()
    var initSync:Boolean = true
    var syncKey:String = ""
    var showWaiting:Boolean = false


    fun save(context: Context) {
        updateVisibleTasks()
        val prefs = context.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("LocalTasks", Gson().toJson(items))
        editor.putString("LocalTasks.localChanges", Gson().toJson(localChanges))
        editor.putString("LocalTasks.initSync", initSync.toString())
        editor.putString("LocalTasks.syncKey", syncKey)
        editor.putString("LocalTasks.showWaiting", showWaiting.toString())
        editor.apply()
    }

    fun load(context: Context) {
        val prefs = context.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
        val taskType = object : TypeToken<ArrayList<Task>>() {}.type
        items = Gson().fromJson(prefs.getString("LocalTasks", ""), taskType) ?: items
        localChanges = Gson().fromJson(prefs.getString("LocalTasks.localChanges", ""), taskType) ?: localChanges
        initSync =  prefs.getString("LocalTasks.initSync", "true")?.toBoolean()?:true
        showWaiting=  prefs.getString("LocalTasks.showWaiting", "true")?.toBoolean()?:false
        syncKey = prefs.getString("LocalTasks.syncKey", syncKey)?: syncKey
        val lastSeenVersion = prefs.getInt("lastSeenVersion", 1)

        //migration
        var itemsModified = false
        if (lastSeenVersion<2){ //keep track of breaking changes
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
            editor.apply()
        }
        if (lastSeenVersion<3){ //keep track of breaking changes
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
            editor.apply()
        }
        if (itemsModified) {
            save(context)
            updateVisibleTasks()
        }
    }

    fun markTaskDone(uuid: UUID) {
        val item = getTaskByUUID(uuid)
        if(item == null) {
            Log.e("LocalTasks", "Failed to find a task with the given UUID")
            return
        }
        item.modifiedDate=Date().toUtc() //update modified date
        item.status = "completed"
        if (!localChanges.contains(item)){
            localChanges.add(item)
        }
    }

    fun updateVisibleTasks(){
        visibleTasks.clear()
        for (t in items){
            if (showWaiting){
                if (Task.shouldDisplayShowWaiting(t)){
                    visibleTasks.add(t)
                    Log.i("update visible", "showing waiting tasks")
                }

            } else {
                if (Task.shouldDisplay(t)) {
                    visibleTasks.add(t)
                    Log.i("update visible", "hiding waiting tasks")
                }
            }
        }
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
}