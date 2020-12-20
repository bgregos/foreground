package me.bgregos.foreground

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import kotlinx.android.synthetic.main.activity_task_detail.*
import me.bgregos.foreground.task.LocalTasks
import java.util.*

/**
 * An activity representing a single Task detail screen. This
 * activity is only used on narrow width devices. On tablet-size devices,
 * item details are presented side-by-side with a list of items
 * in a [TaskListActivity].
 */
class TaskDetailActivity : AppCompatActivity() {

    var uuid:UUID = UUID.randomUUID();

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_detail)
        setSupportActionBar(detail_toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Show the Up button in the action bar.
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // savedInstanceState is non-null when there is fragment state
        // saved from previous configurations of this activity
        // (e.g. when rotating the screen from portrait to landscape).
        // In this case, the fragment will automatically be re-added
        // to its container so we don't need to manually add it.
        // For more information, see the Fragments API guide at:
        //
        // http://developer.android.com/guide/components/fragments.html
        //
        if (savedInstanceState == null) {
            // Create the detail fragment and add it to the activity
            // using a fragment transaction.
            val fragment = TaskDetailFragment()
            val bundle = Bundle()

            bundle.putString("uuid", intent.extras?.getString("uuid"))
            uuid = UUID.fromString(intent.extras?.getString("uuid"))
            fragment.arguments = bundle
            updateToolbar(intent.extras?.getString("displayName"))

            supportFragmentManager.beginTransaction()
                    .add(R.id.task_detail_container, fragment)
                    .commit()

        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_task_detail, menu)
        return true
    }

    fun onDeleteClick(item: MenuItem) {
        val task = LocalTasks.getTaskByUUID(uuid)
        if (task != null){
            task.modifiedDate=Date() //update modified date
            task.status="deleted"
            if (!LocalTasks.localChanges.contains(task)){
                LocalTasks.localChanges.add(task)
            }
            LocalTasks.updateVisibleTasks()
            LocalTasks.save(this.applicationContext)
            navigateUpTo(Intent(this, TaskListActivity::class.java))
        }
    }

    fun onCompleteClick(item: MenuItem) {
        val task = LocalTasks.getTaskByUUID(uuid)
        if (task != null){
            task.modifiedDate=Date() //update modified date
            task.status = "completed"
            if (!LocalTasks.localChanges.contains(task)){
                LocalTasks.localChanges.add(task)
            }
            LocalTasks.updateVisibleTasks()
            LocalTasks.save(this.applicationContext)
            navigateUpTo(Intent(this, TaskListActivity::class.java))
        }
    }

    private fun updateToolbar(name: String?){
        detail_toolbar.title = if(name.isNullOrEmpty()) "New Task" else name
    }

    override fun onOptionsItemSelected(item: MenuItem) =
            when (item.itemId) {
                android.R.id.home -> {
                    // This ID represents the Home or Up button. In the case of this
                    // activity, the Up button is shown. For
                    // more details, see the Navigation pattern on Android Design:
                    //
                    // http://developer.android.com/design/patterns/navigation.html#up-vs-back

                    navigateUpTo(Intent(this, TaskListActivity::class.java))
                    true
                }
                else -> super.onOptionsItemSelected(item)
            }
}
