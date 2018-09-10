package me.bgregos.foreground.task

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.AsyncTask
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.widget.Toast
import de.aaschmid.taskwarrior.TaskwarriorClient
import de.aaschmid.taskwarrior.message.TaskwarriorMessage
import de.aaschmid.taskwarrior.internal.ManifestHelper
import de.aaschmid.taskwarrior.message.TaskwarriorMessage.HEADER_CLIENT
import de.aaschmid.taskwarrior.message.TaskwarriorMessage.HEADER_PROTOCOL
import de.aaschmid.taskwarrior.message.TaskwarriorMessage.HEADER_TYPE
import de.aaschmid.taskwarrior.config.TaskwarriorPropertiesConfiguration
import java.io.File
import java.net.URL
import kotlinx.coroutines.*
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking


class RemoteTaskManager(c:Context) {
    private val PROPERTIES_TASKWARRIOR = File(c.filesDir, "taskwarrior.properties").toURI().toURL()

    private inner class SyncTask : AsyncTask<Void, Void, String>() {

        val config = TaskwarriorPropertiesConfiguration(PROPERTIES_TASKWARRIOR)
        val client = TaskwarriorClient(config)

        override fun onPreExecute() {
        }

        override fun doInBackground(vararg v: Void): String {

            val headers = HashMap<String, String>()
            headers.put(HEADER_TYPE, "statistics")
            headers.put(HEADER_PROTOCOL, "v1")
            headers.put(HEADER_CLIENT, "taskwarrior-java-client " + ManifestHelper.getImplementationVersionFromManifest("local-dev"))

            val response = client.sendAndReceive(TaskwarriorMessage(headers))
            return response.toString()
        }

        override fun onProgressUpdate(vararg v: Void) {
        }

        override fun onPostExecute(response: String) {
            Log.e(this.javaClass.toString(), response)
        }
    }

    //load preferences
    val prefs = c.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
    var sync_enabled = prefs.getBoolean("settings_sync", false)

    init {
        if (!sync_enabled) {
            Toast.makeText(c, "Enable Sync in settings to perform a sync.", Toast.LENGTH_LONG).show()
        }
    }


    fun sync() {
        try{
            val config = TaskwarriorPropertiesConfiguration(PROPERTIES_TASKWARRIOR)
            Log.e(this.javaClass.toString(), "Alive1")

            val client = TaskwarriorClient(config)

            Log.e(this.javaClass.toString(), "Alive2")


            val headers = HashMap<String, String>()
            headers.put(HEADER_TYPE, "sync")
            headers.put(HEADER_PROTOCOL, "v1")
            headers.put(HEADER_CLIENT, "taskwarrior-java-client " + ManifestHelper.getImplementationVersionFromManifest("local-dev"))

            Log.e(this.javaClass.toString(), "Alive3")

            Thread {
                try {
                    var response: TaskwarriorMessage? = null;
                    response = client.sendAndReceive(TaskwarriorMessage(headers))
                    Log.e(this.javaClass.toString(), response.toString())
                } catch (e:Exception) {
                    e.printStackTrace()
                }
            }.start()


        } catch (e: Exception){
            e.printStackTrace()
        }
    }
}