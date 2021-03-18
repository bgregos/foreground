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
import me.bgregos.foreground.model.SyncResult
import me.bgregos.foreground.model.Task
import me.bgregos.foreground.tasklist.TaskRepository
import me.bgregos.foreground.util.NotificationRepository
import java.io.File
import java.net.URL
import java.util.*
import javax.inject.Inject
import javax.inject.Provider
import kotlin.collections.ArrayList

class RemoteTaskSourceImpl @Inject constructor(private val filesDir: File, private val sharedPreferences: SharedPreferences): RemoteTaskSource {
    override var syncEnabled: Boolean = sharedPreferences.getBoolean("settings_sync", false)
    var firstSyncRan: Boolean = sharedPreferences.getBoolean("RemoteTaskSource.firstSyncRan", false)

    override var tasks: MutableList<Task> = mutableListOf()
    override var localChanges: MutableList<Task> = mutableListOf()
    var syncKey:String = ""

    // These are set prior to sync
    private lateinit var taskwarriorPropertiesURL: URL
    private lateinit var config: TaskwarriorPropertiesConfiguration
    private lateinit var client: TaskwarriorClient

    private val mutex: Mutex = Mutex()

    override suspend fun taskwarriorInitSync(): SyncResult = withContext(Dispatchers.IO) launch@{

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
                syncEnabled = true
                return@launch SyncResult(true, response.toString())
            }else{
                syncEnabled = false
                return@launch SyncResult(false, "Response not ok. $response")
            }
        } catch (e: Exception) {
            syncEnabled = false
            return@launch SyncResult(false, e.message ?: "General Error")
        }
    }

    override suspend fun taskwarriorSync(): SyncResult = withContext(Dispatchers.IO) launch@{
        if(syncEnabled){
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

                if (firstSyncRan) { //do not upload on first-round initial sync, need to download first
                    val sb = StringBuilder()
                    sb.appendLine(syncKey) //uuid goes first
                    for (task in localChanges) {
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

                val responseString = error ?: response.toString()
                val rcvdmessage = receivedMessage

                if ((!responseString.contains("status=Ok") && !responseString.contains("status=No change")) || rcvdmessage == null || rcvdmessage.payload == null) {

                    return@launch SyncResult(false, responseString);

                } else { //success
                    localChanges.clear()
                    val jsonObjStrArr: ArrayList<String> = rcvdmessage.payload.toString().replaceFirst("Optional[", "").split("\n") as ArrayList<String>
                    jsonObjStrArr.removeAt(jsonObjStrArr.lastIndex)
                    for (str in jsonObjStrArr) {
                        Log.d("full message recieved", str)
                    }
                    try {
                        UUID.fromString(jsonObjStrArr.get(0))
                        //sync key is at top
                        syncKey = jsonObjStrArr.removeAt(0)
                    } catch (e: java.lang.Exception) {
                        try {
                            UUID.fromString(jsonObjStrArr.get(jsonObjStrArr.lastIndex - 1))
                            //sync key is at bottom
                            syncKey = jsonObjStrArr.removeAt(jsonObjStrArr.lastIndex - 1)
                        } catch (e: java.lang.Exception) {
                            //no sync key!
                            Log.e(this.javaClass.toString(), "Error parsing sync data, no sync key.", e)
                            return@launch SyncResult(false, "Error parsing sync data, no sync key.")
                        }
                    }
                    Log.v("sync key", syncKey)
                    for (taskString in jsonObjStrArr) {
                        if (taskString != "") {
                            val task = Task.fromJson(taskString)

                            if (task != null) {
                                val storedTask = getTaskByUUID(task.uuid)
                                //add task to LocalTasks. must make sure that tasks with same uuid get overwritten by newest task
                                //check if stored task is older and not null
                                if (storedTask?.modifiedDate?.before(task.modifiedDate) == true) {
                                    //stored task is older or has no timestamp, replace
                                    tasks.remove(storedTask)
                                    if (!(task.status == "completed" || task.status == "deleted" || task.status == "recurring")) {
                                        tasks.add(task)
                                    }
                                } else if (storedTask == null) {
                                    //add new task
                                    if (!(task.status == "completed" || task.status == "deleted" || task.status == "recurring")) {
                                        tasks.add(task)
                                    }
                                } else {
                                    //task is older than current, do nothing.
                                }
                            }

                        }
                    }

                    if (!firstSyncRan) { //immediately after initial sync, start another to upload tasks.
                        firstSyncRan = true
                        sharedPreferences.edit().putBoolean("RemoteTaskSource.firstSyncRan", true).apply()
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

    override fun resetSync() {
        tasks.clear()
        localChanges.clear()
        syncKey = ""
        syncEnabled = false
        firstSyncRan = false
    }

    override suspend fun save() {
        sharedPreferences.edit().apply{
            putBoolean("RemoteTaskSource.firstSyncRan", firstSyncRan)
            putString("RemoteTaskSource.syncKey", syncKey)
        }.apply()
    }

    override suspend fun load() {
        sharedPreferences.apply {
            runMigrationIfRequired()
            firstSyncRan = getBoolean("RemoteTaskSource.firstSyncRan", false)
            syncKey = getString("RemoteTaskSource.syncKey", "") ?: ""
        }
    }

    suspend fun runMigrationIfRequired() {
        sharedPreferences.apply {
            val editor = edit()
            //migrate from old system
            val initSync = getString("LocalTasks.initSync", null)
            val syncKey = getString("LocalTasks.syncKey", null)
            var changed = false
            if (initSync != null){
                editor.putBoolean("RemoteTaskSource.firstSyncRan", !initSync.toBoolean())
                editor.remove("LocalTasks.initSync")
                changed = true
            }
            if(syncKey != null){
                editor.putString("RemoteTaskSource.syncKey", syncKey)
                editor.remove("LocalTasks.syncKey")
                changed = true
            }
            if (changed){
                editor.apply()
            }
        }
    }

    private fun getTaskByUUID(uuid: UUID): Task?{
        val tasklist = tasks
        for(task in tasklist){
            if(task.uuid == uuid){
                return task
            }
        }
        return null
    }
}

class TaskwarriorSyncWorker(val ctx: Context, workerParams: WorkerParameters, private val notificationRepository: NotificationRepository, private val tasksRepository: TaskRepository, private val remoteTasks: RemoteTaskSource)
    : CoroutineWorker(ctx, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {
        val job = async {
            remoteTasks.taskwarriorSync()
            Log.i("taskwarrior_sync", "Automatic Sync Complete")
            //TODO: Remove this broadcast - it should be handled by livedata
            var localIntent: Intent = Intent("BRIGHTTASK_REMOTE_TASK_UPDATE") //Send local broadcast
            notificationRepository.scheduleNotificationForTasks(tasksRepository.tasks)
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(localIntent)
        }
        job.await()
        Result.success()
    }

    class Factory @Inject constructor(
            private val notificationRepository: Provider<NotificationRepository>,
            private val tasksRepository: Provider<TaskRepository>,
            private val remoteTaskSource: Provider<RemoteTaskSource>
    ) : CustomWorkerFactory {
        override fun create(appContext: Context, params: WorkerParameters): ListenableWorker {
            return TaskwarriorSyncWorker(
                    appContext,
                    params,
                    notificationRepository.get(),
                    tasksRepository.get(),
                    remoteTaskSource.get()
            )
        }
    }
}