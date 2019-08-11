package me.bgregos.foreground

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.text.TextUtils.split
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import com.google.gson.JsonObject
import com.jakewharton.threetenabp.AndroidThreeTen
import de.aaschmid.taskwarrior.TaskwarriorClient
import de.aaschmid.taskwarrior.config.TaskwarriorPropertiesConfiguration
import de.aaschmid.taskwarrior.internal.ManifestHelper
import de.aaschmid.taskwarrior.message.TaskwarriorMessage
import kotlinx.android.synthetic.main.activity_settings2.*
import kotlinx.android.synthetic.main.activity_task_list.*
import kotlinx.android.synthetic.main.task_detail.*
import kotlinx.android.synthetic.main.task_list.*
import kotlinx.android.synthetic.main.task_list_content.*
import kotlinx.android.synthetic.main.task_list_content.view.*
import me.bgregos.foreground.R.id.action_visibility
import me.bgregos.foreground.R.id.task_list
import me.bgregos.foreground.task.LocalTasks
import me.bgregos.foreground.task.LocalTasks.items
import me.bgregos.foreground.task.LocalTasks.localChanges
import me.bgregos.foreground.task.LocalTasks.updateVisibleTasks
import me.bgregos.foreground.task.LocalTasks.visibleTasks
import me.bgregos.foreground.task.Task
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONException
import java.lang.IllegalArgumentException
import kotlin.collections.ArrayList


