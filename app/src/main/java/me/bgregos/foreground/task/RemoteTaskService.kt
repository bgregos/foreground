package me.bgregos.foreground.task

import android.content.Context
import android.content.Intent
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.aaschmid.taskwarrior.TaskwarriorClient
import de.aaschmid.taskwarrior.message.TaskwarriorMessage
import de.aaschmid.taskwarrior.internal.ManifestHelper
import de.aaschmid.taskwarrior.config.TaskwarriorPropertiesConfiguration
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.lang.IllegalArgumentException
import java.util.*
import kotlin.collections.ArrayList

class RemoteTaskManager(c:Context) {
    private val PROPERTIES_TASKWARRIOR = File(c.filesDir, "taskwarrior.properties").toURI().toURL()
    internal val ctx = c

    val config = TaskwarriorPropertiesConfiguration(PROPERTIES_TASKWARRIOR)
    val client = TaskwarriorClient(config)
    private var recievedMessage : TaskwarriorMessage? = null
    private var outPayload : String? = null
    private var syncKey : UUID? = null
    data class SyncResult(var success:Boolean, var message:String)

    class TaskwarriorSyncWorker(appContext: Context, workerParams: WorkerParameters)
        : CoroutineWorker(appContext, workerParams) {
        var ctx = appContext

        override suspend fun doWork(): Result = coroutineScope {
            val job = async {
                RemoteTaskManager(ctx).taskwarriorSync()
                Log.i("taskwarrior_sync", "Automatic Sync Complete")
                var localIntent: Intent = Intent("BRIGHTTASK_REMOTE_TASK_UPDATE") //Send local broadcast
                LocalBroadcastManager.getInstance(ctx).sendBroadcast(localIntent)
            }
            job.await()
            Result.success()
        }
    }

    suspend fun taskwarriorTestSync(): SyncResult = withContext(Dispatchers.IO) launch@{
        if (client == null) {
            return@launch SyncResult(false, "Invalid Config")
        }
        val headers = HashMap<String, String>()
        headers.put(TaskwarriorMessage.HEADER_TYPE, "statistics")
        headers.put(TaskwarriorMessage.HEADER_PROTOCOL, "v1")
        headers.put(TaskwarriorMessage.HEADER_CLIENT, "foreground " + ManifestHelper.getImplementationVersionFromManifest("local-dev"))

        try {
            val response:TaskwarriorMessage = client!!.sendAndReceive(TaskwarriorMessage(headers))
            if (response.toString().contains("status=Ok")){
                return@launch SyncResult(true, response.toString())
            }else{
                return@launch SyncResult(false, "Response not ok. $response")
            }
        } catch (e: Exception) {
            return@launch SyncResult(false, e.message ?: "General Error")
        }
    }

    suspend fun taskwarriorSync(): SyncResult = withContext(Dispatchers.IO) launch@{
        var result:SyncResult? = null

        val headers = HashMap<String, String>()
        headers[TaskwarriorMessage.HEADER_TYPE] = "sync"
        headers[TaskwarriorMessage.HEADER_PROTOCOL] = "v1"
        headers[TaskwarriorMessage.HEADER_CLIENT] = "foreground" + ManifestHelper.getImplementationVersionFromManifest("local-dev")

        if (!LocalTasks.initSync) { //do not upload on first-round initial sync
            val sb = StringBuilder()
            sb.appendln(LocalTasks.syncKey) //uuid goes first
            for (task in LocalTasks.localChanges) {
                sb.appendln(Task.toJson(task))
            }
            outPayload = sb.toString()
            Log.d(this.javaClass.toString(), "outpayload: " + outPayload)
        }
        var response: TaskwarriorMessage? = null
        var error: String? = null
        try {
            if (outPayload.isNullOrBlank()) {
                response = client.sendAndReceive(TaskwarriorMessage(headers))
            } else {
                response = client.sendAndReceive(TaskwarriorMessage(headers, outPayload))
            }
            recievedMessage = response
        } catch (e: Exception) {
            error = e.toString()
        }

        var responseString = error ?: response.toString()
        val rcvdmessage = recievedMessage
        //Log.d(this.javaClass.toString(), responseString)


        if ((!responseString.contains("status=Ok") && !responseString.contains("status=No change")) || rcvdmessage == null || rcvdmessage.payload == null) {

            return@launch SyncResult(false, responseString);

        } else { //success
            LocalTasks.localChanges.clear()
            val jsonObjStrArr: ArrayList<String> = rcvdmessage.payload.toString().replaceFirst("Optional[", "").split("\n") as ArrayList<String>
            jsonObjStrArr.removeAt(jsonObjStrArr.lastIndex)
            for (str in jsonObjStrArr) {
                Log.d("full message recieved", str)
            }
            val jArray: ArrayList<JSONObject> = ArrayList()
            try {
                UUID.fromString(jsonObjStrArr.get(0))
                //sync key is at top
                LocalTasks.syncKey = jsonObjStrArr.removeAt(0)
            } catch (e: IllegalArgumentException) {
                try {
                    UUID.fromString(jsonObjStrArr.get(jsonObjStrArr.lastIndex - 1))
                    //sync key is at bottom
                    LocalTasks.syncKey = jsonObjStrArr.removeAt(jsonObjStrArr.lastIndex - 1)
                } catch (e: IllegalArgumentException) {
                    //no sync key!
                    Log.e(this.javaClass.toString(), "Error parsing sync data, no sync key.", e)
                    return@launch SyncResult(false, "Error parsing sync data, no sync key.")
                }
            }
            Log.v("sync key", LocalTasks.syncKey)
            for (taskString in jsonObjStrArr) {
                if (taskString != "") {
                    val task = Task.fromJson(taskString)

                    if (task != null) {
                        val storedTask = LocalTasks.getTaskByUUID(task.uuid)
                        //add task to LocalTasks. must make sure that tasks with same uuid get overwritten by newest task
                        //check if stored task is older and not null
                        if (storedTask?.modifiedDate?.before(task.modifiedDate) == true) {
                            //stored task is older or has no timestamp, replace
                            LocalTasks.items.remove(storedTask)
                            if (!(task.status == "completed" || task.status == "deleted" || task.status == "recurring")) {
                                LocalTasks.items.add(task)
                            }
                        } else if (storedTask == null) {
                            //add new task
                            if (!(task.status == "completed" || task.status == "deleted" || task.status == "recurring")) {
                                LocalTasks.items.add(task)
                            }
                        } else {
                            //task is older than current, do nothing.
                        }
                    }

                }
            }
            LocalTasks.save(ctx)

            if (LocalTasks.initSync) { //immediately after initial sync, start another to upload tasks.
                LocalTasks.initSync = false
                Log.i("taskwarriorSync", "Initial sync finished, uploading tasks...")
                var result = taskwarriorSync()
                return@launch result
            } else {
                LocalTasks.updateVisibleTasks()
                Log.i("taskwarriorSync", "Sync successful")
                return@launch SyncResult(true, "Sync Successful")
            }
        }
    }
}