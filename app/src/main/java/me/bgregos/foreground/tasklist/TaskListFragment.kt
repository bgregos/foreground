package me.bgregos.foreground.tasklist

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.Toolbar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import kotlinx.android.synthetic.main.fragment_task_list.*
import kotlinx.android.synthetic.main.task_list.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bgregos.foreground.R
import me.bgregos.foreground.filter.FiltersFragment
import me.bgregos.foreground.model.Task
import me.bgregos.foreground.network.RemoteTasks
import me.bgregos.foreground.settings.SettingsActivity
import me.bgregos.foreground.util.NotificationService
import me.bgregos.foreground.util.ShowErrorDetail
import java.io.File
import java.net.URL


class TaskListFragment : Fragment() {

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private var twoPane: Boolean = false
    private var PROPERTIES_TASKWARRIOR : URL? = null
    private var syncButton: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setHasOptionsMenu(true)
        twoPane = arguments?.getBoolean(TWO_PANE_ARG) ?: savedInstanceState?.getBoolean(TWO_PANE_ARG) ?: false
        super.onCreate(savedInstanceState)
    }

    override fun onAttach(context: Context) {
        LocalTasks.load(context)
        NotificationService.load(context)
        NotificationService.createNotificationChannel(context)

        PROPERTIES_TASKWARRIOR = File(context.filesDir, "taskwarrior.properties").toURI().toURL()

        LocalTasks.updateVisibleTasks()
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_task_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        syncButton = view.findViewById(R.id.action_sync)
        toolbar.inflateMenu(R.menu.menu_main)
        toolbar.navigationIcon = null
        toolbar.title = ""
        (activity as AppCompatActivity?)!!.setSupportActionBar(toolbar)
        toolbar.setOnMenuItemClickListener { item ->
            when (item?.itemId) {
                R.id.action_sync -> onSyncClick(item)
                R.id.action_filters -> onFiltersClick(item)
                R.id.action_settings -> onSettingsClick(item)
                else -> false
            }
        }
        task_list?.let { setupRecyclerView(it) }
        fab.setOnClickListener { view ->
            val newTask = Task("")
            LocalTasks.items.add(newTask)
            openTask(newTask, view, newTask.name)
        }
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        CoroutineScope(Dispatchers.Main).launch {
            LocalTasks.load(context ?: return@launch, true)
            LocalTasks.updateVisibleTasks()
            updatePendingNotifications()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_main, menu)
        super.onCreateOptionsMenu(menu, inflater);
    }

    private fun updatePendingNotifications() {
        NotificationService.scheduleNotificationForTasks(LocalTasks.visibleTasks, requireContext())
    }

    fun onSyncClick(item: MenuItem): Boolean {
        val syncRotateAnimation = RotateAnimation(360f, 0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        syncRotateAnimation.duration = 1000
        syncRotateAnimation.repeatCount = Animation.INFINITE
        val syncButton = view?.findViewById<View>(R.id.action_sync) ?:  return true
        syncButton.clearAnimation()
        syncButton.startAnimation(syncRotateAnimation)

        val prefs = activity?.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE) ?: return true
        if (prefs.getBoolean("settings_sync", false)){
            val bar = Snackbar.make(task_list_parent, "Syncing...", Snackbar.LENGTH_SHORT)
            bar.view.setBackgroundColor(Color.parseColor("#34309f"))
            bar.show()
            CoroutineScope(Dispatchers.Main).launch {
                LocalTasks.save(requireContext(), true)
                var syncResult: RemoteTasks.SyncResult = RemoteTasks(requireContext()).taskwarriorSync()
                if(syncResult.success){
                    val bar2 = Snackbar.make(task_list_parent, "Sync Successful", Snackbar.LENGTH_SHORT)
                    bar2.view.setBackgroundColor(Color.parseColor("#34309f"))
                    bar2.show()
                }else{
                    val bar2 = Snackbar.make(task_list_parent, "Sync Failed: ${syncResult.message}", Snackbar.LENGTH_LONG)
                    bar2.view.setBackgroundColor(Color.parseColor("#34309f"))
                    bar2.setAction("Details", ShowErrorDetail(syncResult.message, requireActivity()))
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
        return true
    }

    fun onFiltersClick(item: MenuItem): Boolean {
        val fragment = FiltersFragment.newInstance()
        if(twoPane){
            activity?.supportFragmentManager
                    ?.beginTransaction()
                    ?.replace(R.id.task_detail_container, fragment)
                    ?.commit()
        } else {
            activity?.supportFragmentManager
                    ?.beginTransaction()
                    ?.replace(R.id.task_list_container, fragment)
                    ?.addToBackStack("filters")
                    ?.commit()
        }
        return true
    }

    fun onSettingsClick(item: MenuItem): Boolean {
        startActivity(Intent(context, SettingsActivity::class.java))
        return true
    }

    private fun setupRecyclerView(recyclerView: RecyclerView) {
        recyclerView.adapter = TaskListAdapter(this, LocalTasks.visibleTasks)
    }

    companion object {

        val TWO_PANE_ARG = "twoPane"

        @JvmStatic
        fun newInstance(twoPane: Boolean) =
                TaskListFragment().apply {
                    arguments = Bundle().apply {
                        putBoolean("twoPane", twoPane)
                    }
                }
    }

    fun openTask(task: Task, v: View, name: String){
        val fragment = TaskDetailFragment.newInstance(task.uuid)
        LocalTasks.updateVisibleTasks()
        task_list.adapter?.notifyDataSetChanged()
        if (twoPane) {
            // Tablet layouts get the task detail fragment opened on the side
            activity?.supportFragmentManager
                    ?.beginTransaction()
                    ?.replace(R.id.task_detail_container, fragment)
                    ?.commit()
        } else {
            //phones get the task detail fragment replacing this one
            activity?.supportFragmentManager
                    ?.beginTransaction()
                    ?.replace(R.id.task_list_container, fragment)
                    ?.addToBackStack("task_detail")
                    ?.commit()
        }
    }
}