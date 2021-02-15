package me.bgregos.foreground.tasklist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentTransaction
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.task_list.*
import me.bgregos.foreground.R
import me.bgregos.foreground.util.NotificationService
import javax.inject.Inject

class MainActivity : AppCompatActivity() {

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private var twoPane: Boolean = false
    private lateinit var fragment: TaskListFragment

    @Inject lateinit var localTasksRepository: LocalTasksRepository


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (task_detail_container != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            twoPane = true
        }
        if (savedInstanceState == null) {
            val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
            fragment = TaskListFragment.newInstance(twoPane)
            transaction.replace(R.id.task_list_container, fragment)
            transaction.commit()
        }
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

    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(broadcastReceiver)
        Log.d("broadcast", "Broadcast receiver unregistered")
        NotificationService.save(this)
        localTasksRepository.save(true)
        super.onPause()
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                "BRIGHTTASK_REMOTE_TASK_UPDATE" -> {
                    val syncRotateAnimation = RotateAnimation(360f, 0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
                    syncRotateAnimation.duration = 1000
                    syncRotateAnimation.repeatCount = 0
                    localTasksRepository.load(true)
                    fragment.task_list?.adapter?.notifyDataSetChanged()
                    Log.i("auto_sync", "Task List received auto-update")
                }

                "BRIGHTTASK_TABLET_LOCAL_TASK_UPDATE" -> {
                    if (twoPane) {
                        localTasksRepository.load(true)
                        fragment.task_list?.adapter?.notifyDataSetChanged()
                        Log.i("task_list", "Task list updated by detail")
                    }
                }
            }
        }
    }

}
