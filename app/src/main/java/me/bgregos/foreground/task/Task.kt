package me.bgregos.foreground.task

import java.io.Serializable
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
    var unsupported : String? = null //the full json task string from the task server for handling unsupported fields

    //List of all possible Task Statuses at https://taskwarrior.org/docs/design/task.html#attr_status

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