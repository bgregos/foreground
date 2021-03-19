package me.bgregos.foreground.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
import androidx.work.*
import kotlinx.android.synthetic.main.activity_settings.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bgregos.foreground.R
import me.bgregos.foreground.getApplicationComponent
import me.bgregos.foreground.model.SyncResult
import me.bgregos.foreground.tasklist.TaskRepository
import me.bgregos.foreground.network.RemoteTaskSource
import me.bgregos.foreground.network.TaskwarriorSyncWorker
import me.bgregos.foreground.util.ShowErrorDetail
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SettingsActivity : AppCompatActivity() {

    @Inject
    lateinit var localTasksRepository: TaskRepository

    @Inject
    lateinit var remoteTaskSource: RemoteTaskSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getApplicationComponent().inject(this)
        setContentView(R.layout.activity_settings)
        settings_syncprogress.visibility = View.INVISIBLE
        settings_sync.visibility = View.VISIBLE
        actionBar?.setDisplayHomeAsUpEnabled(true)
        actionBar?.setDisplayShowHomeEnabled(true)
        actionBar?.setDisplayShowTitleEnabled(true)

        //load preferences
        val prefs = this.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
        settings_sync.isChecked = prefs.getBoolean("settings_sync", false)
        settings_auto_sync.isChecked = prefs.getBoolean("settings_auto_sync", false)
        settings_auto_sync_interval.setText(prefs.getString("settings_auto_sync_interval", "60"))
        settings_address.setText(prefs.getString("settings_address", ""))
        var port = prefs.getInt("settings_port", -1).toString()
        port = if (port=="-1") "" else port
        settings_port.setText(port)
        settings_credentials.setText(prefs.getString("settings_credentials", ""))
        settings_cert_ca.setText(prefs.getString("settings_cert_ca", ""))
        settings_private_key.setText(prefs.getString("settings_cert_key", ""))
        settings_cert_private.setText(prefs.getString("settings_cert_private", ""))

        settings_sync.setOnClickListener {

            if (settings_sync.isChecked) {
                settings_enable_sync_text.text = "Testing Sync"
                settings_sync.isChecked = false
                settings_sync.visibility = View.GONE
                save()
                settings_syncprogress.visibility = View.VISIBLE
                CoroutineScope(Dispatchers.Main).launch {
                    var response: SyncResult
                    var exception: Exception? = null
                    try {
                        response = remoteTaskSource.taskwarriorInitSync()
                    }catch (e: Exception){
                        exception = e
                        Log.e("test sync error", e.toString())
                        response = SyncResult(false, "Invalid or incomplete configuration.")
                    }
                    Log.i(this.javaClass.toString(), response.message)
                    settings_sync.visibility = View.VISIBLE
                    settings_enable_sync_text.text = "Enable Sync"
                    settings_syncprogress.visibility = View.INVISIBLE
                    if (response.success) {
                        settings_sync.isChecked = true
                        val bar = Snackbar.make(settings_parent, "Sync enabled successfully!", Snackbar.LENGTH_SHORT)
                        bar.view.setBackgroundColor(Color.parseColor("#34309f"))
                        bar.show()
                    }else{
                        val bar = Snackbar.make(settings_parent, response.message, Snackbar.LENGTH_LONG)
                        bar.view.setBackgroundColor(Color.parseColor("#34309f"))
                        bar.setAction("Details", ShowErrorDetail(exception?.toString() ?: response.message, this@SettingsActivity))
                        bar.setActionTextColor(Color.WHITE)
                        bar.show()
                    }
                }
            } else {
                settings_auto_sync.isChecked = false
            }
        }

        settings_cert_ca_button.setOnClickListener { performCertFileSearch(CertType.CERT_CA) }
        settings_private_key_button.setOnClickListener { performCertFileSearch(CertType.CERT_KEY) }
        settings_cert_private_button.setOnClickListener { performCertFileSearch(CertType.CERT_PRIVATE) }
        settings_auto_sync.setOnClickListener { onAutoSyncClicked() }
        settings_auto_sync.setOnFocusChangeListener { _, focused -> if(!focused) onAutoSyncIntervalChanged() }
    }

    fun onAutoSyncIntervalChanged(){
        settings_auto_sync.isChecked = false
        var interval: Long = settings_auto_sync_interval.text.toString().toLong()
        if (interval < 15){
            settings_auto_sync_interval.setText("15")
        }
    }

    fun onAutoSyncClicked(){
        if(settings_auto_sync.isChecked){
            var interval: Long = settings_auto_sync_interval.text.toString().toLong()
            if (interval < 15){
                interval = 15
                settings_auto_sync_interval.setText("15")
            }
            val syncRequest =
                    PeriodicWorkRequest.Builder(TaskwarriorSyncWorker::class.java, interval, TimeUnit.MINUTES)
                            .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork("foreground_sync", ExistingPeriodicWorkPolicy.REPLACE, syncRequest)

        }else{
            WorkManager.getInstance(this).cancelAllWork()
        }
    }

    override fun onPause() {
        save()
        super.onPause()
    }

    fun save() {
        onAutoSyncClicked()
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
        editor.putBoolean("settings_auto_sync", settings_auto_sync.isChecked)
        editor.putString("settings_auto_sync_interval", settings_auto_sync_interval.text.toString())
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
                        settings_cert_ca.setText(uriToName(uri))
                        writeCertFile(uri, "cert_ca")
                    }
                    CertType.CERT_KEY -> {
                        settings_private_key.setText(uriToName(uri))
                        writeCertFile(uri, "cert_key")
                    }
                    CertType.CERT_PRIVATE -> {
                        settings_cert_private.setText(uriToName(uri))
                        writeCertFile(uri, "cert_private")
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, resultData)
    }

    fun uriToName(uri: Uri):String{

        // The query, since it only applies to a single document, will only return
        // one row. There's no need to filter, sort, or select fields, since we want
        // all fields for one document.
        val cursor: Cursor? = contentResolver.query( uri, null, null, null, null, null)

        cursor?.use {
            // moveToFirst() returns false if the cursor has 0 rows.  Very handy for
            // "if there's anything to look at, look at it" conditionals.
            if (it.moveToFirst()) {

                // Note it's called "Display Name".  This is
                // provider-specific, and might not necessarily be the file name.
                val displayName: String =
                        it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                return displayName
            }
        }
        return "filename not found"
    }

    fun writeCertFile(uri:Uri, fileName:String){
        val inStream = this.contentResolver.openInputStream(uri)
        val file = File(filesDir, fileName)
        if (inStream != null){
            val buffer = ByteArray(inStream.available())
            inStream.read(buffer)
            inStream.close()
            val outStream = FileOutputStream(file)
            outStream.write(buffer)
            outStream.close()
        } else {
            Log.e("Foreground Settings", "Failed to write cert file!")
        }

    }
}
