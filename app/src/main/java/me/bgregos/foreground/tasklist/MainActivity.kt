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
import me.bgregos.foreground.getApplicationComponent
import me.bgregos.foreground.util.NotificationRepository
import javax.inject.Inject

class MainActivity : AppCompatActivity() {

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private var twoPane: Boolean = false
    private lateinit var fragment: TaskListFragment

    @Inject lateinit var localTasksRepository: TaskRepository

    @Inject lateinit var notificationRepository: NotificationRepository


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getApplicationComponent().inject(this)
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

    override fun onPause() {
        notificationRepository.save()
        super.onPause()
    }
}
