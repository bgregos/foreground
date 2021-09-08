package me.bgregos.foreground.model

import android.util.Log
import com.google.gson.JsonObject
import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*
import kotlin.Comparator

data class Task(
        val name:String,
        val uuid:UUID = UUID.randomUUID(),
        val dueDate:Date? = null,
        val createdDate:Date = Date(),
        val project:String? = null,
        val tags:List<String> = listOf(),
        val annotations:List<String> = listOf(),
        val modifiedDate:Date? = null,
        val priority: String? = null,
        val status: String = "pending",
        val waitDate:Date? = null,
        val endDate:Date? = null,
        val others: Map<String, String> = mapOf() //unaccounted-for fields. (User Defined Attributes)
) : Serializable {
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

        fun shouldDisplay(task: Task):Boolean{
            if (!(task.status=="completed" || task.status=="deleted" || task.status=="recurring" || task.status=="waiting"))
                return true
            val date = task.waitDate

            return false
        }

        fun shouldDisplayShowWaiting(task: Task):Boolean{
            if (!(task.status=="completed" || task.status=="deleted" || task.status=="recurring" ))
                return true
            return false
        }

        /**
         * Serializes this task to JSON for taskwarrior sync
         */
        fun toJson(task: Task):String{
            val timeFormatter = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'")
            timeFormatter.timeZone = TimeZone.getTimeZone("UTC")
            Log.v("brighttask", "tojsoning: "+task.name)
            val out = JSONObject()
            out.putOpt("description", task.name)
            out.put("uuid", task.uuid)
            out.putOpt("project", task.project)
            out.putOpt("status", task.status)
            out.putOpt("priority", task.priority)
            if (task.dueDate!=null) {
                out.putOpt("due", timeFormatter.format(task.dueDate))
            }
            if (task.waitDate!=null) {
                out.putOpt("wait", timeFormatter.format(task.waitDate))
            }
            if (task.modifiedDate!=null) {
                out.putOpt("modified", timeFormatter.format(task.modifiedDate))
            }
            if (task.endDate!=null) {
                out.putOpt("end", timeFormatter.format(task.endDate))
            }
            out.putOpt("entry", timeFormatter.format(task.createdDate))
            out.putOpt("tags", JSONArray(task.tags))
            out.putOpt("annotations", JSONArray(task.annotations))

            for(extra in task.others){
                out.putOpt(extra.key, extra.value)
            }
            
            return out.toString()
        }

        fun unescape(str:String):String {
            return str.replace("\\", "")
        }

        /**
         * Converts a taskwarrior-formatted task from JSON into a native Foreground task.
         * Used during taskwarrior sync.
         */
        fun fromJson(json:String): Task?{
            var obj = JSONObject()
            try {
                obj = JSONObject(json)
            } catch (ex:Exception){
                Log.e(this.javaClass.toString(), "Skipping task import: "+ex.toString())
                return null
            }

            val timeFormatter = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'")
            timeFormatter.timeZone = TimeZone.getTimeZone("UTC")

            val name = obj.optString("description")?: ""
            val uuid = UUID.fromString(obj.getString("uuid"))
            val project = obj.optString("project") ?: null
            val status = obj.optString("status") ?: "pending"
            val priority = obj.optString("priority") ?: null
            var convDate : String? = null
            var dueDate: Date? = null
            convDate = obj.optString("due")?:""
            if (!convDate.isNullOrBlank()) {
                dueDate = timeFormatter.parse(convDate)
            }
            var modifiedDate: Date? = null
            convDate = obj.optString("modified")?:""
            if (!convDate.isNullOrBlank()) {
                modifiedDate = timeFormatter.parse(convDate)
            }
            var entryDate = Date()
            // "created" is changed to "entry" since 1.4 for better sync compatibility
            convDate = obj.optString("created")?:""
            if (convDate.isNullOrBlank()){
                convDate = obj.optString("entry")?:""
            }
            if (!convDate.isNullOrBlank()) {
                entryDate = timeFormatter.parse(convDate)
            }
            var waitDate: Date? = null
            convDate = obj.optString("wait")?:""
            if (!convDate.isNullOrBlank()) {
                waitDate = timeFormatter.parse(convDate)
            }
            var endDate: Date? = null
            convDate = obj.optString("end")?:""
            if (!convDate.isNullOrBlank()) {
                endDate = timeFormatter.parse(convDate)
            }
            val tags = arrayListOf<String>()
            val jsontags = obj.optJSONArray("tags")?: JSONArray()

            for (j in 0 until jsontags.length()) {
                tags.add(jsontags.getString(j))
            }

            val annotations = arrayListOf<String>()
            val jsonannotations = obj.optJSONArray("annotation")?: JSONArray()

            for (j in 0 until jsonannotations.length()) {
                annotations.add(jsonannotations.getString(j))
            }

            val others: MutableMap<String, String> = mutableMapOf()
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
            obj.remove("annotations")
            obj.remove("wait")
            obj.remove("end")
            //add all others to the others map
            for (key in obj.keys()){
                others[key] = unescape(obj.getString(key))
            }
            return Task(
                    name = name,
                    uuid = uuid,
                    project = project,
                    status = status,
                    priority = priority,
                    dueDate = dueDate,
                    createdDate = entryDate,
                    waitDate = waitDate,
                    endDate = endDate,
                    modifiedDate = modifiedDate,
                    tags = tags,
                    annotations = annotations,
                    others = others
            )
        }
    }

}