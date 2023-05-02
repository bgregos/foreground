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
import androidx.core.view.forEach
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bgregos.foreground.R
import me.bgregos.foreground.databinding.FragmentTaskDetailBinding
import me.bgregos.foreground.databinding.TaskDetailUserAttributeContentBinding
import me.bgregos.foreground.getApplicationComponent
import me.bgregos.foreground.util.hideKeyboardFrom
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

    private lateinit var binding: FragmentTaskDetailBinding
    private var initialPrioritySet = false


    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    lateinit var viewModel: TaskViewModel

    companion object {
        fun newInstance(uuid: UUID): TaskDetailFragment {
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
        viewModel = ViewModelProvider(
            requireActivity().viewModelStore,
            viewModelFactory
        ).get(TaskViewModel::class.java)
        val bundle = this.arguments
        //load Task from bundle args
        if (bundle?.getString("uuid") != null) {
            viewModel.setTask(UUID.fromString(bundle.getString("uuid"))
                ?: run { close(); UUID.randomUUID() })
        } else {
            Log.e(this.javaClass.toString(), "No key found.")
        }
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_task_detail, menu)
        super.onCreateOptionsMenu(menu, inflater);
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        binding = FragmentTaskDetailBinding.inflate(inflater, container, false)
        var twoPane = false
        context?.let { twoPane = it.isSideBySide() }
        val isNewTask = viewModel.currentTask?.name?.isBlank() == true

        binding.detailToolbar.title =
            if (isNewTask) "New Task" else viewModel.currentTask?.name
        setHasOptionsMenu(true)

        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getDefault()
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        timeFormat.timeZone = TimeZone.getDefault()

        if (!twoPane) {
            (activity as AppCompatActivity?)?.setSupportActionBar(binding.detailToolbar)
            (activity as AppCompatActivity?)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
            binding.detailToolbar.setNavigationOnClickListener { activity?.supportFragmentManager?.popBackStack() }
        }

        // Create an ArrayAdapter using the string array and a default spinner layout
        val adapter: ArrayAdapter<CharSequence> = ArrayAdapter.createFromResource(
            this.context
                ?: requireActivity().baseContext,
            R.array.priority_spinner, android.R.layout.simple_spinner_item
        )
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // Apply the adapter to the spinner
        binding.detailPriority.adapter = adapter

        //load from Task into input fields
        viewModel.currentTask?.let {
            binding.detailName.setText(it.name)
            if (it.priority != null) {
                binding.detailPriority.setSelection(adapter.getPosition(it.priority))
            } else {
                binding.detailPriority.setSelection(1)
            }

            binding.detailProject.setText(it.project ?: "")

            if (isNewTask) {
                val prefs = this.getActivity()?.getSharedPreferences("me.bgregos.BrightTask", Context.MODE_PRIVATE)
                val default_tags = prefs?.getString("default_tags", "")
                binding.detailTags.setText(default_tags)
                viewModel.setTaskTags(default_tags ?: "")
            }
            else
            {
                val builder = StringBuilder()
                it.tags.forEach { task -> builder.append("$task, ") }
                binding.detailTags.setText(builder.toString().trimEnd(',', ' '))
            }


            if (it.dueDate != null) {
                binding.detailDueDate.date.setText(dateFormat.format(it.dueDate))
                binding.detailDueDate.time.setText(timeFormat.format(it.dueDate))
            } else {
                binding.detailDueDate.date.setText("")
                binding.detailDueDate.time.setText("")
            }
            if (it.waitDate != null) {
                binding.detailWaitDate.date.setText(dateFormat.format(it.waitDate))
                binding.detailWaitDate.time.setText(timeFormat.format(it.waitDate))
            } else {
                binding.detailWaitDate.date.setText("")
                binding.detailWaitDate.time.setText("")
            }
        }

        // update viewmodel on all form changes
        binding.detailName.doOnTextChanged { text, _, _, _ -> viewModel.setTaskName(text.toString()) }
        binding.detailTags.doOnTextChanged { text, _, _, _ -> viewModel.setTaskTags(text.toString()) }
        binding.detailProject.doOnTextChanged { text, _, _, _ -> viewModel.setTaskProject(text.toString()) }
        binding.detailPriority.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                    //no-op
                }

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (initialPrioritySet) {
                        val selected: String =
                            binding.detailPriority.getItemAtPosition(position).toString()
                        viewModel.setTaskPriority(selected)
                    } else {
                        initialPrioritySet = true
                    }
                }
            }
