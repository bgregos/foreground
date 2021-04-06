package me.bgregos.foreground.filter

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ListPopupWindow
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_filters.view.*
import kotlinx.coroutines.flow.collect
import me.bgregos.foreground.R
import me.bgregos.foreground.databinding.FilterListContentBinding
import me.bgregos.foreground.databinding.FragmentFiltersBinding
import me.bgregos.foreground.getApplicationComponent
import me.bgregos.foreground.model.ParameterFormat
import me.bgregos.foreground.model.TaskFilter
import me.bgregos.foreground.model.TaskFiltersAvailable
import me.bgregos.foreground.util.hideKeyboardFrom
import me.bgregos.foreground.util.isSideBySide
import javax.inject.Inject

class FiltersFragment : Fragment() {

    private var _binding: FragmentFiltersBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val listPopupView by lazy { context?.let { ListPopupWindow(it) } }

    companion object {
        fun newInstance() = FiltersFragment()
    }

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private val viewModel by viewModels<FiltersViewModel> { viewModelFactory }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        context.getApplicationComponent().inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentFiltersBinding.inflate(inflater, container, false)
        binding.filtersRecyclerview.layoutManager = LinearLayoutManager(context)
        val rootView = binding.root
        var twoPane = false
        context?.let { twoPane = it.isSideBySide() }

        if(!twoPane){
            (activity as AppCompatActivity?)?.setSupportActionBar(rootView.filter_toolbar)
            (activity as AppCompatActivity?)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
            rootView.filter_toolbar.menu.clear()
            rootView.filter_toolbar.setNavigationOnClickListener(View.OnClickListener { _ -> activity?.supportFragmentManager?.popBackStack() })
        }

        lifecycleScope.launchWhenStarted {
            viewModel.filters.collect {
                binding.filtersRecyclerview.adapter = FilterAdapter(it, viewModel)
            }
        }

        // Define filter type selector popup
        val dataList = TaskFiltersAvailable.filters.map { it.name }
        listPopupView?.setAdapter(context?.let { ArrayAdapter(it, android.R.layout.simple_list_item_1, dataList) })
        listPopupView?.setOnItemClickListener { _, _, position, _ ->
            binding.filterType.setText(dataList[position])
            // if no parameter required by filter type, disable parameter entry
            if (TaskFiltersAvailable.filters.first { it.name == dataList[position] }.parameterFormat == ParameterFormat.NONE) {
                binding.filterParameter.setText("")
                binding.filterParameterContainer.hint = getString(R.string.filter_parameter_hint_disabled)
                binding.filterParameter.isEnabled = false
            } else {
                binding.filterParameterContainer.hint = getString(R.string.filter_parameter_hint_enabled)
                binding.filterParameter.isEnabled = true
            }
            listPopupView?.dismiss()
        }
        listPopupView?.anchorView = binding.filterType

        binding.filterType.apply {
            setOnTouchListener { view, _ ->
                listPopupView?.show(); view.performClick()
            }
            inputType = InputType.TYPE_NULL
        }

        binding.addFilterButton.setOnClickListener {
            val addFilterAllowed = !binding.filterType.text.isNullOrBlank() &&
                    ( TaskFiltersAvailable.filters.first { it.name == binding.filterType.text.toString() }.parameterFormat == ParameterFormat.NONE ||
                    !binding.filterParameter.text.isNullOrBlank() )
            if (addFilterAllowed){
                val newFilter = TaskFilter(
                        id = 0,
                        type = TaskFiltersAvailable.filters.first { it.name == binding.filterType.text.toString() },
                        parameter = binding.filterParameter.text.toString(),
                        includeMatching = binding.filterInclusionButton.isChecked
                )
                val added = viewModel.addFilter(newFilter)
                if (!added) {
                    context?.let { ctx -> hideKeyboardFrom(ctx, binding.root) }
                    val bar1 = Snackbar.make(binding.root, getString(R.string.filter_warning_already_added), Snackbar.LENGTH_SHORT)
                    bar1.view.setBackgroundColor(Color.parseColor("#34309f"))
                    bar1.show()
                }
                binding.filterParameter.setText("")
            } else {
                context?.let { ctx -> hideKeyboardFrom(ctx, binding.root) }
                val bar1 = Snackbar.make(binding.root, getString(R.string.filter_add_warning), Snackbar.LENGTH_SHORT)
                bar1.view.setBackgroundColor(Color.parseColor("#34309f"))
                bar1.show()
            }

        }
        return binding.root
    }

    class FilterAdapter(private val dataSet: List<TaskFilter>, private val viewModel: FiltersViewModel) :
            RecyclerView.Adapter<FilterAdapter.ViewHolder>() {

        class ViewHolder(binding: FilterListContentBinding) : RecyclerView.ViewHolder(binding.root) {
            var enabled = binding.filterEnabled
            val text = binding.filterText
            val delete = binding.filterDelete
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(FilterListContentBinding.inflate(LayoutInflater.from(viewGroup.context), viewGroup, false))
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            val filter = dataSet[position]
            viewHolder.enabled.isChecked = filter.enabled
            viewHolder.enabled.setOnCheckedChangeListener { _, _ -> viewModel.toggleFilterEnable(filter) }
            viewHolder.text.text = viewModel.generateFriendlyString(filter)
            viewHolder.delete.setOnClickListener { viewModel.removeFilter(filter) }
        }

        override fun getItemCount() = dataSet.size

    }


}