package pmluts_gaspoint.pembeli

import pmluts_gaspoint.common.SupabaseStorageHelper

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import pmluts_gaspoint.R
import pmluts_gaspoint.common.FirestoreRepository
import pmluts_gaspoint.databinding.FragmentPembeliPesanBinding
import pmluts_gaspoint.kasir.ProdukKasir
import pmluts_gaspoint.kasir.ProdukKasirAdapter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PembeliPesanFragment : Fragment(R.layout.fragment_pembeli_pesan) {

    private lateinit var binding: FragmentPembeliPesanBinding
    private lateinit var adapter: ProdukKasirAdapter

    private val listProduk = ArrayList<ProdukKasir>()
    private val listFilter = ArrayList<ProdukKasir>()

    private var selectedAlamat = ""
    // Koordinat tiap alamat (kunci: teks yang tampil di dropdown)
    private val alamatKoordinat = mutableMapOf<String, Pair<Double, Double>>()
    private var metodePembayaran = ""
    private var hasUploadedProof = false
    private var buktiPembayaranUri: Uri? = null
    private var buktiUrl = "" // URL bukti di Supabase Storage
    private var cameraImageUri: Uri? = null

    // Jadwal pengantaran (DatePickerDialog + TimePickerDialog)
    private val jadwalAntar: Calendar = Calendar.getInstance()
    private var tanggalAntarDipilih = false
    private var jamAntarDipilih = false

    private val formatJadwal = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val formatTanggal = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    private val pilihGaleriLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                val path = simpanGambarKeInternalStorage(uri, "bukti_pembayaran_pembeli")
                buktiPembayaranUri = Uri.fromFile(File(path))
                binding.imgBuktiPembayaran.setImageURI(buktiPembayaranUri)
                uploadBuktiKeSupabase(buktiPembayaranUri!!)
            }
        }

    private val ambilFotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { berhasil ->
            if (berhasil && cameraImageUri != null) {
                val path = simpanGambarKeInternalStorage(cameraImageUri!!, "bukti_pembayaran_pembeli")
                buktiPembayaranUri = Uri.fromFile(File(path))
                binding.imgBuktiPembayaran.setImageURI(buktiPembayaranUri)
                uploadBuktiKeSupabase(buktiPembayaranUri!!)
            }
        }

    private val izinKameraLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                bukaKamera()
            } else {
                Toast.makeText(requireContext(), "Izin kamera ditolak", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = FragmentPembeliPesanBinding.bind(view)

        binding.cardCheckout.visibility = View.GONE
        binding.layoutQrisPayment.visibility = View.GONE

        loadProduk()
        setupRecyclerView()
        setupKategoriSpinner()
        setupSearch()
        setupAlamat()
        setupPaymentMethod()
        setupJadwalAntar()
        updateTotal()

        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnLanjut.setOnClickListener {
            lanjutKeCheckout()
        }

        binding.btnCheckout.setOnClickListener {
            konfirmasiPesanan()
        }
    }

    // ------------------------------------------------------------------
    // Jadwal pengantaran: DatePickerDialog + TimePickerDialog
    // ------------------------------------------------------------------

    private fun setupJadwalAntar() {
        binding.btnTanggalAntar.setOnClickListener {
            val kal = Calendar.getInstance()

            val dialog = DatePickerDialog(
                requireContext(),
                { _, tahun, bulan, hari ->
                    jadwalAntar.set(tahun, bulan, hari)
                    tanggalAntarDipilih = true
                    binding.btnTanggalAntar.text = formatTanggal.format(jadwalAntar.time)

                    // jam yang sudah dipilih bisa jadi sudah lewat bila tanggal diganti ke hari ini
                    if (jamAntarDipilih && jadwalSudahLewat()) {
                        jamAntarDipilih = false
                        binding.btnJamAntar.text = "Pilih jam"
                    }
                },
                kal.get(Calendar.YEAR),
                kal.get(Calendar.MONTH),
                kal.get(Calendar.DAY_OF_MONTH)
            )

            // tidak boleh memilih tanggal yang sudah lewat
            dialog.datePicker.minDate = System.currentTimeMillis() - 1000
            dialog.show()
        }

        binding.btnJamAntar.setOnClickListener {
            val kal = Calendar.getInstance()

            TimePickerDialog(
                requireContext(),
                { _, jam, menit ->
                    val sebelumJam = jadwalAntar.get(Calendar.HOUR_OF_DAY)
                    val sebelumMenit = jadwalAntar.get(Calendar.MINUTE)

                    jadwalAntar.set(Calendar.HOUR_OF_DAY, jam)
                    jadwalAntar.set(Calendar.MINUTE, menit)
                    jadwalAntar.set(Calendar.SECOND, 0)
                    jadwalAntar.set(Calendar.MILLISECOND, 0)

                    if (tanggalAntarDipilih && jadwalSudahLewat()) {
                        // kembalikan nilai lama
                        jadwalAntar.set(Calendar.HOUR_OF_DAY, sebelumJam)
                        jadwalAntar.set(Calendar.MINUTE, sebelumMenit)

                        Toast.makeText(
                            requireContext(),
                            "Jam pengantaran sudah lewat, pilih jam lain",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        jamAntarDipilih = true
                        binding.btnJamAntar.text =
                            String.format(Locale.getDefault(), "%02d:%02d", jam, menit)
                    }
                },
                kal.get(Calendar.HOUR_OF_DAY),
                kal.get(Calendar.MINUTE),
                true // format 24 jam
            ).show()
        }
    }

    private fun jadwalSudahLewat(): Boolean =
        jadwalAntar.timeInMillis < System.currentTimeMillis()

    private fun setupPaymentMethod() {
        binding.rgPaymentMethod.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_cod -> {
                    metodePembayaran = "COD"
                    binding.layoutQrisPayment.visibility = View.GONE
                }

                R.id.rb_qris -> {
                    metodePembayaran = "QRIS"
                    binding.layoutQrisPayment.visibility = View.VISIBLE
                }
            }
        }

        binding.btnUploadProof.setOnClickListener {
            val pilihan = arrayOf("Ambil dari Kamera", "Pilih dari Galeri")

            AlertDialog.Builder(requireContext())
                .setTitle("Pilih Bukti Pembayaran")
                .setItems(pilihan) { _, which ->
                    when (which) {
                        0 -> izinKameraLauncher.launch(Manifest.permission.CAMERA)
                        1 -> pilihGaleriLauncher.launch("image/*")
                    }
                }
                .show()
        }
    }

    private fun bukaKamera() {
        val imageFile = File(
            requireContext().cacheDir,
            "bukti_pembayaran_${System.currentTimeMillis()}.jpg"
        )

        cameraImageUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            imageFile
        )

        ambilFotoLauncher.launch(cameraImageUri)
    }

    private fun loadProduk() {
        FirestoreRepository.getAllProduk { cursor ->
            if (!isAdded) {
                cursor.close()
                return@getAllProduk
            }

            listProduk.clear()

            if (cursor.moveToFirst()) {
                do {
                    listProduk.add(
                        ProdukKasir(
                            nama = cursor.getString(cursor.getColumnIndexOrThrow("nama")),
                            harga = cursor.getDouble(cursor.getColumnIndexOrThrow("harga")),
                            stok = cursor.getInt(cursor.getColumnIndexOrThrow("stok")),
                            deskripsi = cursor.getString(cursor.getColumnIndexOrThrow("deskripsi")),
                            kategori = cursor.getString(cursor.getColumnIndexOrThrow("kategori")),
                            gambar = cursor.getString(cursor.getColumnIndexOrThrow("gambar")) ?: "",
                            id = cursor.getInt(cursor.getColumnIndexOrThrow("_id")),
                            jumlah = 0
                        )
                    )
                } while (cursor.moveToNext())
            }

            cursor.close()

            refreshFilter()
            updateTotal()
        }
    }

    private fun setupRecyclerView() {
        adapter = ProdukKasirAdapter(listFilter) {
            updateTotal()
        }

        binding.rvProdukPembeli.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvProdukPembeli.adapter = adapter
    }

    private fun setupKategoriSpinner() {
        val kategoriList = arrayOf("Semua", "Gas", "Galon")

        binding.spinnerKategori.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            kategoriList
        )

        binding.spinnerKategori.setOnItemSelectedListener(
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    refreshFilter()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        )
    }

    private fun setupSearch() {
        binding.etSearchProduk.addTextChangedListener {
            refreshFilter()
        }
    }

    private fun refreshFilter() {
        val keyword = binding.etSearchProduk.text.toString().lowercase(Locale.getDefault())
        val kategoriDipilih = binding.spinnerKategori.selectedItem.toString()

        listFilter.clear()
        listFilter.addAll(
            listProduk.filter { produk ->
                val cocokNama = produk.nama.lowercase(Locale.getDefault()).contains(keyword)
                val cocokKategori = kategoriDipilih == "Semua" || produk.kategori == kategoriDipilih
                cocokNama && cocokKategori
            }
        )

        adapter.updateData(ArrayList(listFilter))
    }

    private fun setupAlamat() {
        val username = requireActivity()
            .getSharedPreferences("UserSession", Context.MODE_PRIVATE)
            .getString("username", "") ?: ""

        binding.actAlamat.setOnClickListener {
            binding.actAlamat.showDropDown()
        }

        binding.actAlamat.setOnItemClickListener { parent, _, position, _ ->
            selectedAlamat = parent.getItemAtPosition(position).toString()
            binding.actAlamat.setText(selectedAlamat, false)
        }

        FirestoreRepository.getAlamatByUsername(username) { cursor ->
            if (!isAdded) {
                cursor.close()
                return@getAlamatByUsername
            }

            val alamatList = mutableListOf<String>()
            alamatKoordinat.clear()

            if (cursor.moveToFirst()) {
                do {
                    val label = cursor.getString(cursor.getColumnIndexOrThrow("label"))
                    val detail = cursor.getString(cursor.getColumnIndexOrThrow("detail"))
                    val teks = "$label - $detail"
                    alamatList.add(teks)

                    val iLat = cursor.getColumnIndexOrThrow("lat")
                    val iLng = cursor.getColumnIndexOrThrow("lng")
                    if (!cursor.isNull(iLat) && !cursor.isNull(iLng)) {
                        alamatKoordinat[teks] =
                            Pair(cursor.getDouble(iLat), cursor.getDouble(iLng))
                    }
                } while (cursor.moveToNext())
            }

            cursor.close()

            if (alamatList.isEmpty()) {
                alamatList.add("Belum ada alamat tersimpan")
            }

            binding.actAlamat.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, alamatList)
            )

            if (alamatList[0] != "Belum ada alamat tersimpan") {
                selectedAlamat = ""
                binding.actAlamat.setText("", false)
            } else {
                selectedAlamat = ""
                binding.actAlamat.setText("Belum ada alamat tersimpan", false)
            }
        }
    }

    private fun updateTotal() {
        val total = listProduk.sumOf {
            it.harga * it.jumlah
        }

        binding.tvTotal.text =
            "Total: Rp " + String.format(Locale.getDefault(), "%,.0f", total)
    }

    private fun lanjutKeCheckout() {
        val produkDipesan = listProduk.filter {
            it.jumlah > 0
        }

        if (produkDipesan.isEmpty()) {
            Toast.makeText(requireContext(), "Pilih produk terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        if (produkDipesan.size > MAX_JENIS_PRODUK_PESANAN) {
            Toast.makeText(
                requireContext(),
                "Maksimal $MAX_JENIS_PRODUK_PESANAN jenis produk dalam satu pesanan",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        binding.cardCheckout.visibility = View.VISIBLE
        binding.btnLanjut.visibility = View.GONE
    }

    private fun konfirmasiPesanan() {
        val username = requireActivity()
            .getSharedPreferences("UserSession", Context.MODE_PRIVATE)
            .getString("username", "") ?: ""

        val produkDipesan = listProduk.filter {
            it.jumlah > 0
        }

        if (produkDipesan.isEmpty()) {
            Toast.makeText(requireContext(), "Pilih produk terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        if (produkDipesan.size > MAX_JENIS_PRODUK_PESANAN) {
            Toast.makeText(
                requireContext(),
                "Maksimal $MAX_JENIS_PRODUK_PESANAN jenis produk dalam satu pesanan",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (selectedAlamat.isEmpty() || selectedAlamat == "Belum ada alamat tersimpan") {
            Toast.makeText(requireContext(), "Pilih alamat pengiriman terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        if (!tanggalAntarDipilih || !jamAntarDipilih) {
            Toast.makeText(requireContext(), "Pilih tanggal dan jam pengantaran terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        if (jadwalSudahLewat()) {
            Toast.makeText(requireContext(), "Jadwal pengantaran sudah lewat, pilih ulang", Toast.LENGTH_SHORT).show()
            return
        }

        if (metodePembayaran.isEmpty()) {
            Toast.makeText(requireContext(), "Pilih metode pembayaran terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        if (metodePembayaran == "QRIS" && !hasUploadedProof) {
            Toast.makeText(requireContext(), "Unggah bukti pembayaran QRIS terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        val items = produkDipesan.map {
            FirestoreRepository.OrderItem(it.id, it.jumlah)
        }
        val koordinat = alamatKoordinat[selectedAlamat]
        val expectedTotal = produkDipesan.sumOf { it.harga * it.jumlah }

        binding.btnCheckout.isEnabled = false

        FirestoreRepository.buatPesananPembeli(
            items,
            username,
            selectedAlamat,
            metodePembayaran,
            expectedTotal,
            buktiUrl,
            formatJadwal.format(jadwalAntar.time),
            binding.cbTukarTabung.isChecked,
            koordinat?.first,
            koordinat?.second
        ) { ok ->
            if (!isAdded) return@buatPesananPembeli

            binding.btnCheckout.isEnabled = true

            if (!ok) {
                Toast.makeText(
                    requireContext(),
                    "Pesanan gagal. Stok atau harga mungkin berubah; periksa koneksi lalu coba kembali.",
                    Toast.LENGTH_LONG
                ).show()
                loadProduk()
                return@buatPesananPembeli
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Berhasil")
                .setMessage("Pemesanan berhasil dibuat")
                .setPositiveButton("OK", null)
                .show()

            resetPesanan()
            loadProduk()
        }
    }

    // unggah bukti pembayaran ke Supabase Storage
    private fun uploadBuktiKeSupabase(uri: Uri) {
        hasUploadedProof = false
        buktiUrl = ""
        binding.tvProofStatus.text = "Mengunggah bukti..."

        SupabaseStorageHelper.uploadImage(
            requireContext(),
            uri,
            "bukti-pembayaran"
        ) { url ->
            if (!isAdded) return@uploadImage

            if (url != null) {
                buktiUrl = url
                hasUploadedProof = true
                binding.tvProofStatus.text = "Bukti pembayaran berhasil diunggah"
            } else {
                binding.tvProofStatus.text = "Bukti gagal diunggah, coba lagi"
                Toast.makeText(
                    requireContext(),
                    "Gagal mengunggah bukti ke Supabase",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun resetPesanan() {
        for (produk in listProduk) {
            produk.jumlah = 0
        }

        selectedAlamat = ""
        metodePembayaran = ""
        hasUploadedProof = false
        buktiPembayaranUri = null
        buktiUrl = ""
        cameraImageUri = null

        binding.actAlamat.setText("", false)

        tanggalAntarDipilih = false
        jamAntarDipilih = false
        binding.btnTanggalAntar.text = "Pilih tanggal"
        binding.btnJamAntar.text = "Pilih jam"
        binding.cbTukarTabung.isChecked = false

        binding.rgPaymentMethod.clearCheck()
        binding.layoutQrisPayment.visibility = View.GONE
        binding.imgBuktiPembayaran.setImageResource(R.drawable.ic_launcher_background)
        binding.tvProofStatus.text = "Bukti belum diunggah"
        binding.cardCheckout.visibility = View.GONE
        binding.btnLanjut.visibility = View.VISIBLE

        refreshFilter()
        setupAlamat()
        updateTotal()
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

    private companion object {
        const val MAX_JENIS_PRODUK_PESANAN = 8
    }
}
