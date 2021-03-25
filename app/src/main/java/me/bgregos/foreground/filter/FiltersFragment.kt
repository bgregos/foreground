package me.bgregos.foreground.filter

import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import me.bgregos.foreground.R
import me.bgregos.foreground.databinding.FragmentFiltersBinding
import me.bgregos.foreground.databinding.FragmentTaskDetailBinding
import javax.inject.Inject

class FiltersFragment : Fragment() {

    private var _binding: FragmentFiltersBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    companion object {
        fun newInstance() = FiltersFragment()
    }

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private val viewModel by viewModels<FiltersViewModel> { viewModelFactory }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        _binding = FragmentFiltersBinding.inflate(inflater, container, false)
        val rootView = binding.root

        rootView

        return rootView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
    }

}