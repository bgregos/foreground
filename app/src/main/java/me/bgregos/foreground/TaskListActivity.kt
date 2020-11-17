package me.bgregos.foreground

import android.app.Activity
import android.content.*
import android.graphics.Color
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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

        syncButton = this.findViewById<View>(action_sync)

        LocalTasks.updateVisibleTasks()
        setupRecyclerView(task_list)
    }

    override fun onResume() {
        super.onResume()
        var intentFilter = IntentFilter()
        intentFilter.addAction("BRIGHTTASK_REMOTE_TASK_UPDATE")
        var lbm = LocalBroadcastManager.getInstance(this)
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
                var syncResult: RemoteTaskManager.SyncResult = RemoteTaskManager(this@TaskListActivity).taskwarriorSync()
                var bar: Snackbar?
                if(syncResult.success){
                    val bar = Snackbar.make(task_list_parent, "Sync Successful", Snackbar.LENGTH_SHORT)
                    bar.view.setBackgroundColor(Color.parseColor("#34309f"))
                    bar.show()
                }else{
                    val bar = Snackbar.make(task_list_parent, "Sync Failed: ${syncResult.message}", Snackbar.LENGTH_LONG)
                    bar.view.setBackgroundColor(Color.parseColor("#34309f"))
                    bar.setAction("Details", SyncErrorDetail(syncResult.message, this@TaskListActivity))
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

    private class SyncErrorDetail(val error: String, val activity: Activity): View.OnClickListener {
        override fun onClick(view: View?) {
            AlertDialog.Builder(activity).let {
                it.setTitle("Sync Failed")
                it.setMessage(error)
                it.setPositiveButton("Close", DialogInterface.OnClickListener { _, _ -> Unit })
                it.setNeutralButton("Copy", DialogInterface.OnClickListener {_, _ ->
                    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip: ClipData = ClipData.newPlainText("foreground_error", error)
                    clipboard.setPrimaryClip(clip)
                })
                it.create()
            }.show()
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

                    LocalTasks.updateVisibleTasks()
                    task_list.adapter?.notifyDataSetChanged()
                    Log.i("auto_sync", "Task List received auto-update")
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
                holder.due.text = format.format((item.dueDate as Date).toLocal())
            }

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
            val complete: ImageView = view.complete
        }

    }

}
