package pmluts_gaspoint.pembeli

import pmluts_gaspoint.common.SupabaseStorageHelper

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import pmluts_gaspoint.R
import pmluts_gaspoint.common.FirestoreRepository
import com.google.firebase.firestore.ListenerRegistration
import pmluts_gaspoint.databinding.FragmentPembeliOrdersBinding
import java.util.Locale

class PembeliOrdersFragment : Fragment() {

    private var _binding: FragmentPembeliOrdersBinding? = null
    private val binding get() = _binding!!

    private var listener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPembeliOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        loadActiveOrders()
    }

    private fun loadActiveOrders() {
        val sharedPref = requireActivity().getSharedPreferences(
            "UserSession",
            Context.MODE_PRIVATE
        )

        val username = sharedPref.getString("username", "") ?: ""
        val adapter = object : SimpleCursorAdapter(
            requireContext(),
            R.layout.item_transaksi,
            null,
            arrayOf(
                "nama_produk",
                "status",
                "total_harga",
                "metode_pembayaran"
            ),
            intArrayOf(
                R.id.tv_transaksi_produk,
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
                val tvStatus = view.findViewById<TextView>(R.id.tv_transaksi_status)
                val tvTotal = view.findViewById<TextView>(R.id.tv_transaksi_jumlah)
                val tvTanggal = view.findViewById<TextView>(R.id.tv_transaksi_pembeli)
                val tvMetode = view.findViewById<TextView>(R.id.tv_transaksi_metode)
                val btnDelete = view.findViewById<View>(R.id.btn_delete_transaksi)

                btnDelete.visibility = View.GONE

                val produk = cursor.getString(cursor.getColumnIndexOrThrow("nama_produk"))
                val status = cursor.getString(cursor.getColumnIndexOrThrow("status"))
                val total = cursor.getDouble(cursor.getColumnIndexOrThrow("total_harga"))
                val tanggal = cursor.getString(cursor.getColumnIndexOrThrow("tanggal"))
                val jumlah = cursor.getInt(cursor.getColumnIndexOrThrow("jumlah"))
                val alamat = cursor.getString(cursor.getColumnIndexOrThrow("alamat")) ?: "-"
                val metode = cursor.getString(cursor.getColumnIndexOrThrow("metode_pembayaran")) ?: "-"
                val bukti = cursor.getString(cursor.getColumnIndexOrThrow("bukti_pembayaran")) ?: ""
                val id = cursor.getInt(cursor.getColumnIndexOrThrow("_id"))
                val canCancel = cursor.getInt(cursor.getColumnIndexOrThrow("can_cancel")) == 1

                tvProduk.text = produk
                tvStatus.text = status
                tvTotal.text = "Rp " + String.format(Locale.getDefault(), "%,.0f", total)
                tvTanggal.text = "Tgl: $tanggal"
                tvMetode.text = "Metode: $metode"

                when (status) {
                    "Diproses" -> tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#FF9800"))
                    "Dikirim" -> tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#2196F3"))
                    "Selesai" -> tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
                    "Menunggu Validasi" -> tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#757575"))
                    "Ditugaskan" -> tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#7E57C2"))
                    "Dibatalkan" -> tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#F44336"))
                    else -> tvStatus.setBackgroundColor(android.graphics.Color.GRAY)
                }

                view.setOnClickListener {
                    showDetailPesananDialog(
                        produk = produk,
                        jumlah = jumlah,
                        tanggal = tanggal,
                        alamat = alamat,
                        metode = metode,
                        status = status,
                        total = total,
                        bukti = bukti,
                        id = id,
                        canCancel = canCancel
                    )
                }
            }
        }

        binding.lvOrders.adapter = adapter

        // Real-time: status pesanan berubah otomatis saat admin/kurir memperbaruinya
        listener?.remove()
        listener = FirestoreRepository.observeTransaksiByPembeli(username) { cursor ->
            if (_binding == null) {
                cursor.close()
            } else {
                adapter.changeCursor(cursor)
            }
        }
    }

    private fun showDetailPesananDialog(
        produk: String,
        jumlah: Int,
        tanggal: String,
        alamat: String,
        metode: String,
        status: String,
        total: Double,
        bukti: String,
        id: Int,
        canCancel: Boolean
    ) {
        val message = """
            Produk: $produk
            Jumlah: $jumlah
            Tanggal: $tanggal
            Alamat: $alamat
            Metode Pembayaran: $metode
            Status: $status
            Total: Rp ${String.format(Locale.getDefault(), "%,.0f", total)}
        """.trimIndent()

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Detail Pesanan")
            .setMessage(message)

        if (metode == "QRIS") {
            dialog.setPositiveButton("Lihat Bukti") { _, _ ->
                showBuktiPembayaranDialog(bukti)
            }
        } else if (canCancel) {
            dialog.setPositiveButton(
                "Batalkan Pesanan"
            ) { _, _ ->
                confirmCancelOrder(id)
            }
        } else {
            dialog.setPositiveButton("Tutup", null)
        }

        if (canCancel && metode == "QRIS") {
            dialog.setNeutralButton("Batalkan Pesanan") { _, _ ->
                confirmCancelOrder(id)
            }
        } else if (status == "Dikirim" || status == "Menunggu Konfirmasi Selesai") {
            // QR: ditunjukkan ke kurir saat pesanan sampai
            dialog.setNeutralButton("Tampilkan QR") { _, _ ->
                showQrPesananDialog(id, produk)
            }
        }

        if (metode == "QRIS" || canCancel) {
            dialog.setNegativeButton("Tutup", null)
        }
        dialog.show()
    }

    /** QR generate: isi QR = GASPOINT-<id pesanan>, dibuat dengan ZXing BarcodeEncoder. */
    private fun showQrPesananDialog(id: Int, produk: String) {
        val bitmap: Bitmap = try {
            BarcodeEncoder().encodeBitmap(
                FirestoreRepository.isiQrPesanan(id),
                BarcodeFormat.QR_CODE,
                800,
                800
            )
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Gagal membuat QR", Toast.LENGTH_SHORT).show()
            return
        }

        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val tvInfo = TextView(requireContext()).apply {
            text = "Pesanan #$id - $produk\nTunjukkan QR ini ke kurir saat pesanan sampai."
            gravity = android.view.Gravity.CENTER
            setPadding(pad, 0, pad, 0)
        }

        val imageView = ImageView(requireContext()).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(pad, pad, pad, pad)
        }

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, pad, 0, 0)
            addView(imageView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(tvInfo, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        AlertDialog.Builder(requireContext())
            .setTitle("QR Penerimaan Pesanan")
            .setView(container)
            .setPositiveButton("Tutup", null)
            .show()
    }

    private fun confirmCancelOrder(id: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("Batalkan Pesanan")
            .setMessage("Pesanan yang belum ditugaskan ke kurir akan dibatalkan dan stok dikembalikan.")
            .setPositiveButton("Ya, batalkan") { _, _ ->
                val username = requireActivity()
                    .getSharedPreferences("UserSession", Context.MODE_PRIVATE)
                    .getString("username", "") ?: ""

                FirestoreRepository.batalkanPesananPembeli(id, username) { ok ->
                    if (isAdded) {
                        Toast.makeText(
                            requireContext(),
                            if (ok) "Pesanan berhasil dibatalkan" else "Pesanan tidak dapat dibatalkan. Muat ulang lalu coba lagi.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton("Kembali", null)
            .show()
    }

    private fun showBuktiPembayaranDialog(bukti: String) {
        if (bukti.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Bukti pembayaran belum tersedia",
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
            SupabaseStorageHelper.loadImage(imageView, bukti)
        } catch (e: Exception) {
            imageView.setImageResource(R.drawable.ic_launcher_background)
            Toast.makeText(
                requireContext(),
                "Gagal memuat bukti pembayaran",
                Toast.LENGTH_SHORT
            ).show()
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Bukti Pembayaran QRIS")
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