package me.bgregos.foreground.tasklist

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bgregos.foreground.R
import me.bgregos.foreground.databinding.FragmentTaskListBinding
import me.bgregos.foreground.databinding.TaskListBinding
import me.bgregos.foreground.filter.FiltersFragment
import me.bgregos.foreground.getApplicationComponent
import me.bgregos.foreground.model.SyncResult
import me.bgregos.foreground.model.Task
import me.bgregos.foreground.settings.SettingsActivity
import me.bgregos.foreground.util.*
import java.util.UUID
import javax.inject.Inject

class TaskListFragment : Fragment() {

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private var twoPane: Boolean = false

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var binding: FragmentTaskListBinding
    private lateinit var taskListBinding: TaskListBinding


    lateinit var viewModel: TaskViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        setHasOptionsMenu(true)
        super.onCreate(savedInstanceState)
        //use activity viewmodel store since this viewmodel is shared between fragments
        viewModel = ViewModelProvider(requireActivity().viewModelStore, viewModelFactory).get(TaskViewModel::class.java)
        arguments?.getString("uuid")?.let {
            viewModel.getTaskByUUID(it)?.let { task ->
                openTask(task)
            }
        }
    }

    override fun onAttach(context: Context) {
        context.getApplicationComponent().inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        binding = FragmentTaskListBinding.inflate(inflater, container, false)
        taskListBinding = TaskListBinding.bind(binding.taskListInclude.root)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        context?.let { twoPane = it.isSideBySide() }
        binding.toolbar.inflateMenu(R.menu.menu_main)
        binding.toolbar.navigationIcon = null
        binding.toolbar.title = ""
        (activity as AppCompatActivity?)?.setSupportActionBar(binding.toolbar)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item?.itemId) {
                R.id.action_sync -> onSyncClick()
                R.id.action_filters -> onFiltersClick(item)
                R.id.action_settings -> onSettingsClick(item)
                else -> false
            }
        }
        setupRecyclerView(taskListBinding.taskList)
        binding.fab.setOnClickListener {
            viewModel.removeUnnamedTasks()
            val newTask = viewModel.addTask()
            openTask(newTask)
        }
        taskListBinding.swipeRefreshLayout.setOnRefreshListener {
            onSyncClick()
        }
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_main, menu)
        super.onCreateOptionsMenu(menu, inflater);
    }

    fun onSyncClick(): Boolean {
        val syncRotateAnimation = RotateAnimation(360f, 0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        syncRotateAnimation.duration = 1000
        syncRotateAnimation.repeatCount = Animation.INFINITE
        val syncButton = view?.findViewById<View>(R.id.action_sync) ?:  return true
        syncButton.clearAnimation()
        syncButton.startAnimation(syncRotateAnimation)

        val bar1 = Snackbar.make(binding.taskListParent, "Syncing...", Snackbar.LENGTH_SHORT)
        bar1.view.setBackgroundColor(Color.parseColor("#34309f"))
        bar1.show()

        val prefs = activity?.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE) ?: return true
        if (prefs.getBoolean("settings_sync", false)){
            CoroutineScope(Dispatchers.Main).launch {
                val syncResult: SyncResult = viewModel.sync()
                if(syncResult.success){
                    val bar2 = Snackbar.make(binding.taskListParent, "Sync Successful", Snackbar.LENGTH_SHORT)
                    bar2.view.setBackgroundColor(Color.parseColor("#34309f"))
                    bar2.show()
                }else{
                    val bar2 = Snackbar.make(binding.taskListParent, "Sync Failed: ${syncResult.message}", Snackbar.LENGTH_LONG)
                    bar2.view.setBackgroundColor(Color.parseColor("#34309f"))
                    bar2.setAction("Details", ShowErrorDetail(syncResult.message, requireActivity()))
                    bar2.show()
                }
                taskListBinding.swipeRefreshLayout.isRefreshing = false
                syncRotateAnimation.repeatCount = 0
            }
        } else {
            taskListBinding.swipeRefreshLayout.isRefreshing = false
            val bar = Snackbar.make(binding.taskListParent, "Sync is disabled! Enable it in the settings menu.", Snackbar.LENGTH_LONG)
            bar.view.setBackgroundColor(Color.parseColor("#34309f"))
            bar.show()
            syncRotateAnimation.repeatCount = 0
        }
        return true
    }

    fun onFiltersClick(item: MenuItem): Boolean {
        val fragment = FiltersFragment.newInstance()
        if (twoPane) {
            // Tablet layouts get the task detail fragment opened on the side
            activity?.supportFragmentManager?.commit {
                tabletDetailAnimations()
                replace(R.id.task_detail_container, fragment)
            }
        } else {
            //phones get the task detail fragment replacing this one
            activity?.supportFragmentManager?.commit {
                phoneDetailAnimations()
                replace(R.id.task_list_container, fragment)
                addToBackStack("task_detail")
            }
        }
        return true
    }

    fun onSettingsClick(item: MenuItem): Boolean {
        startActivity(Intent(context, SettingsActivity::class.java))
        return true
    }

    private fun setupRecyclerView(recyclerView: RecyclerView) {
        recyclerView.adapter = TaskListAdapter(this@TaskListFragment, viewModel)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.visibleTasks.collect {
                    (recyclerView.adapter as TaskListAdapter).submitList(it)
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(taskUUIDToOpen: String? = null) = TaskListFragment().apply {
            arguments = Bundle().apply {
                putString("uuid", taskUUIDToOpen)
            }
        }
    }

    fun openTask(task: Task){
        val fragment = TaskDetailFragment.newInstance(task.uuid)
        if (twoPane) {
            // Tablet layouts get the task detail fragment opened on the side
            activity?.supportFragmentManager?.commit {
                tabletDetailAnimations()
                replace(R.id.task_detail_container, fragment)
            }
        } else {
            //phones get the task detail fragment replacing this one
            activity?.supportFragmentManager?.commit {
                phoneDetailAnimations()
                replace(R.id.task_list_container, fragment, "task_detail")
                addToBackStack("task_detail")
            }
        }
    }
}
