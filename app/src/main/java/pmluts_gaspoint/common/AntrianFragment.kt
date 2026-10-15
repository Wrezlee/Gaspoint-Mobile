package pmluts_gaspoint.common
import pmluts_gaspoint.R

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import pmluts_gaspoint.databinding.FragmentAntrianBinding

class AntrianFragment : Fragment() {
    private var _binding: FragmentAntrianBinding? = null
    private val binding get() = _binding!!

    private val listAntrian = arrayOf(
        "Budi - Jl. Sukarno Hatta No. 9 Malang",
        "Siti - Jl. MT Haryono No. 10 Malang",
        "Lintang - Polinema Gedung AF"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAntrianBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, listAntrian)
        binding.lvAntrian.adapter = adapter

        binding.lvAntrian.setOnItemLongClickListener { _, _, position, _ ->
            val data = listAntrian[position]
            val alamat = data.substringAfter("- ").trim()

            val gmmIntentUri = Uri.parse("google.navigation:q=$alamat")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")

            try {
                startActivity(mapIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal membuka Maps", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
