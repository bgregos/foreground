package me.bgregos.foreground.task

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

data class Task(var name:String) : Serializable {
    var uuid:UUID = UUID.randomUUID()
    var dueDate:Date? = null
    var project:String? = null
    var tags:ArrayList<String> = ArrayList()
    var modifiedDate:Date? = null
    var priority: Priority? = Priority.M
    var status: String = "pending"
    var mostRecentJSON : String? = null //the full json task string from the task server, used for handling unsupported fields
    //List of all possible Task Statuses at https://taskwarrior.org/docs/design/task.html#attr_status

    companion object {
        fun shouldDisplay(task:Task):Boolean{
            if (!(task.status=="completed" || task.status=="deleted" || task.status=="recurring" || task.status=="waiting"))
                return true
            val obj = JSONObject(task.mostRecentJSON)
            val convDate = obj.optString("wait")
            Log.e(this.javaClass.toString(), "jsontags: "+convDate.toString())
            if (!convDate.isNullOrEmpty()) {
                val date = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'").parse(convDate)
                if (date.after(Date()) && task.status=="waiting"){
                    task.status="pending"
                    return true
                }
            }
            return false
        }
        fun fromJson(json:String):Task{
            val out = Task("")
            val obj = JSONObject(json)
            out.name = obj.optString("description")?: ""
            //todo: graceful fail on no uuid
            out.uuid = UUID.fromString(obj.getString("uuid"))
            out.project = obj.optString("project") ?: null
            out.status = obj.optString("status") ?: "pending"
            out.priority = when (obj.optString("priority") ?: null) {
                "H" -> Task.Priority.H
                "M" -> Task.Priority.M
                "L" -> Task.Priority.L
                else -> null
            }
            var convDate : String? = null
            convDate = obj.optString("due")?:""
            if (!convDate.isNullOrEmpty()) {
                out.dueDate = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'").parse(convDate)
            }
            convDate = obj.optString("modified")?:""
            if (!convDate.isNullOrEmpty()) {
                out.modifiedDate = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'").parse(convDate)
            }
            val jsontags = obj.optJSONArray("tags")?: JSONArray()

            for (j in 0 until jsontags.length()) {
                out.tags.add(jsontags.getString(j))
            }
            out.mostRecentJSON = json
            return out
        }
    }

    //All possible task priorities, not including empty, which is represented by null.
    enum class Priority {
        H {
            override fun toString(): String {
                return "H"
            }
        },
        M {
            override fun toString(): String {
                return "M"
            }
        },
        L {
            override fun toString(): String {
                return "L"
            }
        }
    }

}