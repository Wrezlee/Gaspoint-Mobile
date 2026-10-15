package pmluts_gaspoint.kurir

import pmluts_gaspoint.common.SupabaseStorageHelper

import android.Manifest
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
import pmluts_gaspoint.R
import pmluts_gaspoint.common.FirestoreRepository
import com.google.firebase.firestore.ListenerRegistration
import pmluts_gaspoint.databinding.FragmentKurirBinding
import java.io.File
import java.util.Locale

class KurirHomeFragment : Fragment() {

    private var _binding: FragmentKurirBinding? = null
    private val binding get() = _binding!!

    private var listener: ListenerRegistration? = null

    private var idTransaksiUntukBukti: Int = -1
    private var cameraImageUri: Uri? = null

    private val pilihGaleriLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null && idTransaksiUntukBukti != -1) {
                simpanBuktiPengantaran(uri)
            }
        }

    private val ambilFotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { berhasil ->
            if (berhasil && cameraImageUri != null && idTransaksiUntukBukti != -1) {
                simpanBuktiPengantaran(cameraImageUri!!)
            } else {
                Toast.makeText(
                    requireContext(),
                    "Gagal mengambil foto",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val izinKameraLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                bukaKameraKurir()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Izin kamera ditolak",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKurirBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val sharedPref = requireActivity().getSharedPreferences(
            "UserSession",
            Context.MODE_PRIVATE
        )

        val username = sharedPref.getString("username", "") ?: ""
        val fullName = sharedPref.getString("full_name", "Kurir")

        binding.tvWelcomeKurir.text = "Halo, $fullName!"
        binding.switchStatusKerja.isEnabled = false
        binding.lvStok.adapter = null

        binding.btnMulaiTugas.setOnClickListener {
            if (!binding.switchStatusKerja.isChecked) {
                Toast.makeText(
                    requireContext(),
                    "Status nonaktif; tugas yang sudah ditugaskan tetap ditampilkan",
                    Toast.LENGTH_SHORT
                ).show()
            }
            refreshData()
        }

        // Muat status kerja dari Firestore, lalu pasang listener switch
        FirestoreRepository.isKurirAktif(username) { isAktif ->
            if (_binding == null) return@isKurirAktif

            binding.switchStatusKerja.isChecked = isAktif
            binding.switchStatusKerja.isEnabled = true
            updateStatusKerja(isAktif)
            setupStatusSwitch(username)

            refreshData()
        }
    }

    private fun setupStatusSwitch(username: String) {
        binding.switchStatusKerja.setOnCheckedChangeListener { _, isChecked ->
            binding.switchStatusKerja.isEnabled = false
            FirestoreRepository.updateStatusKurir(username, isChecked) { ok ->
                if (_binding == null) return@updateStatusKurir

                binding.switchStatusKerja.isEnabled = true
                if (!ok) {
                    binding.switchStatusKerja.setOnCheckedChangeListener(null)
                    binding.switchStatusKerja.isChecked = !isChecked
                    setupStatusSwitch(username)
                    Toast.makeText(
                        requireContext(),
                        "Gagal memperbarui status kerja. Periksa koneksi lalu coba lagi.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@updateStatusKurir
                }

                updateStatusKerja(isChecked)
                if (isChecked) {
                    Toast.makeText(
                        requireContext(),
                        "Status aktif. Admin bisa menugaskan pesanan ke kamu.",
                        Toast.LENGTH_SHORT
                    ).show()
                    refreshData()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Status nonaktif. Tugas yang sudah ditugaskan tetap tersedia.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun updateStatusKerja(isAktif: Boolean) {
        if (isAktif) {
            binding.tvStatusKerja.text = "Status: Aktif menerima tugas"
            binding.tvInfoTugas.text = "Klik pesanan untuk mengubah status pengantaran"
        } else {
            binding.tvStatusKerja.text = "Status: Nonaktif"
            binding.tvInfoTugas.text = "Tugas yang sudah diberikan tetap bisa diselesaikan"
        }
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
                tvMetode.text = "Metode: $metode"

                when (status) {
                    "Ditugaskan" -> tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#7E57C2"))
                    "Dikirim" -> tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#2196F3"))
                    "Menunggu Konfirmasi Selesai" -> tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#009688"))
                    "Selesai" -> tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
                    else -> tvStatus.setBackgroundColor(android.graphics.Color.GRAY)
                }
            }
        }

        binding.lvStok.adapter = adapter

        // Real-time: tugas dari admin langsung muncul
        listener?.remove()
        listener = FirestoreRepository.observePesananKurir(username) { cursor ->
            if (_binding == null) {
                cursor.close()
            } else {
                adapter.changeCursor(cursor)
            }
        }

        binding.lvStok.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, _, id ->
                showInfoTugas(id.toInt())
            }
    }

    private fun showInfoTugas(idTransaksi: Int) {
        FirestoreRepository.getTransaksiById(idTransaksi) { cursor ->
            if (!isAdded) {
                cursor.close()
                return@getTransaksiById
            }

            if (!cursor.moveToFirst()) {
                cursor.close()
                Toast.makeText(
                    requireContext(),
                    "Data tugas tidak ditemukan",
                    Toast.LENGTH_SHORT
                ).show()
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
            val buktiPengantaran = cursor.getString(cursor.getColumnIndexOrThrow("bukti_pengantaran")) ?: ""

            cursor.close()

            val message = """
            Produk: $produk
            Jumlah: $jumlah
            Pembeli: $pembeli
            Alamat: $alamat
            Metode: $metode
            Status: $status
            Tanggal: $tanggal
            Total: Rp ${String.format(Locale.getDefault(), "%,.0f", total)}
        """.trimIndent()

            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Detail Tugas")
                .setMessage(message)
                .setNeutralButton("Buka Maps") { _, _ -> bukaMaps(alamat) }

            when (status) {
                "Ditugaskan" -> {
                    dialog.setPositiveButton("Tandai Dikirim") { _, _ ->
                        FirestoreRepository.tandaiDikirim(idTransaksi) { ok ->
                            if (isAdded) {
                                Toast.makeText(
                                    requireContext(),
                                    if (ok) "Pesanan ditandai sedang dikirim" else "Gagal mengubah status",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }

                }

                "Dikirim" -> {
                    dialog.setPositiveButton("Upload Bukti") { _, _ ->
                        pilihBuktiPengantaran(idTransaksi)
                    }

                }

                "Menunggu Konfirmasi Selesai" -> {
                    if (buktiPengantaran.isNotEmpty()) {
                        dialog.setPositiveButton("Bukti Sudah Diupload", null)
                    } else {
                        dialog.setPositiveButton("Upload Bukti Lagi") { _, _ ->
                            pilihBuktiPengantaran(idTransaksi)
                        }
                    }

                }

                else -> {
                    dialog.setPositiveButton("OK", null)
                }
            }

            dialog.show()
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

    private fun pilihBuktiPengantaran(idTransaksi: Int) {
        idTransaksiUntukBukti = idTransaksi

        val pilihan = arrayOf(
            "Ambil dari Kamera",
            "Pilih dari Galeri"
        )

        AlertDialog.Builder(requireContext())
            .setTitle("Upload Bukti Pengantaran")
            .setItems(pilihan) { _, which ->
                when (which) {
                    0 -> izinKameraLauncher.launch(Manifest.permission.CAMERA)
                    1 -> pilihGaleriLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun bukaKameraKurir() {
        val imageFile = File(
            requireContext().cacheDir,
            "bukti_pengantaran_${System.currentTimeMillis()}.jpg"
        )

        cameraImageUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            imageFile
        )

        ambilFotoLauncher.launch(cameraImageUri)
    }

    private fun simpanBuktiPengantaran(uri: Uri) {
        val path = simpanGambarKeInternalStorage(uri, "bukti_pengantaran")
        val fileUri = Uri.fromFile(File(path))

        val idTransaksi = idTransaksiUntukBukti

        // unggah ke Supabase Storage, lalu simpan URL-nya ke Firestore
        SupabaseStorageHelper.uploadImage(
            requireContext(),
            fileUri,
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

    private fun simpanGambarKeInternalStorage(uri: Uri, prefix: String): String {
        val inputStream = requireContext().contentResolver.openInputStream(uri)

        val file = File(
            requireContext().filesDir,
            "${prefix}_${System.currentTimeMillis()}.jpg"
        )

        val outputStream = file.outputStream()

        inputStream?.copyTo(outputStream)

        inputStream?.close()
        outputStream.close()

        return file.absolutePath
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
        listener = null
        _binding = null
    }
}