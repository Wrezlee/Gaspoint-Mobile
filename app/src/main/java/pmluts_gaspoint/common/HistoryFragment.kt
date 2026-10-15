package pmluts_gaspoint.common
import pmluts_gaspoint.R

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import pmluts_gaspoint.databinding.FragmentHistoryBinding

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refreshData()
    }

    private fun refreshData() {
        // Menampilkan pesanan yang sudah berstatus 'Selesai'
        FirestoreRepository.getTransaksiByStatus("Selesai") { cursor ->
            if (_binding == null) {
                cursor.close()
                return@getTransaksiByStatus
            }

            val adapter = object : SimpleCursorAdapter(
                requireContext(),
                android.R.layout.simple_list_item_2,
                cursor,
                arrayOf("nama_produk", "alamat"),
                intArrayOf(android.R.id.text1, android.R.id.text2),
                0
            ) {
                override fun bindView(view: View, context: android.content.Context?, cursor: android.database.Cursor) {
                    val text1 = view.findViewById<TextView>(android.R.id.text1)
                    val text2 = view.findViewById<TextView>(android.R.id.text2)

                    val produk = cursor.getString(cursor.getColumnIndexOrThrow("nama_produk"))
                    val pembeli = cursor.getString(cursor.getColumnIndexOrThrow("pembeli"))
                    val alamat = cursor.getString(cursor.getColumnIndexOrThrow("alamat"))
                    val tanggal = cursor.getString(cursor.getColumnIndexOrThrow("tanggal"))

                    text1.text = "Selesai: $produk"
                    text2.text = "Ke: $pembeli ($alamat) pada $tanggal"
                }
            }

            binding.lvHistory.adapter = adapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
