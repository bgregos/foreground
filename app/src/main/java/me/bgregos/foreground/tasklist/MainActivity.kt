package me.bgregos.foreground.tasklist

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentTransaction
import me.bgregos.foreground.ForegroundListWidgetProvider
import me.bgregos.foreground.R
import me.bgregos.foreground.getApplicationComponent
import me.bgregos.foreground.util.NotificationRepository
import javax.inject.Inject

class MainActivity : AppCompatActivity() {

    private lateinit var fragment: TaskListFragment

    @Inject lateinit var notificationRepository: NotificationRepository

    @Inject lateinit var taskViewModel: TaskViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getApplicationComponent().inject(this)
        setContentView(R.layout.activity_main)
        val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
        fragment = TaskListFragment.newInstance()
        transaction.replace(R.id.task_list_container, fragment)
        transaction.commit()
        getApplicationComponent().inject(taskViewModel)
        val i: Intent = Intent(this.applicationContext, ForegroundListWidgetProvider::class.java)
        this.applicationContext.sendBroadcast(i)
    }

    override fun onPause() {
        notificationRepository.save()
        super.onPause()
    }
}
