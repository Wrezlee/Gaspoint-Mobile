package pmluts_gaspoint.admin

import pmluts_gaspoint.common.SupabaseStorageHelper

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import pmluts_gaspoint.R
import pmluts_gaspoint.common.FirestoreRepository
import com.google.firebase.firestore.ListenerRegistration
import pmluts_gaspoint.databinding.FragmentAdminOrdersBinding
import java.util.Locale

class AdminOrdersFragment : Fragment() {

    private var _binding: FragmentAdminOrdersBinding? = null
    private val binding get() = _binding!!

    private var listener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadTransaksiData()
    }

    private fun loadTransaksiData() {
        val adapter = object : SimpleCursorAdapter(
            requireContext(),
            R.layout.item_transaksi,
            null,
            arrayOf(
                "nama_produk",
                "pembeli",
                "status",
                "jumlah",
                "metode_pembayaran"
            ),
            intArrayOf(
                R.id.tv_transaksi_produk,
                R.id.tv_transaksi_pembeli,
                R.id.tv_transaksi_status,
                R.id.tv_transaksi_jumlah,
                R.id.tv_transaksi_metode
            ),
            0
        ) {
            override fun bindView(
                view: View,
                context: Context?,
                cursor: android.database.Cursor
            ) {
                val tvProduk = view.findViewById<TextView>(R.id.tv_transaksi_produk)
                val tvPembeli = view.findViewById<TextView>(R.id.tv_transaksi_pembeli)
                val tvStatus = view.findViewById<TextView>(R.id.tv_transaksi_status)
                val tvJumlah = view.findViewById<TextView>(R.id.tv_transaksi_jumlah)
                val tvMetode = view.findViewById<TextView>(R.id.tv_transaksi_metode)
                val btnDelete = view.findViewById<ImageButton>(R.id.btn_delete_transaksi)
                val btnMenu = view.findViewById<ImageButton>(R.id.btn_menu_transaksi)

                val id = cursor.getInt(cursor.getColumnIndexOrThrow("_id"))
                val produk = cursor.getString(cursor.getColumnIndexOrThrow("nama_produk"))
                val pembeli = cursor.getString(cursor.getColumnIndexOrThrow("pembeli"))
                val status = cursor.getString(cursor.getColumnIndexOrThrow("status"))
                val jumlah = cursor.getInt(cursor.getColumnIndexOrThrow("jumlah"))
                val metode = cursor.getString(cursor.getColumnIndexOrThrow("metode_pembayaran")) ?: "-"
                val buktiPembayaran = cursor.getString(cursor.getColumnIndexOrThrow("bukti_pembayaran")) ?: ""
                val kurir = cursor.getString(cursor.getColumnIndexOrThrow("kurir_username")) ?: ""
                val buktiPengantaran = cursor.getString(cursor.getColumnIndexOrThrow("bukti_pengantaran")) ?: ""
                val alamat = cursor.getString(cursor.getColumnIndexOrThrow("alamat")) ?: ""
                val total = cursor.getDouble(cursor.getColumnIndexOrThrow("total_harga"))
                val tanggal = cursor.getString(cursor.getColumnIndexOrThrow("tanggal")) ?: ""
                val jadwal = cursor.getString(cursor.getColumnIndexOrThrow("jadwal_antar")) ?: ""

                tvProduk.text = produk

                tvPembeli.text = if (kurir.isNotEmpty()) {
                    "Pembeli: $pembeli\nKurir: $kurir"
                } else {
                    "Pembeli: $pembeli"
                }

                tvStatus.text = status
                tvJumlah.text = "x$jumlah"
                tvMetode.text = "Metode: $metode"

                when (status) {
                    "Selesai" -> tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
                    "Diproses" -> tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#FF9800"))
                    "Menunggu Validasi" -> tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#2196F3"))
                    "Ditugaskan" -> tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#7E57C2"))
                    "Dikirim" -> tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#03A9F4"))
                    "Menunggu Konfirmasi Selesai" -> tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#009688"))
                    "Dibatalkan" -> tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#F44336"))
                    else -> tvStatus.setBackgroundColor(android.graphics.Color.GRAY)
                }

                view.setOnClickListener {
                    showUpdateStatusDialog(
                        id = id,
                        produk = produk,
                        currentStatus = status,
                        metode = metode,
                        buktiPembayaran = buktiPembayaran,
                        buktiPengantaran = buktiPengantaran
                    )
                }

                // Tombol hapus tunggal diganti tombol titik tiga yang membuka PopupMenu
                btnDelete.visibility = View.GONE
                btnMenu.visibility = View.VISIBLE
                btnMenu.setOnClickListener { anchor ->
                    showOrderPopupMenu(
                        anchor,
                        OrderInfo(
                            id = id,
                            produk = produk,
                            pembeli = pembeli,
                            status = status,
                            jumlah = jumlah,
                            metode = metode,
                            buktiPembayaran = buktiPembayaran,
                            kurir = kurir,
                            buktiPengantaran = buktiPengantaran,
                            alamat = alamat,
                            total = total,
                            tanggal = tanggal,
                            jadwal = jadwal
                        )
                    )
                }
            }
        }

        binding.lvTransactions.adapter = adapter

        // Real-time: daftar ikut berubah saat status pesanan diubah dari perangkat mana pun
        listener?.remove()
        listener = FirestoreRepository.observeAllTransaksi { cursor ->
            if (_binding == null) {
                cursor.close()
            } else {
                adapter.changeCursor(cursor)
            }
        }
    }

    // ------------------------------------------------------------------
    // PopupMenu (tombol titik tiga pada item pesanan)
    // ------------------------------------------------------------------

    private data class OrderInfo(
        val id: Int,
        val produk: String,
        val pembeli: String,
        val status: String,
        val jumlah: Int,
        val metode: String,
        val buktiPembayaran: String,
        val kurir: String,
        val buktiPengantaran: String,
        val alamat: String,
        val total: Double,
        val tanggal: String,
        val jadwal: String
    )

    private fun showOrderPopupMenu(anchor: View, order: OrderInfo) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.admin_order_popup_menu, popup.menu)

        // "Buka di Maps" hanya relevan jika pesanan punya alamat
        popup.menu.findItem(R.id.popup_maps).isVisible = order.alamat.isNotBlank()

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.popup_detail -> {
                    showDetailDialog(order)
                    true
                }

                R.id.popup_ubah_status -> {
                    showUpdateStatusDialog(
                        id = order.id,
                        produk = order.produk,
                        currentStatus = order.status,
                        metode = order.metode,
                        buktiPembayaran = order.buktiPembayaran,
                        buktiPengantaran = order.buktiPengantaran
                    )
                    true
                }

                R.id.popup_maps -> {
                    bukaMaps(order.alamat)
                    true
                }

                R.id.popup_hapus -> {
                    konfirmasiHapusTransaksi(order.id, order.produk)
                    true
                }

                else -> false
            }
        }

        popup.show()
    }

    private fun showDetailDialog(order: OrderInfo) {
        val totalText = "Rp " + String.format(Locale.getDefault(), "%,.0f", order.total)

        val detail = buildString {
            appendLine("Produk: ${order.produk} x${order.jumlah}")
            appendLine("Pembeli: ${order.pembeli}")
            appendLine("Alamat: ${order.alamat.ifBlank { "-" }}")
            appendLine("Total: $totalText")
            appendLine("Metode: ${order.metode}")
            appendLine("Status: ${order.status}")
            appendLine("Kurir: ${order.kurir.ifBlank { "-" }}")
            appendLine("Tanggal: ${order.tanggal.ifBlank { "-" }}")
            append("Jadwal antar: ${order.jadwal.ifBlank { "-" }}")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Detail Pesanan #${order.id}")
            .setMessage(detail)
            .setPositiveButton("Tutup", null)
            .show()
    }

    private fun bukaMaps(alamat: String) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:0,0?q=${Uri.encode(alamat)}")
        )

        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                requireContext(),
                "Aplikasi peta tidak tersedia",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun konfirmasiHapusTransaksi(id: Int, produk: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus Transaksi")
            .setMessage("Hapus transaksi $produk?")
            .setPositiveButton("Hapus") { _, _ ->
                FirestoreRepository.deleteTransaksi(id)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showUpdateStatusDialog(
        id: Int,
        produk: String,
        currentStatus: String,
        metode: String,
        buktiPembayaran: String,
        buktiPengantaran: String
    ) {
        val optionsList = mutableListOf<String>()

        if (buktiPembayaran.isNotEmpty()) {
            optionsList.add("Lihat Bukti Pembayaran")
        }

        if (metode == "QRIS" && currentStatus == "Menunggu Validasi") {
            optionsList.add("Validasi Pembayaran")
        }

        if (currentStatus == "Diproses") {
            optionsList.add("Tugaskan Ke Kurir")
        }

        if (currentStatus == "Ditugaskan") {
            optionsList.add("Ganti Kurir")
        }

        if (buktiPengantaran.isNotEmpty()) {
            optionsList.add("Lihat Bukti Pengantaran")
        }

        if (currentStatus == "Menunggu Konfirmasi Selesai") {
            if (buktiPengantaran.isNotEmpty()) {
                optionsList.add("Tandai Selesai")
            } else {
                optionsList.add("Bukti Pengantaran Belum Ada")
            }
        }

        if (currentStatus != "Selesai" && currentStatus != "Dibatalkan") {
            optionsList.add("Dibatalkan")
        }

        if (optionsList.isEmpty()) {
            optionsList.add("Tidak Ada Aksi")
        }

        val options = optionsList.distinct().toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Kelola Pesanan: $produk")
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Lihat Bukti Pembayaran" -> {
                        showImageDialog(
                            title = "Bukti Pembayaran",
                            imageUri = buktiPembayaran,
                            emptyMessage = "Bukti pembayaran belum tersedia"
                        )
                    }

                    "Validasi Pembayaran" -> {
                        FirestoreRepository.validasiPembayaran(id) { ok ->
                            if (ok && isAdded) {
                                Toast.makeText(
                                    requireContext(),
                                    "Pembayaran divalidasi",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }

                    "Tugaskan Ke Kurir", "Ganti Kurir" -> {
                        showAssignKurirDialog(id)
                    }

                    "Lihat Bukti Pengantaran" -> {
                        showImageDialog(
                            title = "Bukti Pengantaran",
                            imageUri = buktiPengantaran,
                            emptyMessage = "Bukti pengantaran belum tersedia"
                        )
                    }

                    "Bukti Pengantaran Belum Ada" -> {
                        Toast.makeText(
                            requireContext(),
                            "Kurir belum mengupload bukti pengantaran",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    "Tandai Selesai" -> {
                        FirestoreRepository.tandaiSelesai(id) { ok ->
                            if (isAdded) {
                                Toast.makeText(
                                    requireContext(),
                                    if (ok) "Pesanan ditandai selesai" else "Gagal mengubah status",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }

                    "Dibatalkan" -> {
                        FirestoreRepository.updateStatus(id, "Dibatalkan") { ok ->
                            if (ok && isAdded) {
                                Toast.makeText(
                                    requireContext(),
                                    "Pesanan dibatalkan",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }

                    "Tidak Ada Aksi" -> {
                        Toast.makeText(
                            requireContext(),
                            "Tidak ada aksi untuk status ini",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .show()
    }

    private fun showAssignKurirDialog(idTransaksi: Int) {
        FirestoreRepository.getSemuaKurirAktif { cursor ->
            if (!isAdded) {
                cursor.close()
                return@getSemuaKurirAktif
            }

            val kurirList = mutableListOf<String>()

            if (cursor.moveToFirst()) {
                do {
                    val username = cursor.getString(cursor.getColumnIndexOrThrow("username"))
                    kurirList.add(username)
                } while (cursor.moveToNext())
            }

            cursor.close()

            if (kurirList.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Akun kurir aktif belum ada",
                    Toast.LENGTH_SHORT
                ).show()
                return@getSemuaKurirAktif
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Pilih Kurir Aktif")
                .setItems(kurirList.toTypedArray()) { _, which ->
                    val selectedKurir = kurirList[which]

                    FirestoreRepository.assignKurir(idTransaksi, selectedKurir) { ok ->
                        if (isAdded) {
                            Toast.makeText(
                                requireContext(),
                                if (ok) "Pesanan ditugaskan ke $selectedKurir" else "Gagal menugaskan kurir",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                .show()
        }
    }

    private fun showImageDialog(
        title: String,
        imageUri: String,
        emptyMessage: String
    ) {
        if (imageUri.isEmpty()) {
            Toast.makeText(
                requireContext(),
                emptyMessage,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val imageView = ImageView(requireContext())

        imageView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        imageView.setPadding(32, 32, 32, 32)
        imageView.adjustViewBounds = true
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER

        try {
            SupabaseStorageHelper.loadImage(imageView, imageUri)
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Gagal memuat gambar",
                Toast.LENGTH_SHORT
            ).show()
        }

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(imageView)
            .setPositiveButton("Tutup", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
        listener = null
        _binding = null
    }
}