//        binding.detail_priority.setOnTouchListener { view, _ ->
//            view.performClick()
//            viewModel.setTaskPriority(binding.detail_priority.selectedItem.toString())
//            true
//        }
//        binding.detail_priority.setOnKeyListener { view, _, _ ->
//            view.performClick()
//            viewModel.setTaskPriority(binding.detail_priority.selectedItem.toString())
//            true
//        }

        binding.detailWaitDate.dateInputLayout.hint = "Wait Until Date"
        binding.detailWaitDate.date.setOnClickListener {
            val c = Calendar.getInstance()
            val year = c.get(Calendar.YEAR)
            val month = c.get(Calendar.MONTH)
            val day = c.get(Calendar.DAY_OF_MONTH)
            val dpd = DatePickerDialog(this.requireContext(), { _, year, monthOfYear, dayOfMonth ->
                // Display Selected date in textbox
                binding.detailWaitDate.date.setText(DateFormatSymbols().getMonths()[monthOfYear] + " " + dayOfMonth + ", " + year)
                if (binding.detailWaitDate.time.text.isNullOrBlank()) {
                    binding.detailWaitDate.time.setText("12:00 AM")
                }
                viewModel.setTaskWaitDate(
                    binding.detailWaitDate.date.text.toString(),
                    binding.detailWaitDate.time.text.toString()
                )
            }, year, month, day)
            dpd.show()
        }

        binding.detailWaitDate.time.setOnClickListener {
            val c = Calendar.getInstance()
            val hour: Int = c.get(Calendar.HOUR_OF_DAY)
            val minute: Int = c.get(Calendar.MINUTE)
            val dpd = TimePickerDialog(this.requireContext(), { _, hour, minute ->
                // Display Selected date in textbox
                val inputFormat = SimpleDateFormat("KK:mm", Locale.getDefault())
                val outputFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val timeText = outputFormat.format(inputFormat.parse("" + hour + ":" + minute))
                binding.detailWaitDate.time.setText(timeText)
                if (binding.detailWaitDate.date.text.isNullOrBlank()) {
                    val year = c.get(Calendar.YEAR)
                    val month = c.get(Calendar.MONTH)
                    val day = c.get(Calendar.DAY_OF_MONTH)
                    binding.detailWaitDate.date.setText(DateFormatSymbols().shortMonths[month] + " " + day + ", " + year)
                }
                viewModel.setTaskWaitDate(
                    binding.detailWaitDate.date.text.toString(),
                    binding.detailWaitDate.time.text.toString()
                )
            }, hour, minute, false)
            dpd.show()
        }

        binding.detailDueDate.dateInputLayout.hint = "Due Date"
        binding.detailDueDate.date.setOnClickListener {
            val c = Calendar.getInstance()
            val year = c.get(Calendar.YEAR)
            val month = c.get(Calendar.MONTH)
            val day = c.get(Calendar.DAY_OF_MONTH)
            val dpd = DatePickerDialog(this.requireContext(), { _, year, monthOfYear, dayOfMonth ->
                // Display Selected date in textbox
                val dateText = "${DateFormatSymbols().months[monthOfYear]} $dayOfMonth, $year"
                binding.detailDueDate.date.setText(dateText)
                if (binding.detailDueDate.time.text.isNullOrBlank()) {
                    binding.detailDueDate.time.setText("12:00 AM")
                }
                viewModel.setTaskDueDate(
                    binding.detailDueDate.date.text.toString(),
                    binding.detailDueDate.time.text.toString()
                )
            }, year, month, day)
            dpd.show()
        }

        binding.detailDueDate.time.setOnClickListener {
            val c = Calendar.getInstance()
            val hour: Int = c.get(Calendar.HOUR_OF_DAY)
            val minute: Int = c.get(Calendar.MINUTE)
            val dpd = TimePickerDialog(this.requireContext(), { _, hour, minute ->
                // Display Selected date in textbox
                val inputFormat = SimpleDateFormat("KK:mm", Locale.getDefault())
                val outputFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val timeText = outputFormat.format(inputFormat.parse("" + hour + ":" + minute))
                binding.detailDueDate.time.setText(timeText)
                if (binding.detailDueDate.date.text.isNullOrBlank()) {
                    val year = c.get(Calendar.YEAR)
                    val month = c.get(Calendar.MONTH)
                    val day = c.get(Calendar.DAY_OF_MONTH)
                    binding.detailDueDate.date.setText("${DateFormatSymbols().shortMonths[month]} $day, $year")
                }
                viewModel.setTaskDueDate(
                    binding.detailDueDate.date.text.toString(),
                    binding.detailDueDate.time.text.toString()
                )
            }, hour, minute, false)
            dpd.show()
        }

        val item = viewModel.currentTask

        // Subscribe to the task detail close channel
        lifecycleScope.launchWhenStarted {
            viewModel.closeDetailChannel.receive()
            Log.d("test", "closing detail view")
            if (twoPane) {
                activity?.supportFragmentManager?.beginTransaction()?.tabletDetailAnimations()
                    ?.remove(this@TaskDetailFragment)?.commit()
            } else {
                activity?.supportFragmentManager?.popBackStack()
            }
        }

        //some strange issue requires this to be done or the view doesn't adjust
        //to match the content height
        //TODO: replace this spinner with a different ui element
        if (item?.priority == null || item.priority == "No Priority Assigned") {
            binding.detailPriority.setSelection(0)
            binding.detailPriority.setSelection(1)
            binding.detailPriority.setSelection(0)
            binding.detailPriority.setSelection(1)
            binding.detailPriority.setSelection(0)
        }

        viewModel.currentTask?.others?.forEach {
            addUserAttribute(it.key, it.value)
        }

        binding.addUserAttribute.setOnClickListener {
            addUserAttribute().apply {
                root.requestFocus()
            }
        }

        return binding.root
    }

    private fun addUserAttribute(key: String = "", value: String = ""): TaskDetailUserAttributeContentBinding {
        val newViewBinding = TaskDetailUserAttributeContentBinding.inflate(layoutInflater)
        newViewBinding.apply {
            deleteIcon.setOnClickListener {
                binding.userAttributeList.removeView(newViewBinding.root)
            }
            this.key.setText(key)
            this.value.setText(value)
        }
        binding.userAttributeList.addView(newViewBinding.root)
        return newViewBinding
    }

    private fun updateToolbar(name: String?) {
        binding.detailToolbar.title = if (name.isNullOrEmpty()) "New Task" else name
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
        context?.let { ctx -> hideKeyboardFrom(ctx, binding.root) }

        val udas = mutableListOf<Pair<String, String>>()
        binding.userAttributeList.forEach {
            val attribute = TaskDetailUserAttributeContentBinding.bind(it)
            udas.add(Pair(attribute.key.text.toString(), attribute.value.text.toString()))
        }
        viewModel.setUserAttributes(udas)
        Log.d("udas", "saved list: $udas")

        CoroutineScope(Dispatchers.IO).launch {
            viewModel.save()
        }
        super.onPause()
    }

    private fun close() {
        activity?.supportFragmentManager?.beginTransaction()?.remove(this)?.commit()
    }
}
