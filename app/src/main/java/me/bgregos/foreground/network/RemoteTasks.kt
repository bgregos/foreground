package me.bgregos.foreground.network

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.aaschmid.taskwarrior.TaskwarriorClient
import de.aaschmid.taskwarrior.message.TaskwarriorMessage
import de.aaschmid.taskwarrior.internal.ManifestHelper
import de.aaschmid.taskwarrior.config.TaskwarriorPropertiesConfiguration
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.bgregos.foreground.BuildConfig
import me.bgregos.foreground.model.Task
import me.bgregos.foreground.tasklist.LocalTasksRepository
import me.bgregos.foreground.util.NotificationService
import me.bgregos.foreground.util.contentsChanged
import org.json.JSONObject
import java.io.File
import java.lang.IllegalArgumentException
import java.util.*
import kotlin.collections.ArrayList

class RemoteTasks(c: Context) {
    private val PROPERTIES_TASKWARRIOR = File(c.filesDir, "taskwarrior.properties").toURI().toURL()
    private val ctx = c

    private val config = TaskwarriorPropertiesConfiguration(PROPERTIES_TASKWARRIOR)
    private val client = TaskwarriorClient(config)
    private val mutex: Mutex = Mutex()

    data class SyncResult(var success:Boolean, var message:String)

    class TaskwarriorSyncWorker(appContext: Context, workerParams: WorkerParameters)
        : CoroutineWorker(appContext, workerParams) {
        var ctx = appContext

        override suspend fun doWork(): Result = coroutineScope {
            val job = async {
                RemoteTasks(ctx).taskwarriorSync()
                Log.i("taskwarrior_sync", "Automatic Sync Complete")
                var localIntent: Intent = Intent("BRIGHTTASK_REMOTE_TASK_UPDATE") //Send local broadcast
                NotificationService.scheduleNotificationForTasks(LocalTasksRepository.tasks.value, ctx)
                LocalBroadcastManager.getInstance(ctx).sendBroadcast(localIntent)
            }
            job.await()
            Result.success()
        }
    }

    suspend fun taskwarriorTestSync(): SyncResult = withContext(Dispatchers.IO) launch@{
        val headers = HashMap<String, String>()
        headers.put(TaskwarriorMessage.HEADER_TYPE, "statistics")
        headers.put(TaskwarriorMessage.HEADER_PROTOCOL, "v1")
        headers.put(TaskwarriorMessage.HEADER_CLIENT, "foreground " + ManifestHelper.getImplementationVersionFromManifest("local-dev"))

        try {
            val response:TaskwarriorMessage = client.sendAndReceive(TaskwarriorMessage(headers))
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
        mutex.withLock {
            var receivedMessage : TaskwarriorMessage? = null
            var outPayload : String? = null
            val headers = HashMap<String, String>()
            headers[TaskwarriorMessage.HEADER_TYPE] = "sync"
            headers[TaskwarriorMessage.HEADER_PROTOCOL] = "v1"
            headers[TaskwarriorMessage.HEADER_CLIENT] = "foreground ${BuildConfig.VERSION_NAME}" + ManifestHelper.getImplementationVersionFromManifest("local-dev")

            if (!LocalTasksRepository.initSync) { //do not upload on first-round initial sync
                val sb = StringBuilder()
                sb.appendLine(LocalTasksRepository.syncKey) //uuid goes first
                for (task in LocalTasksRepository.localChanges.value) {
                    sb.appendLine(Task.toJson(task))
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
                receivedMessage = response
            } catch (e: Exception) {
                error = e.toString()
            }

            var responseString = error ?: response.toString()
            val rcvdmessage = receivedMessage
            //Log.d(this.javaClass.toString(), responseString)


            if ((!responseString.contains("status=Ok") && !responseString.contains("status=No change")) || rcvdmessage == null || rcvdmessage.payload == null) {

                return@launch SyncResult(false, responseString);

            } else { //success
                LocalTasksRepository.localChanges.value.clear()
                val jsonObjStrArr: ArrayList<String> = rcvdmessage.payload.toString().replaceFirst("Optional[", "").split("\n") as ArrayList<String>
                jsonObjStrArr.removeAt(jsonObjStrArr.lastIndex)
                for (str in jsonObjStrArr) {
                    Log.d("full message recieved", str)
                }
                val jArray: ArrayList<JSONObject> = ArrayList()
                try {
                    UUID.fromString(jsonObjStrArr.get(0))
                    //sync key is at top
                    LocalTasksRepository.syncKey = jsonObjStrArr.removeAt(0)
                } catch (e: IllegalArgumentException) {
                    try {
                        UUID.fromString(jsonObjStrArr.get(jsonObjStrArr.lastIndex - 1))
                        //sync key is at bottom
                        LocalTasksRepository.syncKey = jsonObjStrArr.removeAt(jsonObjStrArr.lastIndex - 1)
                    } catch (e: IllegalArgumentException) {
                        //no sync key!
                        Log.e(this.javaClass.toString(), "Error parsing sync data, no sync key.", e)
                        return@launch SyncResult(false, "Error parsing sync data, no sync key.")
                    }
                }
                Log.v("sync key", LocalTasksRepository.syncKey)
                for (taskString in jsonObjStrArr) {
                    if (taskString != "") {
                        val task = Task.fromJson(taskString)

                        if (task != null) {
                            val storedTask = LocalTasksRepository.getTaskByUUID(task.uuid)
                            //add task to LocalTasks. must make sure that tasks with same uuid get overwritten by newest task
                            //check if stored task is older and not null
                            if (storedTask?.modifiedDate?.before(task.modifiedDate) == true) {
                                //stored task is older or has no timestamp, replace
                                LocalTasksRepository.tasks.value.remove(storedTask)
                                if (!(task.status == "completed" || task.status == "deleted" || task.status == "recurring")) {
                                    LocalTasksRepository.tasks.value.add(task)
                                }
                            } else if (storedTask == null) {
                                //add new task
                                if (!(task.status == "completed" || task.status == "deleted" || task.status == "recurring")) {
                                    LocalTasksRepository.tasks.value.add(task)
                                }
                            } else {
                                //task is older than current, do nothing.
                            }
                        }

                    }
                }
                LocalTasksRepository.tasks.contentsChanged()
                LocalTasksRepository.save(ctx, true)

                if (LocalTasksRepository.initSync) { //immediately after initial sync, start another to upload tasks.
                    LocalTasksRepository.initSync = false
                    Log.i("taskwarriorSync", "Initial sync finished, uploading tasks...")
                    var result = taskwarriorSync()
                    return@launch result
                } else {
                    Log.i("taskwarriorSync", "Sync successful")
                    return@launch SyncResult(true, "Sync Successful")
                }
            }
        }
    }
}