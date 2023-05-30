package me.bgregos.foreground.tasklist

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentTransaction
import me.bgregos.foreground.ForegroundListWidgetProvider
import me.bgregos.foreground.ForegroundListWidgetUpdater
import me.bgregos.foreground.R
import me.bgregos.foreground.getApplicationComponent
import me.bgregos.foreground.util.NotificationRepository
import javax.inject.Inject

class MainActivity : AppCompatActivity() {

    private lateinit var fragment: TaskListFragment

    @Inject lateinit var notificationRepository: NotificationRepository

    @Inject lateinit var taskViewModel: TaskViewModel

    @Inject lateinit var widgetUpdater: ForegroundListWidgetUpdater

    companion object {
        //intent action for opening a task
        const val BRIGHTTASK_OPEN_TASK = "me.bgregos.brighttask.OPEN_TASK"

        //intent extra property for opening a task
        const val BRIGHTTASK_OPEN_TASK_PARAM_UUID = "OPEN_TASK_PARAM_UUID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getApplicationComponent().inject(this)
        setContentView(R.layout.activity_main)
        val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
        val taskUUIDToOpen = intent.getStringExtra(BRIGHTTASK_OPEN_TASK_PARAM_UUID)
        fragment = TaskListFragment.newInstance(taskUUIDToOpen)
        transaction.replace(R.id.task_list_container, fragment)
        transaction.commit()
        getApplicationComponent().inject(taskViewModel)
        val i = Intent(this.applicationContext, ForegroundListWidgetProvider::class.java)
        this.applicationContext.sendBroadcast(i)
    }

    override fun onResume() {
        super.onResume()
        widgetUpdater.updateWidget()
    }

    override fun onPause() {
        notificationRepository.save()
        widgetUpdater.updateWidget()
        super.onPause()
    }
}
