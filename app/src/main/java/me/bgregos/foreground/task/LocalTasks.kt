package me.bgregos.foreground.task

import android.content.Context
import com.google.gson.Gson
import java.util.*
import com.google.gson.reflect.TypeToken




object LocalTasks {
    var items:ArrayList<Task> = ArrayList()

    fun addMockTasks() {
        for(i in 0..10){
            items.add(Task("Test Task $i"))
        }
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("LocalTasks", Gson().toJson(items))
        editor.apply()
    }

    fun load(context: Context) {
        val prefs = context.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
        val taskType = object : TypeToken<ArrayList<Task>>() {}.type
        items = Gson().fromJson(prefs.getString("LocalTasks", ""), taskType) ?: items
    }

    fun getTaskByUUID(uuid: UUID): Task?{
        for(task in items){
            if(task.uuid == uuid){
                return task;
            }
        }
        return null;
    }
}