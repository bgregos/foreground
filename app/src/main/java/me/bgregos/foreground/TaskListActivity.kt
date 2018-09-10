package me.bgregos.foreground

import android.content.Intent
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.*
import android.widget.ImageView
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
import kotlinx.android.synthetic.main.task_list_content.view.*
import me.bgregos.foreground.task.LocalTasks
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
class TaskListActivity : AppCompatActivity() {

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
        AndroidThreeTen.init(this);
        LocalTasks.load(this)

        setContentView(R.layout.activity_task_list)
        PROPERTIES_TASKWARRIOR = File(this.filesDir, "taskwarrior.properties").toURI().toURL()
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

        setupRecyclerView(task_list)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    fun onSyncClick(item: MenuItem) {
        //SyncTask().execute()
        val bar = Snackbar.make(task_list_parent, "Sync is coming soon!", Snackbar.LENGTH_SHORT)
        bar.view.setBackgroundColor(Color.parseColor("#34309f"))
        bar.show()
    }

    fun onSettingsClick(item: MenuItem) {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun setupRecyclerView(recyclerView: RecyclerView) {
        recyclerView.adapter = SimpleItemRecyclerViewAdapter(this, LocalTasks.items, twoPane)
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
                                        private val values: List<Task>,
                                        private val twoPane: Boolean) :
            RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder>() {

        private val onClickListener: View.OnClickListener

        init {
            onClickListener = View.OnClickListener { v ->
                val task = v.tag as Task
                parentActivity.openTask(task, v, task.name)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.task_list_content, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val format = SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)
            val item = values[position]
            holder.title.text = item.name
            if(item.dueDate != null) {
                holder.due.text = format.format(item.dueDate)
            }

            with(holder.itemView) {
                tag = item
                setOnClickListener(onClickListener)
            }

            holder.delete.setOnClickListener {
                val pos = LocalTasks.items.indexOf(item)
                LocalTasks.items.removeAt(pos)
                LocalTasks.save(parentActivity)
                this.notifyItemRemoved(pos)
            }
        }

        override fun getItemCount() = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.title
            val due: TextView = view.due
            val delete: ImageView = view.delete
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
            headers.put(TaskwarriorMessage.HEADER_CLIENT, "taskwarrior-java-client " + ManifestHelper.getImplementationVersionFromManifest("local-dev"))

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
            Log.d(this.javaClass.toString(), responseString)
            if (!responseString.contains("status=Ok") || rcvdmessage == null || rcvdmessage.payload == null) {
                val bar = Snackbar.make(task_list_parent, "Sync Failed.", Snackbar.LENGTH_SHORT)
                bar.view.setBackgroundColor(Color.parseColor("#34309f"))
                bar.show()

            }else{ //success
                Log.d(this.javaClass.toString(), rcvdmessage.payload.toString())

                val jsonObjStrArr : ArrayList<String> = rcvdmessage.payload.toString().replaceFirst("Optional[", "").split("\n") as ArrayList<String>
                jsonObjStrArr.removeAt(jsonObjStrArr.lastIndex)
                for (str in jsonObjStrArr){
                    Log.d(this.javaClass.toString(), str)
                }
                val jArray : ArrayList<JSONObject> = ArrayList()
                try{
                    UUID.fromString(jsonObjStrArr.get(0))
                    //sync key is at top
                    syncKey = UUID.fromString(jsonObjStrArr.removeAt(0))
                } catch (e:IllegalArgumentException) {
                    try {
                        UUID.fromString(jsonObjStrArr.get(jsonObjStrArr.lastIndex))
                        //sync key is at bottom
                        syncKey = UUID.fromString(jsonObjStrArr.removeAt(jsonObjStrArr.lastIndex))
                    } catch (e:IllegalArgumentException) {
                        //no sync key!
                        Log.e(this.javaClass.toString(), "Error parsing sync data, no sync key.", e)
                        val bar = Snackbar.make(task_list_parent, "Sync Failed.", Snackbar.LENGTH_SHORT)
                        bar.view.setBackgroundColor(Color.parseColor("#34309f"))
                        bar.show()
                        return
                    }
                }
                for (str : String in jsonObjStrArr) {
                    jArray.add(JSONObject(str))
                }
                for (i in 0..jArray.size)
                {
                    try {
                        val uuid = jArray.get(i).get("uuid")
                        var task : Task? = null
                        for(t : Task in LocalTasks.items){
                            if (t.uuid == uuid){
                                task = t
                            }
                        }
                        if(task == null) {
                            task = Task("")
                        }
                        task.name = jArray.get(i).getString("description")
                        task.project = jArray.get(i).getString("project")
                        task.status = jArray.get(i).getString("status")
                        task.priority = when (jArray.get(i).getString("priority")) {
                            "H" -> Task.Priority.H
                            "M" -> Task.Priority.M
                            "L" -> Task.Priority.L
                            else -> null
                        }
                        var convDate : Date? = null
                        convDate = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'").parse(jArray.get(i).getString("due"))
                        task.dueDate = convDate
                        convDate = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'").parse(jArray.get(i).getString("modified"))
                        task.modifiedDate = convDate

                        val jsontags = jArray.get(i).getJSONArray("tags")
                        for (j in 0..jsontags.length()) {
                            task.tags.add(jsontags.getJSONObject(j).keys().next())
                        }
                        task.unsupported = rcvdmessage.payload.toString().replaceFirst("Optional[", "")

                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }


                val bar = Snackbar.make(task_list_parent, "Sync successful!", Snackbar.LENGTH_SHORT)
                bar.view.setBackgroundColor(Color.parseColor("#34309f"))
                bar.show()
            }

        }
    }

}
