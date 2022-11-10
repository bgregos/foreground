package me.bgregos.foreground.tasklist

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.bgregos.foreground.ForegroundListWidgetProvider
import me.bgregos.foreground.R
import me.bgregos.foreground.getApplicationComponent
import me.bgregos.foreground.util.NotificationRepository
import me.bgregos.foreground.util.launchLifecycleAware
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

        Log.d("widget", "checking intent extras: empty=${intent.extras?.isEmpty.toString()}")
        Log.d("widget", "intent action: ${intent.action}")
        intent.extras?.getString("me.bgregos.foreground.taskUuidToOpen")?.let {
            Log.d("widget", "uuid to open found: $it")
            lifecycleScope.launch {
                taskViewModel.requestOpenTask(it) }
        }
    }

    override fun onPause() {
        notificationRepository.save()
        super.onPause()
    }
}
