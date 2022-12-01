package me.bgregos.foreground.tasklist

import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import me.bgregos.foreground.R
import me.bgregos.foreground.databinding.TaskDetailUserAttributeContentBinding

typealias UserAttribute = Triple<String, String, Int>

class UserAttributeAdapter(initialAttributes: List<UserAttribute>, val toFocus: View) :
    ListAdapter<UserAttribute, UserAttributeAdapter.ViewHolder>(DiffCallback()) {

    var list: List<UserAttribute>
        private set

    private var latestId = 0

    init {
        list = initialAttributes
        submitList(list)
    }

    inner class ViewHolder(view: View, val onChange: (Int, String, String) -> Unit, val onDelete: (Int) -> Unit) : RecyclerView.ViewHolder(view) {
        val binding = TaskDetailUserAttributeContentBinding.bind(view)
        val keyTextListener = KeyTextListener()
        val valueTextListener = ValueTextListener()

        init {
            binding.deleteIcon.setOnClickListener {
                onDelete(bindingAdapterPosition)
                binding.key.clearFocus()
                binding.value.clearFocus()
            }
        }

        inner class KeyTextListener : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                //no-op
            }

            override fun onTextChanged(newText: CharSequence, i: Int, i2: Int, i3: Int) {
                onChange(bindingAdapterPosition, newText.toString(), binding.value.text.toString())
            }

            override fun afterTextChanged(p0: Editable?) {
                //no-op
            }
        }

        inner class ValueTextListener : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                //no-op
            }

            override fun onTextChanged(newText: CharSequence, i: Int, i2: Int, i3: Int) {
                onChange(bindingAdapterPosition, binding.key.text.toString(), newText.toString())
            }

            override fun afterTextChanged(p0: Editable?) {
                //no-op
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<UserAttribute>() {

        override fun areItemsTheSame(oldItem: UserAttribute, newItem: UserAttribute) =
            oldItem.first == newItem.first && oldItem.second == newItem.second && oldItem.third == newItem.third

        override fun areContentsTheSame(oldItem: UserAttribute, newItem: UserAttribute) =
            oldItem.first == newItem.first && oldItem.second == newItem.second
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.task_detail_user_attribute_content, parent, false)
        return ViewHolder(view, ::onChange, ::onDelete)
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.binding.key.removeTextChangedListener(holder.keyTextListener)
        holder.binding.value.removeTextChangedListener(holder.valueTextListener)
    }

    fun add() {
        toFocus.isFocusableInTouchMode = true
        toFocus.requestFocus()
        latestId += 1
        list = list.plus(UserAttribute("", "", latestId))
        submitList(list)
        toFocus.isFocusableInTouchMode = false
        toFocus.clearFocus()
        Log.d("uaa", "added element. list = $list")
    }

    private fun onChange(position: Int, key: String, value: String){
        list = list.mapIndexed { index, attr -> if (index == position) UserAttribute(key, value, index) else attr }
        Log.d("uaa", "modified element. list = $list")
    }

    private fun onDelete(position: Int) {
        list = list.filterIndexed { index, _ -> index != position }
        submitList(list)
        Log.d("uaa", "deleted element. list = $list")
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        Log.d("dbg2", item.toString())
        holder.binding.key.setText(item.first)
        holder.binding.value.setText(item.second)
        holder.binding.key.addTextChangedListener(holder.keyTextListener)
        holder.binding.value.addTextChangedListener(holder.valueTextListener)
    }

}