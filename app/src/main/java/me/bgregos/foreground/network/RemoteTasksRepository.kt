package me.bgregos.foreground.network

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import de.aaschmid.taskwarrior.TaskwarriorClient
import de.aaschmid.taskwarrior.config.TaskwarriorPropertiesConfiguration
import de.aaschmid.taskwarrior.message.TaskwarriorMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.bgregos.foreground.BuildConfig
import me.bgregos.foreground.di.CustomWorkerFactory
import me.bgregos.foreground.model.Task
import me.bgregos.foreground.tasklist.LocalTasksRepository
import me.bgregos.foreground.util.NotificationRepository
import me.bgregos.foreground.util.contentsChanged
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.*
import javax.inject.Inject
import javax.inject.Provider
import kotlin.collections.ArrayList

class RemoteTasksRepository @Inject constructor(private val filesDir: File, private val notificationRepository: NotificationRepository, private val tasksRepository: LocalTasksRepository, private val sharedPreferences: SharedPreferences) {
    var syncInitialized: Boolean = sharedPreferences.getBoolean("settings_sync", false)

    // These are set prior to sync
    private lateinit var taskwarriorPropertiesURL: URL
    private lateinit var config: TaskwarriorPropertiesConfiguration
    private lateinit var client: TaskwarriorClient

    private val mutex: Mutex = Mutex()

    data class SyncResult(var success: Boolean, var message: String)

    suspend fun taskwarriorInitSync(): SyncResult = withContext(Dispatchers.IO) launch@{

        //instantiate a TaskwarriorClient with the latest sync settings
        taskwarriorPropertiesURL = File(filesDir, "taskwarrior.properties").toURI().toURL()
        config = TaskwarriorPropertiesConfiguration(taskwarriorPropertiesURL)
        client = TaskwarriorClient(config)

        val headers = HashMap<String, String>()
        headers.put(TaskwarriorMessage.HEADER_TYPE, "statistics")
        headers.put(TaskwarriorMessage.HEADER_PROTOCOL, "v1")
        headers.put(TaskwarriorMessage.HEADER_CLIENT, "foreground ${BuildConfig.VERSION_NAME}")

        try {
            val response:TaskwarriorMessage = client.sendAndReceive(TaskwarriorMessage(headers))
            if (response.toString().contains("status=Ok")){
                syncInitialized = true
                return@launch SyncResult(true, response.toString())
            }else{
                syncInitialized = false
                return@launch SyncResult(false, "Response not ok. $response")
            }
        } catch (e: Exception) {
            syncInitialized = false
            return@launch SyncResult(false, e.message ?: "General Error")
        }
    }

