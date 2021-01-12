package me.bgregos.foreground.tasklist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_task_list.*
import kotlinx.android.synthetic.main.task_list.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bgregos.foreground.R
import me.bgregos.foreground.R.id.action_filters
import me.bgregos.foreground.R.id.action_sync
import me.bgregos.foreground.util.NotificationService
import me.bgregos.foreground.network.RemoteTasks
import me.bgregos.foreground.model.Task
import me.bgregos.foreground.filter.FiltersFragment
import me.bgregos.foreground.settings.SettingsActivity
import me.bgregos.foreground.util.ShowErrorDetail
import java.io.File
import java.net.URL

class TaskListActivity : AppCompatActivity() {

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private var twoPane: Boolean = false
    private var PROPERTIES_TASKWARRIOR : URL? = null
    var syncButton: View? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            addAction("BRIGHTTASK_TABLET_LOCAL_TASK_UPDATE")
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
        syncButton?.clearAnimation()
        syncButton?.startAnimation(syncRotateAnimation)

        val prefs = this.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
        if (prefs.getBoolean("settings_sync", false)){
            val bar = Snackbar.make(task_list_parent, "Syncing...", Snackbar.LENGTH_SHORT)
            bar.view.setBackgroundColor(Color.parseColor("#34309f"))
            bar.show()
            CoroutineScope(Dispatchers.Main).launch {
                LocalTasks.save(this@TaskListActivity, true)
                var syncResult: RemoteTasks.SyncResult = RemoteTasks(this@TaskListActivity).taskwarriorSync()
                if(syncResult.success){
                    val bar2 = Snackbar.make(task_list_parent, "Sync Successful", Snackbar.LENGTH_SHORT)
                    bar2.view.setBackgroundColor(Color.parseColor("#34309f"))
                    bar2.show()
                }else{
                    val bar2 = Snackbar.make(task_list_parent, "Sync Failed: ${syncResult.message}", Snackbar.LENGTH_LONG)
                    bar2.view.setBackgroundColor(Color.parseColor("#34309f"))
                    bar2.setAction("Details", ShowErrorDetail(syncResult.message, this@TaskListActivity))
                    bar2.show()
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

    fun onFiltersClick(item: MenuItem) {
        if(twoPane){
            val fragment = FiltersFragment()
            this.supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.task_detail_container, fragment)
                    .commit()
        }
    }

    fun onSettingsClick(item: MenuItem) {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun setupRecyclerView(recyclerView: RecyclerView) {
        recyclerView.adapter = TaskListAdapter(this, LocalTasks.visibleTasks, twoPane)
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

                "BRIGHTTASK_TABLET_LOCAL_TASK_UPDATE" -> {
                    if(twoPane){
                        LocalTasks.load(context, true)
                        LocalTasks.updateVisibleTasks()
                        task_list.adapter?.notifyDataSetChanged()
                        Log.i("task_list", "Task list updated by detail")
                    }
                }
            }
        }
    }

    fun openTask(task: Task, v: View, name: String){
        // Tablet layouts get the task detail fragment opened on the side
        if (twoPane) {
            val fragment = TaskDetailFragment().apply {
                arguments = Bundle().apply {
                    putString("uuid", task.uuid.toString())
                    putString("displayName", name)
                    putBoolean("twoPane", true)
                }
            }
            LocalTasks.updateVisibleTasks()
            task_list.adapter?.notifyDataSetChanged()
            this.supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.task_detail_container, fragment)
                    .commit()
        } else {
            //phones get the task detail fragment in a new activity
            val intent = Intent(v.context, TaskDetailActivity::class.java)
            intent.putExtra("uuid", task.uuid.toString())
            intent.putExtra("displayName", name)
            intent.putExtra("twoPane", false)
            v.context.startActivity(intent)
        }
    }

}
