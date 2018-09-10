package me.bgregos.foreground

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.opengl.Visibility
import android.os.AsyncTask
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import de.aaschmid.taskwarrior.TaskwarriorClient
import de.aaschmid.taskwarrior.config.TaskwarriorPropertiesConfiguration
import de.aaschmid.taskwarrior.internal.ManifestHelper
import de.aaschmid.taskwarrior.message.TaskwarriorMessage
import kotlinx.android.synthetic.main.activity_settings2.*
import kotlinx.android.synthetic.main.activity_task_list.*
import kotlinx.android.synthetic.main.task_detail.*
import me.bgregos.foreground.task.RemoteTaskManager
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.*


class SettingsActivity : AppCompatActivity() {

    private var PROPERTIES_TASKWARRIOR : URL? = null

    private inner class TestTask : AsyncTask<Void, Void, String>() {

        val config = TaskwarriorPropertiesConfiguration(PROPERTIES_TASKWARRIOR)
        val client = TaskwarriorClient(config)

        override fun onPreExecute() {
        }

        override fun doInBackground(vararg v: Void): String {

            val headers = HashMap<String, String>()
            headers.put(TaskwarriorMessage.HEADER_TYPE, "statistics")
            headers.put(TaskwarriorMessage.HEADER_PROTOCOL, "v1")
            headers.put(TaskwarriorMessage.HEADER_CLIENT, "taskwarrior-java-client " + ManifestHelper.getImplementationVersionFromManifest("local-dev"))

            try {
                val response = client.sendAndReceive(TaskwarriorMessage(headers))
                return response.toString()
            } catch (e: Exception) {
                return "Network Failure"
            }
        }

        override fun onProgressUpdate(vararg v: Void) {
        }

        override fun onPostExecute(response: String) {
            Log.d(this.javaClass.toString(), response)
            settings_syncprogress.visibility = View.INVISIBLE
            if (!response.contains("status=Ok")) {
                val bar = Snackbar.make(settings_parent, "There was a problem with your sync configuration.", Snackbar.LENGTH_LONG)
                bar.view.setBackgroundColor(Color.parseColor("#34309f"))
                bar.show()

            }else{
                settings_sync.isChecked = true
                val bar = Snackbar.make(settings_parent, "Sync enabled successfully!", Snackbar.LENGTH_SHORT)
                bar.view.setBackgroundColor(Color.parseColor("#34309f"))
                bar.show()
            }

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PROPERTIES_TASKWARRIOR = File(this.filesDir, "taskwarrior.properties").toURI().toURL()
        setContentView(R.layout.activity_settings2)
        settings_syncprogress.visibility = View.INVISIBLE
        actionBar?.setDisplayHomeAsUpEnabled(true)
        actionBar?.setDisplayShowHomeEnabled(true)
        actionBar?.setDisplayShowTitleEnabled(true)
        val permissions_response = 240

        //load preferences
        val prefs = this.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
        settings_sync.isChecked = prefs.getBoolean("settings_sync", false)
        settings_address.setText(prefs.getString("settings_address", ""))
        var port = prefs.getInt("settings_port", -1).toString()
        port = if (port=="-1") "" else port
        settings_port.setText(port)
        settings_credentials.setText(prefs.getString("settings_credentials", ""))
        settings_cert_path.setText(prefs.getString("settings_cert_path", ""))
        settings_cert_ca.setText(prefs.getString("settings_cert_ca", ""))
        settings_private_key.setText(prefs.getString("settings_cert_key", ""))
        settings_cert_private.setText(prefs.getString("settings_cert_private", ""))

        settings_sync.setOnClickListener {
            if(settings_sync.isChecked) {
                settings_sync.isChecked = false
                val bar = Snackbar.make(settings_parent, "Sync is coming soon!", Snackbar.LENGTH_SHORT)
                bar.view.setBackgroundColor(Color.parseColor("#34309f"))
                bar.show()
            }

//            if (settings_sync.isChecked && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//                settings_sync.isChecked = false
//                settings_syncprogress.visibility = View.VISIBLE
//                ActivityCompat.requestPermissions(this,
//                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), permissions_response)
//            }
//            if (settings_sync.isChecked){
//                settings_sync.isChecked = false
//                settings_syncprogress.visibility = View.VISIBLE
//                save()
//                TestTask().execute()
//            }
        }

    }

    override fun onPause() {
        save()
        super.onPause()
    }

    fun save() {
        //handle port save
        val port = if (settings_port.text.toString() == "") -1 else settings_port.text.toString().toInt()
        val creds = settings_credentials.text.toString().split("/")
        /*  Generate settings file used by the taskwarrior sync agent. These settings
            are a subset of the full application settings */


        val infile = File("taskwarrior.properties")
        if (!infile.exists()) { //create empty properties file if it doesnt exist.
            val properties = Properties()
            val openedFile = openFileOutput("taskwarrior.properties", Context.MODE_PRIVATE)
            properties.store(openedFile, "")
            openedFile.close()
        }
        val inputFile = openFileInput("taskwarrior.properties")
        val properties = Properties()
        properties.load(inputFile)
        properties["taskwarrior.server.host"]=settings_address.text.toString()
        properties["taskwarrior.server.port"]=port.toString()
        properties["taskwarrior.ssl.folder"]=settings_cert_path.text.toString()
        properties["taskwarrior.ssl.cert.ca.file"]=settings_cert_ca.text.toString()
        properties["taskwarrior.ssl.private.key.file"]=settings_private_key.text.toString()
        properties["taskwarrior.ssl.cert.key.file"]=settings_cert_private.text.toString()
        if(creds.size == 3){
            properties["taskwarrior.auth.organisation"]=creds[0]
            properties["taskwarrior.auth.user"]=creds[1]
            properties["taskwarrior.auth.key"]=creds[2]
        }
        inputFile.close()
        val openedFile = openFileOutput("taskwarrior.properties", Context.MODE_PRIVATE)
        properties.store(openedFile, "")
        openedFile.close()

        // Store all app settings
        val sp = this.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
        val editor = sp.edit()

        editor.putBoolean("settings_sync", settings_sync.isChecked)
        editor.putString("settings_address", settings_address.text.toString())
        editor.putInt("settings_port", port)
        editor.putString("settings_credentials", settings_credentials.text.toString())
        editor.putString("settings_cert_path", settings_cert_path.text.toString())
        editor.putString("settings_cert_ca", settings_cert_ca.text.toString())
        editor.putString("settings_cert_key", settings_private_key.text.toString())
        editor.putString("settings_cert_private", settings_cert_private.text.toString())
        editor.apply()
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            240 -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Toast.makeText(this, "File permissions granted!", Toast.LENGTH_SHORT).show()
                } else {
                    settings_sync.isChecked = false
                    Toast.makeText(this, "File permissions not granted, disabling Sync", Toast.LENGTH_LONG).show()
                }
                return
            }
            else -> {
                // Ignore all other requests.
            }
        }
    }





}
