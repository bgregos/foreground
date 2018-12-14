package me.bgregos.foreground

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
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
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.*


class SettingsActivity : AppCompatActivity() {

    private var PROPERTIES_TASKWARRIOR : URL? = null

    private inner class TestTask : AsyncTask<Void, Void, String>() {

        val config = TaskwarriorPropertiesConfiguration(PROPERTIES_TASKWARRIOR)
        var client : TaskwarriorClient? = null
        init {
            try {
                client = TaskwarriorClient(config)
            } catch (e:Exception){
                //keep client as null, weill be caught by async task
            }
        }


        override fun onPreExecute() {
            save()
        }

        override fun doInBackground(vararg v: Void): String {
            if (client == null) {
                return "Invalid Config"
            }
            val headers = HashMap<String, String>()
            headers.put(TaskwarriorMessage.HEADER_TYPE, "statistics")
            headers.put(TaskwarriorMessage.HEADER_PROTOCOL, "v1")
            headers.put(TaskwarriorMessage.HEADER_CLIENT, "taskwarrior-java-client " + ManifestHelper.getImplementationVersionFromManifest("local-dev"))

            try {
                val response:TaskwarriorMessage = client!!.sendAndReceive(TaskwarriorMessage(headers))
                return response.toString()
            } catch (e: Exception) {
                return e.message ?: "General Error"
            }

        }

        override fun onProgressUpdate(vararg v: Void) {
        }

        override fun onPostExecute(response: String) {
            Log.i(this.javaClass.toString(), response)
            settings_sync.visibility = View.VISIBLE
            settings_enable_sync_text.text = "Enable Sync"
            settings_syncprogress.visibility = View.INVISIBLE
            if (!response.contains("status=Ok")) {
                val bar = Snackbar.make(settings_parent, "There was a problem testing the sync configuration: "+response, Snackbar.LENGTH_INDEFINITE)
                bar.view.setBackgroundColor(Color.parseColor("#34309f"))
                bar.setAction("Dismiss",  View.OnClickListener {
                    bar.dismiss()
                })
                bar.setActionTextColor(Color.WHITE)
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
        settings_sync.visibility = View.VISIBLE
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
        settings_cert_ca.setText(prefs.getString("settings_cert_ca", ""))
        settings_private_key.setText(prefs.getString("settings_cert_key", ""))
        settings_cert_private.setText(prefs.getString("settings_cert_private", ""))

        settings_sync.setOnClickListener {

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

            if (settings_sync.isChecked) {
                settings_enable_sync_text.text = "Testing Sync"
                settings_sync.isChecked = false
                settings_sync.visibility = View.GONE
                save()
                settings_syncprogress.visibility = View.VISIBLE
                TestTask().execute()
            }
        }

        settings_cert_ca_button.setOnClickListener { performCertFileSearch(CertType.CERT_CA) }
        settings_private_key_button.setOnClickListener { performCertFileSearch(CertType.CERT_KEY) }
        settings_cert_private_button.setOnClickListener { performCertFileSearch(CertType.CERT_PRIVATE) }

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
        properties["taskwarrior.ssl.cert.ca.file"]=File(filesDir, "cert_ca").absolutePath ?: ""
        //TODO: This leads to a parsing error in the lib. Try external storage?
        properties["taskwarrior.ssl.private.key.file"]=File(filesDir, "cert_key").absolutePath ?: ""
        properties["taskwarrior.ssl.cert.key.file"]=File(filesDir, "cert_private").absolutePath ?: ""
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
        editor.putString("settings_cert_ca", settings_cert_ca.text.toString())
        editor.putString("settings_cert_key", settings_private_key.text.toString())
        editor.putString("settings_cert_private", settings_cert_private.text.toString())
        editor.apply()
    }

    enum class CertType(val value: Int) {
        CERT_CA(1),
        CERT_KEY(2),
        CERT_PRIVATE(3)
    }

    private val requestCodes : ArrayList<Int> = ArrayList()

    fun performCertFileSearch(certType: CertType) {
        requestCodes.add(certType.value)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            // Filter to only show results that can be "opened", such as a
            // file (as opposed to a list of contacts or timezones)
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, certType.value)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {

        // The ACTION_OPEN_DOCUMENT intent was sent with the request code
        // READ_REQUEST_CODE. If the request code seen here doesn't match, it's the
        // response to some other intent, and the code below shouldn't run at all.

        if (requestCodes.remove(requestCode) && resultCode == Activity.RESULT_OK) {
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.
            // Pull that URI using resultData.getData().
            resultData?.data?.also { uri ->
                //put uri in bar
                Log.e(this.javaClass.toString(), uri.toString())
                Log.e(this.javaClass.toString(), CertType.values().filter{ x -> x.value==requestCode}.first().toString())
                when(CertType.values().filter{ x -> x.value==requestCode}.first()) {
                    CertType.CERT_CA -> {
                        settings_cert_ca.setText(uri.lastPathSegment.toString().split("/").last())
                        writeCertFile(uri, "cert_ca")
                    }
                    CertType.CERT_KEY-> {
                        settings_private_key.setText(uri.lastPathSegment.toString().split("/").last())
                        writeCertFile(uri, "cert_key")
                    }
                    CertType.CERT_PRIVATE -> {
                        settings_cert_private.setText(uri.lastPathSegment.toString().split("/").last())
                        writeCertFile(uri, "cert_private")
                    }
                }
            }
        }
    }

    fun writeCertFile(uri:Uri, fileName:String){
        val inStream = this.contentResolver.openInputStream(uri)
        val file = File(filesDir, fileName)
        val buffer = ByteArray(inStream.available())
        inStream.read(buffer)
        inStream.close()
        val outStream = FileOutputStream(file)
        outStream.write(buffer)
        outStream.close()
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
