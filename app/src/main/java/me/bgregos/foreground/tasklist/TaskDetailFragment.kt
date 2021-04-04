package me.bgregos.foreground.tasklist

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.android.synthetic.main.date_layout.*
import kotlinx.android.synthetic.main.date_layout.view.*
import kotlinx.android.synthetic.main.fragment_task_detail.*
import kotlinx.android.synthetic.main.fragment_task_detail.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bgregos.foreground.R
import me.bgregos.foreground.databinding.FragmentTaskDetailBinding
import me.bgregos.foreground.getApplicationComponent
import me.bgregos.foreground.util.isSideBySide
import me.bgregos.foreground.util.tabletDetailAnimations
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * A fragment representing a single Task detail screen.
 * This fragment is either contained in a [MainActivity]
 * in two-pane mode (on tablets) or a [TaskDetailActivity]
 * on handsets.
 */
class TaskDetailFragment : Fragment() {

    private var _binding: FragmentTaskDetailBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!


    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    lateinit var viewModel: TaskViewModel

    companion object {
        fun newInstance(uuid: UUID): TaskDetailFragment{
            return TaskDetailFragment().apply {
                arguments = Bundle().apply {
                    putString("uuid", uuid.toString())
                }
            }
        }
    }

    override fun onAttach(context: Context) {
        context.getApplicationComponent().inject(this)
        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //use activity viewmodel store since this viewmodel is shared between fragments
        viewModel = ViewModelProvider(requireActivity().viewModelStore, viewModelFactory).get(TaskViewModel::class.java)
        val bundle = this.arguments
        //load Task from bundle args
        if (bundle?.getString("uuid") != null) {
            viewModel.setTask(UUID.fromString(bundle.getString("uuid"))
                    ?: run { close(); UUID.randomUUID() })
        }
        else {
            Log.e(this.javaClass.toString(), "No key found.")
        }
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        _binding = FragmentTaskDetailBinding.inflate(inflater, container, false)
        val rootView = binding.root
        var twoPane = false
        context?.let { twoPane = it.isSideBySide() }

        rootView.detail_toolbar.title = if(viewModel.currentTask?.name?.isBlank() == true) "New Task" else viewModel.currentTask?.name
        setHasOptionsMenu(true)

        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        dateFormat.timeZone= TimeZone.getDefault()
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        timeFormat.timeZone= TimeZone.getDefault()

        if(!twoPane){
            (activity as AppCompatActivity?)?.setSupportActionBar(rootView.detail_toolbar)
            (activity as AppCompatActivity?)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
            rootView.detail_toolbar.menu.clear()
            rootView.detail_toolbar.setNavigationOnClickListener(View.OnClickListener { _ -> activity?.supportFragmentManager?.popBackStack() })
        }

        // Create an ArrayAdapter using the string array and a default spinner layout
        val adapter: ArrayAdapter<CharSequence> = ArrayAdapter.createFromResource(this.context
                ?: requireActivity().baseContext,
                R.array.priority_spinner, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        rootView.detail_priority.adapter = adapter

        //load from Task into input fields
        viewModel.currentTask?.let {
            rootView.detail_name.setText(it.name)
            if (it.priority != null) {
                rootView.detail_priority.setSelection(adapter.getPosition(it.priority))
            } else {
                rootView.detail_priority.setSelection(1)
            }

            rootView.detail_project.setText(it.project ?: "")
            val builder = StringBuilder()
            it.tags.forEach { task -> builder.append("$task, ") }
            rootView.detail_tags.setText(builder.toString().trimEnd(',', ' '))


            if(it.dueDate != null){
                rootView.detail_dueDate.date.setText(dateFormat.format(it.dueDate as Date))
                rootView.detail_dueDate.time.setText(timeFormat.format(it.dueDate as Date))
            }else{
                rootView.detail_dueDate.date.setText("")
                rootView.detail_dueDate.time.setText("")
            }
            if(it.waitDate != null){
                rootView.detail_waitDate.date.setText(dateFormat.format(it.waitDate as Date))
                rootView.detail_waitDate.time.setText(timeFormat.format(it.waitDate as Date))
            }else{
                rootView.detail_waitDate.date.setText("")
                rootView.detail_waitDate.time.setText("")
            }
        }

        // update viewmodel on all form changes
        rootView.detail_name.doOnTextChanged { text, _, _, _ -> viewModel.setTaskName(text.toString()) }
        rootView.detail_tags.doOnTextChanged { text, _, _, _ -> viewModel.setTaskTags(text.toString()) }
        rootView.detail_project.doOnTextChanged{ text, _, _, _ -> viewModel.setTaskProject(text.toString()) }
        rootView.detail_priority.onItemSelectedListener  = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                //no-op
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected: String = rootView.detail_priority.getItemAtPosition(position).toString()
                viewModel.setTaskPriority(selected)
            }
        }
//        rootView.detail_priority.setOnTouchListener { view, _ ->
//            view.performClick()
//            viewModel.setTaskPriority(rootView.detail_priority.selectedItem.toString())
//            true
//        }
//        rootView.detail_priority.setOnKeyListener { view, _, _ ->
//            view.performClick()
//            viewModel.setTaskPriority(rootView.detail_priority.selectedItem.toString())
//            true
//        }

