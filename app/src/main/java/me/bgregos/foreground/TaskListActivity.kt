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
import android.view.animation.Animation
import android.view.animation.RotateAnimation
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
import kotlinx.coroutines.*
import me.bgregos.foreground.R.id.*
import me.bgregos.foreground.task.LocalTasks
import me.bgregos.foreground.task.LocalTasks.items
import me.bgregos.foreground.task.LocalTasks.localChanges
import me.bgregos.foreground.task.LocalTasks.updateVisibleTasks
import me.bgregos.foreground.task.LocalTasks.visibleTasks
import me.bgregos.foreground.task.RemoteTaskManager
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
        val r = RotateAnimation(360f, 0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        r.duration = 1000
        r.repeatCount = Animation.INFINITE
        val syncButton = this.findViewById<View>(R.id.action_sync)
        syncButton.clearAnimation()
        syncButton.startAnimation(r)

        val prefs = this.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
        if (prefs.getBoolean("settings_sync", false)){
            val bar = Snackbar.make(task_list_parent, "Syncing...", Snackbar.LENGTH_SHORT)
            bar.view.setBackgroundColor(Color.parseColor("#34309f"))
            bar.show()
            CoroutineScope(Dispatchers.Main).launch {
                var syncResult: RemoteTaskManager.SyncResult = RemoteTaskManager(this@TaskListActivity).taskwarriorSync()
                var bar: Snackbar?
                if(syncResult.success){
                    val bar = Snackbar.make(task_list_parent, "Sync Successful", Snackbar.LENGTH_SHORT)
                    bar.view.setBackgroundColor(Color.parseColor("#34309f"))
                    bar.show()
                }else{
                    val bar = Snackbar.make(task_list_parent, "Sync Failed: ${syncResult.message}", Snackbar.LENGTH_LONG)
                    bar.view.setBackgroundColor(Color.parseColor("#34309f"))
                    bar.show()
                }
                task_list.adapter?.notifyDataSetChanged()
                r.repeatCount = 0
            }
        } else {
            val bar = Snackbar.make(task_list_parent, "Sync is disabled! Enable it in the settings menu.", Snackbar.LENGTH_LONG)
            bar.view.setBackgroundColor(Color.parseColor("#34309f"))
            bar.show()
            r.repeatCount = 0
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
                item.modifiedDate=toUtc(Date()) //update modified date
                item.status = "completed"
                if (!LocalTasks.localChanges.contains(item)){
                    LocalTasks.localChanges.add(item)
                }
                LocalTasks.save(this.parentActivity.applicationContext)
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

}
