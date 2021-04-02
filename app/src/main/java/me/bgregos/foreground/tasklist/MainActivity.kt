package me.bgregos.foreground.tasklist

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentTransaction
import me.bgregos.foreground.R
import me.bgregos.foreground.getApplicationComponent
import me.bgregos.foreground.util.NotificationRepository
import javax.inject.Inject

class MainActivity : AppCompatActivity() {

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private lateinit var fragment: TaskListFragment

    @Inject lateinit var notificationRepository: NotificationRepository

    @Inject lateinit var taskRepository: NotificationRepository


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getApplicationComponent().inject(this)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
            fragment = TaskListFragment.newInstance()
            transaction.replace(R.id.task_list_container, fragment)
            transaction.commit()
        }
    }

    override fun onPause() {
        notificationRepository.save()
        taskRepository.save()
        super.onPause()
    }
}