        rootView.detail_waitDate.dateInputLayout.hint = "Wait Until Date"
        rootView.detail_waitDate.date.setOnClickListener {
            val c = Calendar.getInstance()
            val year = c.get(Calendar.YEAR)
            val month = c.get(Calendar.MONTH)
            val day = c.get(Calendar.DAY_OF_MONTH)
            val dpd = DatePickerDialog(this.requireContext(), { _, year, monthOfYear, dayOfMonth ->
                // Display Selected date in textbox
                detail_waitDate.date.setText(DateFormatSymbols().getMonths()[monthOfYear] + " " + dayOfMonth + ", " + year)
                if (detail_waitDate.time.text.isNullOrBlank()) {
                    detail_waitDate.time.setText("12:00 AM")
                }
                viewModel.setTaskWaitDate(detail_waitDate.date.text.toString(), detail_waitDate.time.text.toString())
            }, year, month, day)
            dpd.show()
        }

        rootView.detail_waitDate.time.setOnClickListener {
            val c = Calendar.getInstance()
            val hour:Int = c.get(Calendar.HOUR_OF_DAY)
            val minute:Int = c.get(Calendar.MINUTE)
            val dpd = TimePickerDialog(this.requireContext(), { _, hour, minute ->
                // Display Selected date in textbox
                val inputFormat = SimpleDateFormat("KK:mm", Locale.getDefault())
                val outputFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val timeText = outputFormat.format(inputFormat.parse("" + hour + ":" + minute))
                detail_waitDate.time.setText(timeText)
                if (detail_waitDate.date.text.isNullOrBlank()) {
                    val year = c.get(Calendar.YEAR)
                    val month = c.get(Calendar.MONTH)
                    val day = c.get(Calendar.DAY_OF_MONTH)
                    detail_waitDate.date.setText(DateFormatSymbols().shortMonths[month] + " " + day + ", " + year)
                }
                viewModel.setTaskWaitDate(detail_waitDate.date.text.toString(), detail_waitDate.time.text.toString())
            }, hour, minute, false)
            dpd.show()
        }

        rootView.detail_dueDate.dateInputLayout.hint = "Due Date"
        rootView.detail_dueDate.date.setOnClickListener {
            val c = Calendar.getInstance()
            val year = c.get(Calendar.YEAR)
            val month = c.get(Calendar.MONTH)
            val day = c.get(Calendar.DAY_OF_MONTH)
            val dpd = DatePickerDialog(this.requireContext(), { _, year, monthOfYear, dayOfMonth ->
                // Display Selected date in textbox
                val dateText = "${DateFormatSymbols().months[monthOfYear]} $dayOfMonth, $year"
                detail_dueDate.date.setText(dateText)
                if (detail_dueDate.time.text.isNullOrBlank()) {
                    detail_dueDate.time.setText("12:00 AM")
                }
                viewModel.setTaskDueDate(detail_dueDate.date.text.toString(), detail_dueDate.time.text.toString())
            }, year, month, day)
            dpd.show()
        }

        rootView.detail_dueDate.time.setOnClickListener {
            val c = Calendar.getInstance()
            val hour:Int = c.get(Calendar.HOUR_OF_DAY)
            val minute:Int = c.get(Calendar.MINUTE)
            val dpd = TimePickerDialog(this.requireContext(), { _, hour, minute ->
                // Display Selected date in textbox
                val inputFormat = SimpleDateFormat("KK:mm", Locale.getDefault())
                val outputFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val timeText = outputFormat.format(inputFormat.parse("" + hour + ":" + minute))
                detail_dueDate.time.setText(timeText)
                if (detail_dueDate.date.text.isNullOrBlank()) {
                    val year = c.get(Calendar.YEAR)
                    val month = c.get(Calendar.MONTH)
                    val day = c.get(Calendar.DAY_OF_MONTH)
                    detail_dueDate.date.setText("${DateFormatSymbols().shortMonths[month]} $day, $year")
                }
                viewModel.setTaskDueDate(detail_dueDate.date.text.toString(), detail_dueDate.time.text.toString())
            }, hour, minute, false)
            dpd.show()
        }

        val item = viewModel.currentTask

        // Subscribe to the task detail close channel
        lifecycleScope.launchWhenStarted {
            viewModel.closeDetailChannel.receive()
            Log.d("test", "closing detail view")
            if(twoPane){
                activity?.supportFragmentManager?.beginTransaction()?.tabletDetailAnimations()?.remove(this@TaskDetailFragment)?.commit()
            } else {
                activity?.supportFragmentManager?.popBackStack()
            }

        }

        //some strange issue requires this to be done or the view doesn't adjust
        //to match the content height
        //TODO: replace this spinner with a different ui element
        if (item?.priority == null || item.priority == "No Priority Assigned"){
            rootView.detail_priority.setSelection(0)
            rootView.detail_priority.setSelection(1)
            rootView.detail_priority.setSelection(0)
            rootView.detail_priority.setSelection(1)
            rootView.detail_priority.setSelection(0)
        }

        return rootView
    }

    private fun updateToolbar(name: String?){
        detail_toolbar.title = if(name.isNullOrEmpty()) "New Task" else name
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.action_delete -> {
                viewModel.currentTask?.let { viewModel.delete(it) }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPause() {
        //remove this task if it's blank - taskwarrior disallows tasks with no name
        viewModel.removeUnnamedTasks()
        CoroutineScope(Dispatchers.Main).launch {
            viewModel.save()
        }
        super.onPause()
    }
    private fun close(){
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)?.commit()
    }
}
