package me.bgregos.foreground

import android.app.Activity
import android.content.*
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.Image
import android.opengl.Visibility
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import android.util.Log
import android.view.*
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.jakewharton.threetenabp.AndroidThreeTen
import kotlinx.android.synthetic.main.activity_task_list.*
import kotlinx.android.synthetic.main.task_list.*
import kotlinx.android.synthetic.main.task_list_content.view.*
import kotlinx.coroutines.*
import me.bgregos.foreground.R.id.*
import me.bgregos.foreground.task.LocalTasks
import me.bgregos.foreground.task.NotificationService
import me.bgregos.foreground.task.RemoteTaskManager
import me.bgregos.foreground.task.Task
import me.bgregos.foreground.util.ShowErrorDetail
import org.w3c.dom.Text
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class TaskListActivity : AppCompatActivity(), PopupMenu.OnMenuItemClickListener {

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private var twoPane: Boolean = false
    private var PROPERTIES_TASKWARRIOR : URL? = null
    var syncButton: View? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidThreeTen.init(this)
        LocalTasks.load(this)
        NotificationService.load(this)
        NotificationService.createNotificationChannel(this)

        setContentView(R.layout.activity_task_list)
        PROPERTIES_TASKWARRIOR = File(this.filesDir, "taskwarrior.properties").toURI().toURL()
        toolbar.navigationIcon = null
        toolbar.title = ""
        toolbar.subtitle = ""
        setSupportActionBar(toolbar)
        fab.setOnClickListener { view ->
            val newTask = Task("New Task")
            LocalTasks.items.add(newTask)
            openTask(newTask, view, newTask.name)
        }

        if (task_detail_container != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            twoPane = true
        }

        syncButton = this.findViewById<View>(action_sync)

        LocalTasks.updateVisibleTasks()
        setupRecyclerView(task_list)
    }

    override fun onResume() {
        super.onResume()
        val intentFilter = IntentFilter().apply {
            addAction("BRIGHTTASK_REMOTE_TASK_UPDATE")
            addAction("BRIGHTTASK_LOCAL_TASK_UPDATE")
        }
        val lbm = LocalBroadcastManager.getInstance(this)
        lbm.registerReceiver(broadcastReceiver, intentFilter)
        Log.d("broadcast", "Broadcast receiver registered")
        CoroutineScope(Dispatchers.Main).launch {
            LocalTasks.load(this@TaskListActivity, true)
            LocalTasks.updateVisibleTasks()
            updatePendingNotifications()
            setupRecyclerView(task_list)
        }

    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(broadcastReceiver)
        Log.d("broadcast", "Broadcast receiver unregistered")
        NotificationService.save(this)
        LocalTasks.save(this, true)
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    private fun updatePendingNotifications() {
        NotificationService.scheduleNotificationForTasks(LocalTasks.visibleTasks, this)
    }

    fun onSyncClick(item: MenuItem) {
        val syncRotateAnimation = RotateAnimation(360f, 0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        syncRotateAnimation.duration = 1000
        syncRotateAnimation.repeatCount = Animation.INFINITE
        val syncButton = this.findViewById<View>(R.id.action_sync)
        syncButton.clearAnimation()
        syncButton.startAnimation(syncRotateAnimation)

        val prefs = this.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
        if (prefs.getBoolean("settings_sync", false)){
            val bar = Snackbar.make(task_list_parent, "Syncing...", Snackbar.LENGTH_SHORT)
            bar.view.setBackgroundColor(Color.parseColor("#34309f"))
            bar.show()
            CoroutineScope(Dispatchers.Main).launch {
                LocalTasks.save(this@TaskListActivity, true)
                var syncResult: RemoteTaskManager.SyncResult = RemoteTaskManager(this@TaskListActivity).taskwarriorSync()
                var bar: Snackbar?
                if(syncResult.success){
                    val bar = Snackbar.make(task_list_parent, "Sync Successful", Snackbar.LENGTH_SHORT)
                    bar.view.setBackgroundColor(Color.parseColor("#34309f"))
                    bar.show()
                }else{
                    val bar = Snackbar.make(task_list_parent, "Sync Failed: ${syncResult.message}", Snackbar.LENGTH_LONG)
                    bar.view.setBackgroundColor(Color.parseColor("#34309f"))
                    bar.setAction("Details", ShowErrorDetail(syncResult.message, this@TaskListActivity))
                    bar.show()
                }
                task_list.adapter?.notifyDataSetChanged()
                syncRotateAnimation.repeatCount = 0
            }
        } else {
            val bar = Snackbar.make(task_list_parent, "Sync is disabled! Enable it in the settings menu.", Snackbar.LENGTH_LONG)
            bar.view.setBackgroundColor(Color.parseColor("#34309f"))
            bar.show()
            syncRotateAnimation.repeatCount = 0
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

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                "BRIGHTTASK_REMOTE_TASK_UPDATE" -> {
                    val syncRotateAnimation = RotateAnimation(360f, 0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
                    syncRotateAnimation.duration = 1000
                    syncRotateAnimation.repeatCount = 0
                    syncButton?.clearAnimation()
                    syncButton?.startAnimation(syncRotateAnimation)
                    LocalTasks.load(context, true)
                    LocalTasks.updateVisibleTasks()
                    task_list.adapter?.notifyDataSetChanged()
                    Log.i("auto_sync", "Task List received auto-update")
                }

                "BRIGHTTASK_LOCAL_TASK_UPDATE" -> {
                    LocalTasks.load(context, true)
                    LocalTasks.updateVisibleTasks()
                    task_list.adapter?.notifyDataSetChanged()
                    Log.i("task_list", "Task list updated by detail")
                }
            }
        }
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
            this.setHasStableIds(false)
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
            val format = SimpleDateFormat("MMM d, yyyy 'at' h:mm aaa", Locale.getDefault())
            val item = values[position]
            holder.title.text = item.name
            if(item.dueDate != null) {
                holder.due.visibility = VISIBLE
                holder.dueicon.visibility = VISIBLE
                holder.due.text = format.format((item.dueDate as Date).toLocal())
            }else{
                holder.dueicon.visibility = GONE
                holder.due.visibility = GONE
            }
            if(item.project.isNullOrEmpty()){
                holder.project.visibility = GONE
                holder.projecticon.visibility = GONE
                //remove margin from tags icon so it lines up with where this was
                val param = holder.tagsicon.layoutParams as ViewGroup.MarginLayoutParams
                param.marginStart = 0
                holder.tagsicon.layoutParams = param
            }else{
                holder.project.visibility = VISIBLE
                holder.projecticon.visibility = VISIBLE
                holder.project.text = item.project
            }
            if(item.tags.size == 0 || item.tags[0].isBlank()){
                holder.tags.visibility = GONE
                holder.tagsicon.visibility = GONE
            }else{
                holder.tags.visibility = VISIBLE
                holder.tagsicon.visibility = VISIBLE
                holder.tags.text = item.tags.joinToString(", ")
            }
            val color = ColorDrawable(when (item.priority) {
                "H" -> Color.parseColor("#550000")
                "M" -> Color.parseColor("#666600")
                "L" -> Color.parseColor("#303066")
                else -> Color.parseColor("#373737")
            })
            holder.accent.background = color

            with(holder.itemView) {
                tag = item
                setOnClickListener(onClickListener)
            }

            holder.complete.setOnClickListener {
                val pos = holder.layoutPosition
                values.removeAt(pos)
                notifyItemRemoved(pos)
                //notifyItemRangeChanged(pos, values.size)
                item.modifiedDate=Date().toUtc() //update modified date
                item.status = "completed"
                if (!LocalTasks.localChanges.contains(item)){
                    LocalTasks.localChanges.add(item)
                }
            }
        }

        override fun getItemCount() = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.title
            val due: TextView = view.due
            val dueicon: ImageView = view.ic_date
            val project: TextView = view.project
            val projecticon: ImageView = view.ic_proj
            val tags: TextView = view.tags
            val tagsicon: ImageView = view.ic_tags
            val accent: View = view.task_list_card_accentbar
            val complete: ImageView = view.complete
        }

    }

}
