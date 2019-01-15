package me.bgregos.foreground

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.date_layout.view.*
import kotlinx.android.synthetic.main.task_detail.*
import kotlinx.android.synthetic.main.task_detail.view.*
import me.bgregos.foreground.task.LocalTasks
import me.bgregos.foreground.task.Task
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.*


/**
 * A fragment representing a single Task detail screen.
 * This fragment is either contained in a [TaskListActivity]
 * in two-pane mode (on tablets) or a [TaskDetailActivity]
 * on handsets.
 */
class TaskDetailFragment : Fragment() {

    /**
     * The dummy content this fragment is presenting.
     */
    private var item: Task = Task("error")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bundle = this.arguments

        //load Task from bundle args
        if (bundle!!.getString("uuid") != null) {
            item = LocalTasks.getTaskByUUID(UUID.fromString(bundle.getString("uuid")))?: Task("Task lookup error")
        }
        else {
            Log.e(this.javaClass.toString(), "No key found.")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.task_detail, container, false)
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.US)

        // Create an ArrayAdapter using the string array and a default spinner layout
        val adapter: ArrayAdapter<CharSequence> = ArrayAdapter.createFromResource(this.context,
                R.array.priority_spinner, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        rootView.detail_priority.setAdapter(adapter);

        //load from Task into input fields
        item.let {
            rootView.detail_name.setText(it.name)
            rootView.detail_priority.setSelection(adapter.getPosition(it.priority?.toString() ?: " "))
            rootView.detail_project.setText(it.project ?: "")
            val builder = StringBuilder()
            it.tags.forEach { task -> builder.append("$task,") }
            rootView.detail_tags.setText(builder.toString().trimEnd(','))
            if(it.dueDate != null){
                rootView.detail_dueDate.date.setText(dateFormat.format(it.dueDate))
                rootView.detail_dueDate.time.setText(timeFormat.format(it.dueDate))
            }else{
                rootView.detail_dueDate.date.setText("")
                rootView.detail_dueDate.time.setText("")
            }
        }


        rootView.detail_dueDate.date.setOnClickListener {
            val c = Calendar.getInstance()
            val year = c.get(Calendar.YEAR)
            val month = c.get(Calendar.MONTH)
            val day = c.get(Calendar.DAY_OF_MONTH)
            val dpd = DatePickerDialog(activity, DatePickerDialog.OnDateSetListener { view, year, monthOfYear, dayOfMonth ->
                // Display Selected date in textbox
                detail_dueDate.date.setText(DateFormatSymbols().getMonths()[monthOfYear] + " " + dayOfMonth + ", " + year)
                if(detail_dueDate.time.text.isNullOrBlank()){
                    detail_dueDate.time.setText("12:00 AM")
                }
            }, year, month, day)
            dpd.show()
        }

        rootView.detail_dueDate.time.setOnClickListener {
            val c = Calendar.getInstance()
            val hour:Int = c.get(Calendar.HOUR_OF_DAY)
            val minute:Int = c.get(Calendar.MINUTE)
            val dpd = TimePickerDialog(activity, TimePickerDialog.OnTimeSetListener { view, hour, minute ->
                // Display Selected date in textbox
                val inputFormat = SimpleDateFormat("KK:mm", Locale.US)
                val outputFormat = SimpleDateFormat("hh:mm a", Locale.US)
                detail_dueDate.time.setText(outputFormat.format(inputFormat.parse(""+hour+":"+minute)))
                if(detail_dueDate.date.text.isNullOrBlank()){
                    val year = c.get(Calendar.YEAR)
                    val month = c.get(Calendar.MONTH)
                    val day = c.get(Calendar.DAY_OF_MONTH)
                    detail_dueDate.date.setText(DateFormatSymbols().shortMonths[month] + " " + day + ", " + year)
                }
            }, hour, minute, false)
            dpd.show()
        }

        return rootView
    }

    override fun onPause() {
        //handle save
        if (detail_name.text.toString().isBlank() || detail_name.text.toString().isEmpty()) {
            Log.d(this.javaClass.toString(), "Removing task w/ no name")
            val pos = LocalTasks.items.indexOf(item)
            LocalTasks.items.removeAt(pos)
            LocalTasks.save(activity!!.applicationContext)
            super.onPause()
        } else {
            val format = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.US)
            val toModify: Task = LocalTasks.getTaskByUUID(item.uuid) ?: throw NullPointerException("Task uuid lookup failed!")
            toModify.name=detail_name.text.toString()
            toModify.tags=detail_tags.text?.split(" ,",",") as ArrayList<String>
            toModify.project=detail_project.text?.toString()
            toModify.priority=detail_priority.selectedItem.toString()
            toModify.modifiedDate=Date() //update modified date
            if(!detail_dueDate.date.text.isNullOrBlank() && !detail_dueDate.time.text.isNullOrBlank()){
                toModify.dueDate=format.parse(detail_dueDate.date.text.toString()+" "+detail_dueDate.time.text.toString())
            }
            if (!LocalTasks.localChanges.contains(toModify)){
                LocalTasks.localChanges.add(toModify)
            }
            LocalTasks.save(activity!!.applicationContext)
            Log.e(this.javaClass.toString(), "Saving task")
            super.onPause()
        }
    }
}
