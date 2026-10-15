package pmluts_gaspoint.pembeli
import pmluts_gaspoint.common.FirestoreRepository
import com.google.firebase.firestore.ListenerRegistration
import pmluts_gaspoint.R

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import pmluts_gaspoint.databinding.FragmentPembeliRiwayatBinding
import java.util.Locale

class PembeliRiwayatFragment : Fragment() {
    private var _binding: FragmentPembeliRiwayatBinding? = null
    private val binding get() = _binding!!
    private var listener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPembeliRiwayatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadHistory()
    }

    private fun loadHistory() {
        val sharedPref = requireActivity().getSharedPreferences("UserSession", Context.MODE_PRIVATE)
        val username = sharedPref.getString("username", "") ?: ""
        

        val adapter = object : SimpleCursorAdapter(
            requireContext(),
            R.layout.item_transaksi,
            null,
            arrayOf("nama_produk", "tanggal", "total_harga"),
            intArrayOf(R.id.tv_transaksi_produk, R.id.tv_transaksi_pembeli, R.id.tv_transaksi_jumlah),
            0
        ) {
            override fun bindView(view: View, context: Context?, cursor: android.database.Cursor) {
                val tvProduk = view.findViewById<TextView>(R.id.tv_transaksi_produk)
                val tvStatus = view.findViewById<TextView>(R.id.tv_transaksi_status)
                val tvTotal = view.findViewById<TextView>(R.id.tv_transaksi_jumlah)
                val tvTanggal = view.findViewById<TextView>(R.id.tv_transaksi_pembeli)
                val btnDelete = view.findViewById<View>(R.id.btn_delete_transaksi)

                btnDelete.visibility = View.GONE
                
                val produk = cursor.getString(cursor.getColumnIndexOrThrow("nama_produk"))
                val total = cursor.getDouble(cursor.getColumnIndexOrThrow("total_harga"))
                val tanggal = cursor.getString(cursor.getColumnIndexOrThrow("tanggal"))

                tvProduk.text = produk
                tvStatus.text = "SELESAI"
                tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
                tvTotal.text = "Rp " + String.format(Locale.getDefault(), "%,.0f", total)
                tvTanggal.text = "Selesai pada: $tanggal"
            }
        }
        binding.lvHistory.adapter = adapter

        listener?.remove()
        listener = FirestoreRepository.observeTransaksiByPembeli(username, onlySelesai = true) { cursor ->
            if (_binding == null) {
                cursor.close()
            } else {
                adapter.changeCursor(cursor)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
        listener = null
        _binding = null
    }
}
