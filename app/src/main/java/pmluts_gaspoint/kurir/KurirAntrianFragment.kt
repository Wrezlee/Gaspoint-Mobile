package pmluts_gaspoint.kurir

import pmluts_gaspoint.common.SupabaseStorageHelper

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import pmluts_gaspoint.R
import pmluts_gaspoint.common.FirestoreRepository
import pmluts_gaspoint.common.LokasiHelper
import pmluts_gaspoint.common.PetaHelper
import com.google.firebase.firestore.ListenerRegistration
import pmluts_gaspoint.databinding.FragmentKurirAntrianBinding
import java.io.File
import java.util.Locale

class KurirAntrianFragment : Fragment() {

    private var _binding: FragmentKurirAntrianBinding? = null
    private val binding get() = _binding!!

    private var listener: ListenerRegistration? = null

    private var selectedTransaksiId = -1
    private var cameraImageUri: Uri? = null

    private val pilihGaleriLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->

            if (uri != null && selectedTransaksiId != -1) {

                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                kirimBuktiPengantaran(selectedTransaksiId, uri)
            }
        }

    private val kameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->

            if (success && cameraImageUri != null && selectedTransaksiId != -1) {

                kirimBuktiPengantaran(selectedTransaksiId, cameraImageUri!!)
            }
        }

    // QR scan: hasil pemindaian QR penerima (dibuat pembeli di halaman Pesanan Saya)
    private val scanQrLauncher =
        registerForActivityResult(ScanContract()) { hasil ->
            val isi = hasil.contents ?: return@registerForActivityResult
            prosesHasilScan(isi)
        }

    private fun bukaScanner() {
        val opsi = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("Arahkan kamera ke QR milik pembeli")
            .setBeepEnabled(true)
            .setOrientationLocked(false)

        scanQrLauncher.launch(opsi)
    }

    /**
     * Cocokkan isi QR dengan pesanan milik kurir ini yang sedang diantar.
     * Cocok -> status Selesai. Tidak cocok -> pesan salah pesanan.
     */
    private fun prosesHasilScan(isi: String) {
        val idQr = FirestoreRepository.idDariQr(isi)

        if (idQr == null) {
            Toast.makeText(requireContext(), "QR bukan milik GasPoint", Toast.LENGTH_LONG).show()
            return
        }

        val username = requireActivity()
            .getSharedPreferences("UserSession", Context.MODE_PRIVATE)
            .getString("username", "") ?: ""

        FirestoreRepository.getTransaksiById(idQr) { cursor ->
            if (!isAdded) {
                cursor.close()
                return@getTransaksiById
            }

            var cocok = false
            var statusSaatIni = ""

            if (cursor.moveToFirst()) {
                val kurirPesanan = cursor.getString(cursor.getColumnIndexOrThrow("kurir_username")) ?: ""
                statusSaatIni = cursor.getString(cursor.getColumnIndexOrThrow("status")) ?: ""
                cocok = kurirPesanan == username
            }
            cursor.close()

            when {
                !cocok -> Toast.makeText(
                    requireContext(),
                    "Salah pesanan: QR ini bukan pesanan yang Anda antar",
                    Toast.LENGTH_LONG
                ).show()

                statusSaatIni == "Selesai" -> Toast.makeText(
                    requireContext(),
                    "Pesanan #$idQr sudah selesai",
                    Toast.LENGTH_SHORT
                ).show()

                statusSaatIni != "Dikirim" && statusSaatIni != "Menunggu Konfirmasi Selesai" -> Toast.makeText(
                    requireContext(),
                    "Pesanan #$idQr belum berstatus Dikirim. Tandai dikirim dulu.",
                    Toast.LENGTH_LONG
                ).show()

                else -> FirestoreRepository.konfirmasiPengantaranQr(idQr) { ok ->
                    if (isAdded) {
                        Toast.makeText(
                            requireContext(),
                            if (ok) "QR cocok. Pesanan #$idQr selesai" else "Gagal menyelesaikan pesanan",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    // unggah bukti ke Supabase Storage, lalu simpan URL-nya ke Firestore
    private fun kirimBuktiPengantaran(idTransaksi: Int, uri: Uri) {
        SupabaseStorageHelper.uploadImage(
            requireContext(),
            uri,
            "bukti-pengantaran"
        ) { url ->
            if (url == null) {
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        "Gagal mengunggah bukti ke Supabase",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@uploadImage
            }

            FirestoreRepository.uploadBuktiPengantaran(idTransaksi, url) { ok ->
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        if (ok) "Bukti pengantaran berhasil diupload" else "Gagal upload bukti pengantaran",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentKurirAntrianBinding.inflate(
            inflater,
            container,
            false
        )

        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnScanQr.setOnClickListener { bukaScanner() }

        refreshData()
    }

    private fun refreshData() {
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
                "pembeli",
                "status",
                "total_harga",
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
                val tvTotal = view.findViewById<TextView>(R.id.tv_transaksi_jumlah)
                val tvMetode = view.findViewById<TextView>(R.id.tv_transaksi_metode)
                val btnDelete = view.findViewById<View>(R.id.btn_delete_transaksi)

                btnDelete.visibility = View.GONE

                val produk = cursor.getString(cursor.getColumnIndexOrThrow("nama_produk"))
                val pembeli = cursor.getString(cursor.getColumnIndexOrThrow("pembeli"))
                val status = cursor.getString(cursor.getColumnIndexOrThrow("status"))
                val total = cursor.getDouble(cursor.getColumnIndexOrThrow("total_harga"))
                val metode = cursor.getString(cursor.getColumnIndexOrThrow("metode_pembayaran")) ?: "-"

                tvProduk.text = produk
                tvPembeli.text = "Pembeli: $pembeli"
                tvStatus.text = status
                tvTotal.text = "Rp " + String.format(Locale.getDefault(), "%,.0f", total)

                val jadwal = cursor.getString(cursor.getColumnIndexOrThrow("jadwal_antar")) ?: ""
                tvMetode.text =
                    if (jadwal.isNotBlank()) "Metode: $metode  |  Antar: $jadwal"
                    else "Metode: $metode"

                when (status) {
                    "Ditugaskan" -> {
                        tvStatus.setBackgroundColor(
                            android.graphics.Color.parseColor("#7E57C2")
                        )
                    }

                    "Dikirim" -> {
                        tvStatus.setBackgroundColor(
                            android.graphics.Color.parseColor("#2196F3")
                        )
                    }

                    "Menunggu Konfirmasi Selesai" -> {
                        tvStatus.setBackgroundColor(
                            android.graphics.Color.parseColor("#009688")
                        )
                    }

                    else -> {
                        tvStatus.setBackgroundColor(
                            android.graphics.Color.GRAY
                        )
                    }
                }
            }
        }

        binding.lvAntrianKirim.adapter = adapter

        // Real-time: tugas baru dari admin langsung muncul
        listener?.remove()
        listener = FirestoreRepository.observePesananKurir(username) { cursor ->
            if (_binding == null) {
                cursor.close()
            } else {
                adapter.changeCursor(cursor)
            }
        }

        binding.lvAntrianKirim.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, _, id ->
                showDetailPesanan(id.toInt())
            }
    }

    private fun showDetailPesanan(idTransaksi: Int) {
        FirestoreRepository.getTransaksiById(idTransaksi) { cursor ->
            if (!isAdded) {
                cursor.close()
                return@getTransaksiById
            }

            if (!cursor.moveToFirst()) {
                cursor.close()
                Toast.makeText(requireContext(), "Data pesanan tidak ditemukan", Toast.LENGTH_SHORT).show()
                return@getTransaksiById
            }

            val produk = cursor.getString(cursor.getColumnIndexOrThrow("nama_produk"))
            val jumlah = cursor.getInt(cursor.getColumnIndexOrThrow("jumlah"))
            val pembeli = cursor.getString(cursor.getColumnIndexOrThrow("pembeli"))
            val alamat = cursor.getString(cursor.getColumnIndexOrThrow("alamat"))
            val status = cursor.getString(cursor.getColumnIndexOrThrow("status"))
            val metode = cursor.getString(cursor.getColumnIndexOrThrow("metode_pembayaran")) ?: "-"
            val total = cursor.getDouble(cursor.getColumnIndexOrThrow("total_harga"))
            val tanggal = cursor.getString(cursor.getColumnIndexOrThrow("tanggal"))
            val jadwalAntar = cursor.getString(cursor.getColumnIndexOrThrow("jadwal_antar")) ?: ""
            val tukarTabung = cursor.getInt(cursor.getColumnIndexOrThrow("tukar_tabung")) == 1

            val iLat = cursor.getColumnIndexOrThrow("alamat_lat")
            val iLng = cursor.getColumnIndexOrThrow("alamat_lng")
            val alamatLat = if (cursor.isNull(iLat)) null else cursor.getDouble(iLat)
            val alamatLng = if (cursor.isNull(iLng)) null else cursor.getDouble(iLng)

            cursor.close()

            val pesan = """
            Produk: $produk
            Jumlah: $jumlah
            Pembeli: $pembeli
            Alamat: $alamat
            Metode: $metode
            Status: $status
            Tanggal pesan: $tanggal
            Jadwal antar: ${jadwalAntar.ifBlank { "-" }}
            Tukar tabung kosong: ${if (tukarTabung) "Ya, bawa pulang tabung kosong" else "Tidak"}
            Total: Rp ${String.format(Locale.getDefault(), "%,.0f", total)}
        """.trimIndent()

            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Detail Pengiriman")
                .setMessage(pesan)
                .setNegativeButton("Lihat Peta") { _, _ ->
                    tampilkanPetaPengantaran(alamat, alamatLat, alamatLng)
                }

            when (status) {
                "Ditugaskan" -> {
                    dialog.setPositiveButton("Tandai Dikirim") { _, _ ->
                        FirestoreRepository.updateStatus(idTransaksi, "Dikirim") { ok ->
                            if (isAdded) {
                                Toast.makeText(
                                    requireContext(),
                                    if (ok) "Status menjadi Dikirim" else "Gagal mengubah status",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }

                "Dikirim" -> {
                    dialog.setPositiveButton("Upload Bukti") { _, _ ->
                        selectedTransaksiId = idTransaksi
                        pilihSumberFoto()
                    }
                }

                "Menunggu Konfirmasi Selesai" -> {
                    dialog.setPositiveButton("Menunggu Admin", null)
                }
            }

            dialog.setNeutralButton("Tutup", null)
            dialog.show()
        }
    }

    private fun pilihSumberFoto() {
        val pilihan = arrayOf(
            "Ambil Foto Kamera",
            "Pilih dari Galeri"
        )

        AlertDialog.Builder(requireContext())
            .setTitle("Upload Bukti Pengantaran")
            .setItems(pilihan) { _, which ->

                when (which) {
                    0 -> bukaKamera()
                    1 -> pilihGaleriLauncher.launch(arrayOf("image/*"))
                }
            }
            .show()
    }

    private fun bukaKamera() {
        val folder = File(
            requireContext().filesDir,
            "bukti_pengantaran"
        )

        if (!folder.exists()) {
            folder.mkdirs()
        }

        val imageFile = File(
            folder,
            "pengantaran_${System.currentTimeMillis()}.jpg"
        )

        cameraImageUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            imageFile
        )

        kameraLauncher.launch(cameraImageUri)
    }

    /**
     * Peta di dalam aplikasi: marker tujuan (pembeli) dan posisi kurir dari GPS.
     * Pesanan lama yang belum punya koordinat tetap bisa dibuka lewat tombol Navigasi.
     */
    private fun tampilkanPetaPengantaran(alamat: String, lat: Double?, lng: Double?) {

        PetaHelper.inisialisasi(requireContext())

        val view = layoutInflater.inflate(
            R.layout.dialog_peta_pengantaran,
            null
        )

        val tvInfo = view.findViewById<TextView>(R.id.tv_info_peta)
        val map = view.findViewById<MapView>(R.id.map_pengantaran)

        val punyaKoordinat = lat != null && lng != null
        val tujuan: GeoPoint? = if (lat != null && lng != null) GeoPoint(lat, lng) else null

        if (tujuan != null) {
            PetaHelper.siapkanPeta(map, tujuan, 16.0)
            PetaHelper.tambahMarker(map, tujuan, "Tujuan: pembeli")
            tvInfo.text = "Penanda merah: alamat pembeli. Mengambil posisi Anda..."
        } else {
            PetaHelper.siapkanPeta(map, PetaHelper.PUSAT_INDONESIA, 5.0)
            tvInfo.text = "Pesanan ini belum punya koordinat. Gunakan Navigasi untuk membuka alamat di aplikasi peta."
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Peta Pengantaran")
            .setView(view)
            .setPositiveButton("Navigasi") { _, _ ->
                bukaMaps(alamat)
            }
            .setNegativeButton("Tutup", null)
            .create()

        // Siklus hidup MapView mengikuti dialog
        dialog.setOnShowListener { map.onResume() }
        dialog.setOnDismissListener {
            map.onPause()
            map.onDetach()
        }
        dialog.show()

        // GPS: posisi kurir saat ini
        LokasiHelper.ambilLokasiSaatIni(
            this,
            onIzinDitolak = {
                tvInfo.text = "Izin lokasi ditolak, posisi Anda tidak ditampilkan."
            }
        ) { lokasi ->

            if (lokasi == null) {
                tvInfo.text = if (punyaKoordinat) {
                    "Posisi Anda tidak terdeteksi. Pastikan GPS aktif."
                } else {
                    "Pesanan ini belum punya koordinat. Gunakan Navigasi untuk membuka alamat di aplikasi peta."
                }
                return@ambilLokasiSaatIni
            }

            val posisiKurir = GeoPoint(lokasi.latitude, lokasi.longitude)
            PetaHelper.tambahMarker(map, posisiKurir, "Posisi Anda")

            if (tujuan != null) {
                // Atur zoom agar posisi kurir dan tujuan sama-sama terlihat
                map.zoomToBoundingBox(
                    BoundingBox.fromGeoPointsSafe(listOf(posisiKurir, tujuan)),
                    true,
                    120
                )
                tvInfo.text = "Penanda biru: posisi Anda. Penanda merah: alamat pembeli."
            } else {
                map.controller.animateTo(posisiKurir)
            }

            map.invalidate()
        }
    }

    private fun bukaMaps(alamat: String) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:0,0?q=${Uri.encode(alamat)}")
        )

        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(
                requireContext(),
                "Aplikasi peta tidak tersedia",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
        listener = null
        _binding = null
    }
}