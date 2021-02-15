package me.bgregos.foreground.tasklist

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.View.*
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.task_list_content.view.*
import me.bgregos.foreground.R
import me.bgregos.foreground.model.Task
import me.bgregos.foreground.util.contentsChanged
import java.text.SimpleDateFormat
import java.util.*

class TaskListAdapter(private val parentFragment: TaskListFragment,
                      private var values: ArrayList<Task>) :
        RecyclerView.Adapter<TaskListAdapter.ViewHolder>() {

    private val onClickListener: View.OnClickListener

    init {
        this.setHasStableIds(false)
        onClickListener = View.OnClickListener { v ->
            val task = v.tag as Task
            parentFragment.openTask(task, v, task.name)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.task_list_content, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val format = SimpleDateFormat("MMM d, yyyy 'at' h:mm aaa", Locale.getDefault())
        val item = values[position]
        holder.title.text = item.name
        if(item.dueDate != null) {
            holder.due.visibility = VISIBLE
            holder.dueicon.visibility = VISIBLE
            holder.due.text = format.format(item.dueDate as Date)
        }else{
            holder.dueicon.visibility = GONE
            holder.due.visibility = GONE
        }
        if(item.project.isNullOrEmpty()){
            holder.project.visibility = GONE
            holder.projecticon.visibility = GONE
            //remove margin from tags icon so it lines up with where this was
            val param = holder.tagsicon.layoutParams as ViewGroup.MarginLayoutParams
            param.marginStart = 0
            holder.tagsicon.layoutParams = param
        }else{
            holder.project.visibility = VISIBLE
            holder.projecticon.visibility = VISIBLE
            holder.project.text = item.project
        }
        if(item.tags.size == 0 || item.tags[0].isBlank()){
            holder.tags.visibility = GONE
            holder.tagsicon.visibility = GONE
        }else{
            holder.tags.visibility = VISIBLE
            holder.tagsicon.visibility = VISIBLE
            holder.tags.text = item.tags.joinToString(", ")
        }
        val color = ColorDrawable(when (item.priority) {
            "H" -> Color.parseColor("#550000")
            "M" -> Color.parseColor("#666600")
            "L" -> Color.parseColor("#303066")
            else -> Color.parseColor("#373737")
        })
        holder.accent.background = color

        with(holder.itemView) {
            tag = item
            setOnClickListener(onClickListener)
        }

        holder.complete.setOnClickListener {
            val pos = holder.layoutPosition
            values.removeAt(pos)
            notifyItemRemoved(pos)
            //notifyItemRangeChanged(pos, values.size)
            item.modifiedDate=Date() //update modified date
            item.status = "completed"
            if (!LocalTasksRepository.localChanges.value.contains(item)){

                LocalTasksRepository.localChanges.apply{
                    value.add(item)
                    contentsChanged()
                }
            }
            LocalTasksRepository.tasks.apply {
                value.remove(item)
                contentsChanged()
            }
        }
    }

    override fun getItemCount() = values.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.title
        val due: TextView = view.due
        val dueicon: ImageView = view.ic_date
        val project: TextView = view.project
        val projecticon: ImageView = view.ic_proj
        val tags: TextView = view.tags
        val tagsicon: ImageView = view.ic_tags
        val accent: View = view.task_list_card_accentbar
        val complete: ImageView = view.complete
    }

}