/**
 * An activity representing a list of Pings. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a [TaskDetailActivity] representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
class TaskListActivity : AppCompatActivity(), PopupMenu.OnMenuItemClickListener {

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private var twoPane: Boolean = false
    private var PROPERTIES_TASKWARRIOR : URL? = null
    private var recievedMessage : TaskwarriorMessage? = null
    private var outPayload : String? = null
    private var syncKey : UUID? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidThreeTen.init(this)
        LocalTasks.load(this)

        setContentView(R.layout.activity_task_list)
        PROPERTIES_TASKWARRIOR = File(this.filesDir, "taskwarrior.properties").toURI().toURL()
        toolbar.navigationIcon = null
        toolbar.title = ""
        toolbar.subtitle = ""
        setSupportActionBar(toolbar)
        fab.setOnClickListener { view ->
            val newTask = Task("")
            LocalTasks.items.add(newTask)
            openTask(newTask, view, "New Task")

        }

        if (task_detail_container != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            twoPane = true
        }
        LocalTasks.updateVisibleTasks()

        setupRecyclerView(task_list)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    fun onSyncClick(item: MenuItem) {
        val prefs = this.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
        if (prefs.getBoolean("settings_sync", false)){
            val bar = Snackbar.make(task_list_parent, "Syncing...", Snackbar.LENGTH_SHORT)
            bar.view.setBackgroundColor(Color.parseColor("#34309f"))
            bar.show()
            SyncTask().execute()
        } else {
            val bar = Snackbar.make(task_list_parent, "Sync is disabled! Enable it in the settings menu.", Snackbar.LENGTH_LONG)
            bar.view.setBackgroundColor(Color.parseColor("#34309f"))
            bar.show()
        }
    }

    fun onVisibilityClick(item: MenuItem) {
        PopupMenu(this, findViewById(action_visibility)).apply {
            setOnMenuItemClickListener(this@TaskListActivity)
            inflate(R.menu.menu_visibility)
            if(LocalTasks.showWaiting){
                this.menu.getItem(0).isChecked = true
            }else{
                this.menu.getItem(1).isChecked = true
            }
            show()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_show_waiting -> {
                Log.i("recyclerview item count", task_list.adapter?.itemCount.toString())
                item.isChecked = !item.isChecked
                LocalTasks.showWaiting = true
                LocalTasks.updateVisibleTasks()
                task_list.adapter?.notifyDataSetChanged()
                task_list.hasPendingAdapterUpdates()
                Log.i("recyclerview item count", task_list.adapter?.itemCount.toString() + ","+ LocalTasks.visibleTasks.size.toString())
                true
            }
            R.id.menu_hide_waiting -> {
                Log.i("recyclerview item count", task_list.adapter?.itemCount.toString())
                item.isChecked = !item.isChecked
                LocalTasks.showWaiting = false
                LocalTasks.updateVisibleTasks()
                task_list.adapter?.notifyDataSetChanged()
                task_list.adapter?.notifyItemRangeChanged(0, LocalTasks.items.size)
                task_list.hasPendingAdapterUpdates()
                Log.i("recyclerview item count", task_list.adapter?.itemCount.toString() + ","+ LocalTasks.visibleTasks.size.toString())
                true
            }

            else -> false
        }
    }


    fun onClearClick(item: MenuItem) {
        LocalTasks.items.clear()
        LocalTasks.localChanges.clear()
        LocalTasks.syncKey = ""
        LocalTasks.initSync = true
        task_list.adapter?.notifyDataSetChanged()
        LocalTasks.save(applicationContext)
    }

    fun onSettingsClick(item: MenuItem) {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun setupRecyclerView(recyclerView: RecyclerView) {
        recyclerView.adapter = SimpleItemRecyclerViewAdapter(this, LocalTasks.visibleTasks, twoPane)
    }

    private fun openTask(task: Task, v: View, name: String){
        if (twoPane) {
            val fragment = TaskDetailFragment().apply {
                arguments = Bundle().apply {
                    putString("uuid", task.uuid.toString())
                    putString("displayName", name)
                }
            }
            this.supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.task_detail_container, fragment)
                    .commit()
        } else {
            val intent = Intent(v.context, TaskDetailActivity::class.java)
            intent.putExtra("uuid", task.uuid.toString())
            intent.putExtra("displayName", name)
            v.context.startActivity(intent)
        }
    }

    class SimpleItemRecyclerViewAdapter(private val parentActivity: TaskListActivity,
                                        private var values: java.util.ArrayList<Task>,
                                        private val twoPane: Boolean) :
            RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder>() {

        private val onClickListener: View.OnClickListener

        init {
            onClickListener = View.OnClickListener { v ->
                val task = v.tag as Task
                parentActivity.openTask(task, v, task.name)
            }
        }

        fun swap(){
            values.clear()
            values.addAll(LocalTasks.visibleTasks)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.task_list_content, parent, false)
            return ViewHolder(view)
        }

        fun toLocal(date:Date):Date{
            val dfLocal = SimpleDateFormat()
            dfLocal.timeZone = TimeZone.getDefault()
            val dfUtc = SimpleDateFormat()
            dfUtc.timeZone = TimeZone.getTimeZone("UTC")
            return dfUtc.parse(dfLocal.format(date))
        }

        fun toUtc(date:Date):Date{
            val dfLocal = SimpleDateFormat()
            dfLocal.timeZone = TimeZone.getDefault()
            val dfUtc = SimpleDateFormat()
            dfUtc.timeZone = TimeZone.getTimeZone("UTC")
            return dfLocal.parse(dfUtc.format(date))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val format = SimpleDateFormat("MMM d, yyyy 'at' h:mm aaa", Locale.ENGLISH)
            val item = values[position]
            holder.title.text = item.name
            if(item.dueDate != null) {
                holder.due.text = format.format(toLocal(item.dueDate as Date))
            }

            with(holder.itemView) {
                tag = item
                setOnClickListener(onClickListener)
            }

            holder.complete.setOnClickListener {
                val pos = LocalTasks.items.indexOf(item)
                val visiblePos = LocalTasks.visibleTasks.indexOf(item)
                removeAt(position)
                LocalTasks.items.remove(item)
            }
        }

        fun removeAt(position:Int) {
            values.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, values.size-1)
        }

        override fun getItemCount() = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.title
            val due: TextView = view.due
            val complete: ImageView = view.complete
        }

    }


    private inner class SyncTask : AsyncTask<Void, Void, String>() {

        val config = TaskwarriorPropertiesConfiguration(PROPERTIES_TASKWARRIOR)
        val client = TaskwarriorClient(config)

        override fun onPreExecute() {
            JSONArray()
        }

        override fun doInBackground(vararg v: Void): String {

            val headers = HashMap<String, String>()
            headers.put(TaskwarriorMessage.HEADER_TYPE, "sync")
            headers.put(TaskwarriorMessage.HEADER_PROTOCOL, "v1")
            headers.put(TaskwarriorMessage.HEADER_CLIENT, "foreground" + ManifestHelper.getImplementationVersionFromManifest("local-dev"))

            if (!LocalTasks.initSync) { //do not upload on first-round initial sync
                val sb = StringBuilder()
                sb.appendln(LocalTasks.syncKey) //uuid goes first
                for (task in LocalTasks.localChanges) {
                    sb.appendln(Task.toJson(task))
                }
                outPayload = sb.toString()
                Log.d(this.javaClass.toString(), "outpayload: " + outPayload)
            }
            try {
                var response:TaskwarriorMessage
                if(outPayload.isNullOrBlank()){
                    response = client.sendAndReceive(TaskwarriorMessage(headers))
                } else {
                    response = client.sendAndReceive(TaskwarriorMessage(headers, outPayload))
                }
                recievedMessage = response
                return response.toString()
            } catch (e: Exception) {
                return "Network Failure"
            }
        }

        override fun onProgressUpdate(vararg v: Void) {
        }

        override fun onPostExecute(responseString: String) {
            val rcvdmessage = recievedMessage
            //Log.d(this.javaClass.toString(), responseString)
            if ((!responseString.contains("status=Ok") && !responseString.contains("status=No change")) || rcvdmessage == null || rcvdmessage.payload == null) {
                val bar = Snackbar.make(task_list_parent, "Sync Failed: $responseString", Snackbar.LENGTH_SHORT)
                Log.i(this.javaClass.toString(), "sync fail: "+ responseString)
                bar.view.setBackgroundColor(Color.parseColor("#34309f"))
                bar.show()

            }else{ //success
                LocalTasks.localChanges.clear()
                val jsonObjStrArr : ArrayList<String> = rcvdmessage.payload.toString().replaceFirst("Optional[", "").split("\n") as ArrayList<String>
                jsonObjStrArr.removeAt(jsonObjStrArr.lastIndex)
                for (str in jsonObjStrArr){
                    Log.d("full message recieved", str)
                }
                val jArray : ArrayList<JSONObject> = ArrayList()
                try{
                    UUID.fromString(jsonObjStrArr.get(0))
                    //sync key is at top
                    LocalTasks.syncKey = jsonObjStrArr.removeAt(0)
                } catch (e:IllegalArgumentException) {
                    try {
                        UUID.fromString(jsonObjStrArr.get(jsonObjStrArr.lastIndex-1))
                        //sync key is at bottom
                        LocalTasks.syncKey = jsonObjStrArr.removeAt(jsonObjStrArr.lastIndex-1)
                    } catch (e:IllegalArgumentException) {
                        //no sync key!
                        Log.e(this.javaClass.toString(), "Error parsing sync data, no sync key.", e)
                        val bar = Snackbar.make(task_list_parent, "Sync Failed.", Snackbar.LENGTH_SHORT)
                        bar.view.setBackgroundColor(Color.parseColor("#34309f"))
                        bar.show()
                        return
                    }
                }
                Log.v("sync key", LocalTasks.syncKey)
                for (taskString in jsonObjStrArr){
                    if (taskString != "") {
                        val task = Task.fromJson(taskString)

                        if (task != null){
                            val storedTask = LocalTasks.getTaskByUUID(task.uuid)
                            //add task to LocalTasks. must make sure that tasks with same uuid get overwritten by newest task
                            //check if stored task is older and not null
                            if (storedTask?.modifiedDate?.before(task.modifiedDate) == true) {
                                //stored task is older or has no timestamp, replace
                                LocalTasks.items.remove(storedTask)
                                if (!(task.status=="completed" || task.status=="deleted" || task.status=="recurring")){
                                    LocalTasks.items.add(task)
                                }
                            } else if (storedTask == null) {
                                //add new task
                                if (!(task.status=="completed" || task.status=="deleted" || task.status=="recurring")){
                                    LocalTasks.items.add(task)
                                }
                            } else {
                                //task is older than current, do nothing.
                            }
                        }

                    }
                }
                LocalTasks.save(applicationContext)

                if (LocalTasks.initSync){ //immediately after initial sync, start another to upload tasks.
                    LocalTasks.initSync = false
                    SyncTask().execute()
                }else {
                    val bar = Snackbar.make(task_list_parent, "Sync successful!", Snackbar.LENGTH_SHORT)
                    bar.view.setBackgroundColor(Color.parseColor("#34309f"))
                    bar.show()

                    LocalTasks.updateVisibleTasks()
                    setupRecyclerView(task_list)
                }
            }

        }
    }

}
