package me.bgregos.foreground.task

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*
import kotlin.Comparator
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

data class Task(var name:String) : Serializable {
    var uuid:UUID = UUID.randomUUID()
    var dueDate:Date? = null
    var createdDate:Date = Date()
    var project:String? = null
    var tags:ArrayList<String> = ArrayList()
    var modifiedDate:Date? = null
    var priority: String? = ""
    var status: String = "pending"
    var waitDate:Date? = null
    var others = mutableMapOf<String, String>() //unaccounted-for fields. (User Defined Attributes)
    //List of all possible Task Statuses at https://taskwarrior.org/docs/design/task.html#attr_status

    class DateCompare : Comparator<Task>{
        override fun compare(o1: Task, o2: Task): Int {
            if (o1.dueDate == null && o2.dueDate == null) {
                return 0;
            }

            if (o1.dueDate == null) {
                return 1;
            }

            if (o2.dueDate == null) {
                return -1;
            }

            val out = compareValues(o1.dueDate, o2.dueDate)
            if (out != 0) {
                return out
            }
            return compareValues(o1.createdDate, o2.createdDate)
        }

    }

    companion object {

        fun shouldDisplay(task:Task):Boolean{
            if (!(task.status=="completed" || task.status=="deleted" || task.status=="recurring" || task.status=="waiting"))
                return true
            val date = task.waitDate
            if(date != null) {
                if (date.before(Date()) && task.status=="waiting"){
                    task.status="pending"
                    return true
                }
                return false
            }
            return false
        }

        fun shouldDisplayShowWaiting(task:Task):Boolean{
            if (!(task.status=="completed" || task.status=="deleted" || task.status=="recurring" ))
                return true
            return false
        }

        fun toJson(task:Task):String{
            Log.v("brighttask", "tojsoning: "+task.name)
            val out = JSONObject()
            out.putOpt("description", task.name)
            out.put("uuid", task.uuid)
            out.putOpt("project", task.project)
            out.putOpt("status", task.status)
            out.putOpt("priority", task.priority)
            if (task.dueDate!=null) {
                out.putOpt("due", SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'").format(task.dueDate))
            }
            if (task.waitDate!=null) {
                out.putOpt("wait", SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'").format(task.waitDate))
            }
            if (task.modifiedDate!=null) {
                out.putOpt("modified", SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'").format(task.modifiedDate))
            }
            out.putOpt("created", SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'").format(task.createdDate))
            out.putOpt("tags", JSONArray(task.tags))

            for(extra in task.others){
                out.putOpt(extra.key, extra.value)
            }
            
            return out.toString()
        }

        fun unescape(str:String):String {
            return str.replace("\\", "")
        }

        fun fromJson(json:String):Task?{
            val out = Task("")
            var obj = JSONObject()
            try {
                obj = JSONObject(json)
            } catch (ex:Exception){
                Log.e(this.javaClass.toString(), "Skipping task import: "+ex.toString())
                return null
            }
            out.name = obj.optString("description")?: ""
            out.uuid = UUID.fromString(obj.getString("uuid"))
            out.project = obj.optString("project") ?: null
            out.status = obj.optString("status") ?: "pending"
            out.priority = obj.optString("priority") ?: null
            var convDate : String? = null
            convDate = obj.optString("due")?:""
            if (!convDate.isNullOrBlank()) {
                out.dueDate = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'").parse(convDate)
            }
            convDate = obj.optString("modified")?:""
            if (!convDate.isNullOrBlank()) {
                out.modifiedDate = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'").parse(convDate)
            }
            convDate = obj.optString("created")?:""
            if (!convDate.isNullOrBlank()) {
                out.createdDate = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'").parse(convDate)
            }
            convDate = obj.optString("wait")?:""
            if (!convDate.isNullOrBlank()) {
                out.waitDate = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'").parse(convDate)
            }
            val jsontags = obj.optJSONArray("tags")?: JSONArray()

            for (j in 0 until jsontags.length()) {
                out.tags.add(jsontags.getString(j))
            }
            //remove what we have specific fields for
            obj.remove("description")
            obj.remove("uuid")
            obj.remove("project")
            obj.remove("status")
            obj.remove("priority")
            obj.remove("due")
            obj.remove("modified")
            obj.remove("created")
            obj.remove("tags")
            obj.remove("wait")
            //add all others to the others map
            for (key in obj.keys()){
                out.others[key] = unescape(obj.getString(key))
            }
            return out
        }
    }

}