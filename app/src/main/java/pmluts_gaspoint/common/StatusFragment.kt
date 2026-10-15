package pmluts_gaspoint.common
import pmluts_gaspoint.R

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import pmluts_gaspoint.databinding.FragmentStatusBinding

class StatusFragment : Fragment() {
    private var _binding: FragmentStatusBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSimpanStatus.setOnClickListener {
            val status = if (binding.rbOnline.isChecked) "Online" else "Offline"
            binding.tvHasilStatus.visibility = View.VISIBLE
            binding.tvHasilStatus.text = "Status Anda saat ini: $status"
            Toast.makeText(context, "Status diperbarui menjadi $status", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
