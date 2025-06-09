package com.mykolashvets.diplomawork.presentation

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.mykolashvets.diplomawork.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val PREFS = "mushroom_prefs"
        private const val KEY_HISTORY = "history_set"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_HISTORY, emptySet()) ?: emptySet()

        binding.tvHistoryContent.text =
            if (set.isNotEmpty())
                set.joinToString(separator = "\n")   // кожен гриб на новому рядку
            else
                "Історія поки порожня"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

