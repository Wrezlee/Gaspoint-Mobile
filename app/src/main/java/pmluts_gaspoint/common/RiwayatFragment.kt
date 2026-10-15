package pmluts_gaspoint.common
import pmluts_gaspoint.R

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import pmluts_gaspoint.databinding.FragmentRiwayatBinding

class RiwayatFragment : Fragment() {
    private var _binding: FragmentRiwayatBinding? = null
    private val binding get() = _binding!!

    private val dataRiwayat = arrayOf(
        "Selesai: Pesanan #001 - Diterima Budi",
        "Selesai: Pesanan #002 - Diterima Siti",
        "Selesai: Pesanan #003 - Diterima Andi"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRiwayatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Menggunakan layout simple_list_item_1 (Pastikan warna teks terbaca di background gelap)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, dataRiwayat)
        binding.lvRiwayat.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