    suspend fun taskwarriorSync(): SyncResult = withContext(Dispatchers.IO) launch@{
        if(syncInitialized){

            //instantiate a TaskwarriorClient with the latest sync settings
            taskwarriorPropertiesURL = File(filesDir, "taskwarrior.properties").toURI().toURL()
            config = TaskwarriorPropertiesConfiguration(taskwarriorPropertiesURL)
            client = TaskwarriorClient(config)

            mutex.withLock {
                var receivedMessage : TaskwarriorMessage? = null
                var outPayload : String? = null
                val headers = HashMap<String, String>()
                headers[TaskwarriorMessage.HEADER_TYPE] = "sync"
                headers[TaskwarriorMessage.HEADER_PROTOCOL] = "v1"
                headers[TaskwarriorMessage.HEADER_CLIENT] = "foreground ${BuildConfig.VERSION_NAME}"

                if (!tasksRepository.initSync) { //do not upload on first-round initial sync
                    val sb = StringBuilder()
                    sb.appendLine(tasksRepository.syncKey) //uuid goes first
                    for (task in tasksRepository.localChanges.value ?: ArrayList()) {
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
                    tasksRepository.localChanges.value?.clear()
                    val jsonObjStrArr: ArrayList<String> = rcvdmessage.payload.toString().replaceFirst("Optional[", "").split("\n") as ArrayList<String>
                    jsonObjStrArr.removeAt(jsonObjStrArr.lastIndex)
                    for (str in jsonObjStrArr) {
                        Log.d("full message recieved", str)
                    }
                    try {
                        UUID.fromString(jsonObjStrArr.get(0))
                        //sync key is at top
                        tasksRepository.syncKey = jsonObjStrArr.removeAt(0)
                    } catch (e: IllegalArgumentException) {
                        try {
                            UUID.fromString(jsonObjStrArr.get(jsonObjStrArr.lastIndex - 1))
                            //sync key is at bottom
                            tasksRepository.syncKey = jsonObjStrArr.removeAt(jsonObjStrArr.lastIndex - 1)
                        } catch (e: IllegalArgumentException) {
                            //no sync key!
                            Log.e(this.javaClass.toString(), "Error parsing sync data, no sync key.", e)
                            return@launch SyncResult(false, "Error parsing sync data, no sync key.")
                        }
                    }
                    Log.v("sync key", tasksRepository.syncKey)
                    for (taskString in jsonObjStrArr) {
                        if (taskString != "") {
                            val task = Task.fromJson(taskString)

                            if (task != null) {
                                val storedTask = tasksRepository.getTaskByUUID(task.uuid)
                                //add task to LocalTasks. must make sure that tasks with same uuid get overwritten by newest task
                                //check if stored task is older and not null
                                if (storedTask?.modifiedDate?.before(task.modifiedDate) == true) {
                                    //stored task is older or has no timestamp, replace
                                    tasksRepository.tasks.value?.remove(storedTask)
                                    if (!(task.status == "completed" || task.status == "deleted" || task.status == "recurring")) {
                                        tasksRepository.tasks.value?.add(task)
                                    }
                                } else if (storedTask == null) {
                                    //add new task
                                    if (!(task.status == "completed" || task.status == "deleted" || task.status == "recurring")) {
                                        tasksRepository.tasks.value?.add(task)
                                    }
                                } else {
                                    //task is older than current, do nothing.
                                }
                            }

                        }
                    }
                    tasksRepository.tasks.postValue(tasksRepository.tasks.value) //send livedata update
                    tasksRepository.save(true)

                    if (tasksRepository.initSync) { //immediately after initial sync, start another to upload tasks.
                        tasksRepository.initSync = false
                        Log.i("taskwarriorSync", "Initial sync finished, uploading tasks...")
                        var result = taskwarriorSync()
                        return@launch result
                    } else {
                        Log.i("taskwarriorSync", "Sync successful")
                        return@launch SyncResult(true, "Sync Successful")
                    }
                }
            }
        } else {
            return@launch SyncResult(false, "Sync not setup - you can do this in settings")
        }

    }
}

class TaskwarriorSyncWorker(val ctx: Context, workerParams: WorkerParameters, private val notificationRepository: NotificationRepository, private val tasksRepository: LocalTasksRepository, private val remoteTasks: RemoteTasksRepository)
    : CoroutineWorker(ctx, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {
        val job = async {
            remoteTasks.taskwarriorSync()
            Log.i("taskwarrior_sync", "Automatic Sync Complete")
            //TODO: Remove this broadcast - it should be handled by livedata
            var localIntent: Intent = Intent("BRIGHTTASK_REMOTE_TASK_UPDATE") //Send local broadcast
            notificationRepository.scheduleNotificationForTasks(tasksRepository.tasks.value as List<Task>)
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(localIntent)
        }
        job.await()
        Result.success()
    }

    class Factory @Inject constructor(
            private val notificationRepository: Provider<NotificationRepository>,
            private val tasksRepository: Provider<LocalTasksRepository>,
            private val remoteTasksRepository: Provider<RemoteTasksRepository>
    ) : CustomWorkerFactory {
        override fun create(appContext: Context, params: WorkerParameters): ListenableWorker {
            return TaskwarriorSyncWorker(
                    appContext,
                    params,
                    notificationRepository.get(),
                    tasksRepository.get(),
                    remoteTasksRepository.get()
            )
        }
    }
}