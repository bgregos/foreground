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
import androidx.appcompat.app.AlertDialog
import androidx.work.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bgregos.foreground.R
import me.bgregos.foreground.getApplicationComponent
import me.bgregos.foreground.model.SyncResult
import me.bgregos.foreground.data.tasks.TaskRepository
import me.bgregos.foreground.databinding.ActivitySettingsBinding
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
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getApplicationComponent().inject(this)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.settingsSyncprogress.visibility = View.INVISIBLE
        binding.settingsSync.visibility = View.VISIBLE
        actionBar?.setDisplayHomeAsUpEnabled(true)
        actionBar?.setDisplayShowHomeEnabled(true)
        actionBar?.setDisplayShowTitleEnabled(true)

        //load preferences
        val prefs = this.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
        binding.settingsSync.isChecked = prefs.getBoolean("settings_sync", false)
        binding.settingsAutoSync.isChecked = prefs.getBoolean("settings_auto_sync", false)
        binding.settingsAutoSyncInterval.setText(prefs.getString("settings_auto_sync_interval", "60"))
        binding.settingsAddress.setText(prefs.getString("settings_address", ""))
        var port = prefs.getInt("settings_port", -1).toString()
        port = if (port=="-1") "" else port
        binding.settingsPort.setText(port)
        binding.settingsCredentials.setText(prefs.getString("settings_credentials", ""))
        binding.settingsCertCa.setText(prefs.getString("settings_cert_ca", ""))
        binding.settingsPrivateKey.setText(prefs.getString("settings_cert_key", ""))
        binding.settingsCertPrivate.setText(prefs.getString("settings_cert_private", ""))

        binding.settingsSync.setOnClickListener {

            if (binding.settingsSync.isChecked) {
                val alertDialogBuilder = AlertDialog.Builder(this)
                        .setTitle(getString(R.string.warning))
                        .setMessage(getString(R.string.sync_warning))
                        .setPositiveButton(getString(R.string.accept)) { _, _ -> attemptInitialSync() }
                        .setNegativeButton(getString(R.string.go_back)) { _, _ -> cancelInitialSync() }
                alertDialogBuilder.show()
            } else {
                binding.settingsAutoSync.isChecked = false
                //TODO: cancel the auto sync work specifically
                WorkManager.getInstance(this).cancelAllWork()
                CoroutineScope(Dispatchers.Main).launch {
                    localTasksRepository.disableSync()
                }
            }
        }

        binding.settingsCertCaButton.setOnClickListener { performCertFileSearch(CertType.CERT_CA) }
        binding.settingsPrivateKeyButton.setOnClickListener { performCertFileSearch(CertType.CERT_KEY) }
        binding.settingsCertPrivateButton.setOnClickListener { performCertFileSearch(CertType.CERT_PRIVATE) }
        binding.settingsAutoSync.setOnClickListener { onAutoSyncClicked() }
        binding.settingsAutoSync.setOnFocusChangeListener { _, focused -> if(!focused) onAutoSyncIntervalChanged() }
        binding.settingsDefaultTags.setText(prefs.getString("settings_default_tags", ""))
    }

    fun onAutoSyncIntervalChanged(){
        val interval: Long = binding.settingsAutoSyncInterval.text.toString().toLong()
        if (interval < 15){
            binding.settingsAutoSyncInterval.setText("15")
        }
    }

    private fun attemptInitialSync(){
        binding.settingsEnableSyncText.text = "Testing Sync"
        binding.settingsSync.isChecked = false
        binding.settingsSync.visibility = View.GONE
        save()
        binding.settingsSyncprogress.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.Main).launch {
            var response: SyncResult
            var exception: Exception? = null
            try {
                response = localTasksRepository.testSync()
            }catch (e: Exception){
                exception = e
                Log.e("test sync error", e.toString())
                response = SyncResult(false, "Invalid or incomplete configuration.")
            }
            Log.i(this.javaClass.toString(), response.message)
            binding.settingsSync.visibility = View.VISIBLE
            binding.settingsEnableSyncText.text = "Enable Sync"
            binding.settingsSyncprogress.visibility = View.INVISIBLE
            if (response.success) {
                createSnackbar(getString(R.string.sync_enabled_message), Snackbar.LENGTH_SHORT)
                binding.settingsSync.isChecked = true
            }else{
                val bar = Snackbar.make(binding.settingsParent, response.message, Snackbar.LENGTH_LONG)
                bar.view.setBackgroundColor(Color.parseColor("#34309f"))
                bar.setAction("Details", ShowErrorDetail(exception?.toString() ?: response.message, this@SettingsActivity))
                bar.setActionTextColor(Color.WHITE)
                bar.show()
            }
        }
    }

    private fun cancelInitialSync(){
        binding.settingsSync.isChecked = false
        binding.settingsAutoSync.isChecked = false
    }

    fun onAutoSyncClicked(){
        if(binding.settingsAutoSync.isChecked && !binding.settingsSync.isChecked) {
            createSnackbar(getString(R.string.auto_sync_requirement), Snackbar.LENGTH_SHORT)
            binding.settingsAutoSync.isChecked = false
            return
        }
        if(binding.settingsAutoSync.isChecked){
            var interval: Long = binding.settingsAutoSyncInterval.text.toString().toLong()
            if (interval < 15){
                interval = 15
                binding.settingsAutoSyncInterval.setText("15")
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
        //handle port save
        val port = if (binding.settingsPort.text.toString() == "") -1 else binding.settingsPort.text.toString().toInt()
        val creds = binding.settingsCredentials.text.toString().split("/")
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
        properties["taskwarrior.server.host"]=binding.settingsAddress.text.toString()
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

        editor.putBoolean("settings_sync", binding.settingsSync.isChecked)
        editor.putBoolean("settings_auto_sync", binding.settingsAutoSync.isChecked)
        editor.putString("settings_auto_sync_interval", binding.settingsAutoSyncInterval.text.toString())
        editor.putString("settings_address", binding.settingsAddress.text.toString())
        editor.putInt("settings_port", port)
        editor.putString("settings_credentials", binding.settingsCredentials.text.toString())
        editor.putString("settings_cert_ca", binding.settingsCertCa.text.toString())
        editor.putString("settings_cert_key", binding.settingsPrivateKey.text.toString())
        editor.putString("settings_cert_private", binding.settingsCertPrivate.text.toString())
        editor.putString("settings_default_tags", binding.settingsDefaultTags.text.toString())
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
                        binding.settingsCertCa.setText(uriToName(uri))
                        writeCertFile(uri, "cert_ca")
                    }
                    CertType.CERT_KEY -> {
                        binding.settingsPrivateKey.setText(uriToName(uri))
                        writeCertFile(uri, "cert_key")
                    }
                    CertType.CERT_PRIVATE -> {
                        binding.settingsCertPrivate.setText(uriToName(uri))
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

    private fun createSnackbar(text: String, length: Int){
        val bar = Snackbar.make(binding.settingsParent, text, length)
        bar.view.setBackgroundColor(Color.parseColor("#34309f"))
        bar.show()
    }
}
