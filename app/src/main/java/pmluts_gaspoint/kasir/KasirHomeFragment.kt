package pmluts_gaspoint.kasir

import pmluts_gaspoint.common.SupabaseStorageHelper

import android.Manifest
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.card.MaterialCardView
import pmluts_gaspoint.R
import pmluts_gaspoint.common.FirestoreRepository
import pmluts_gaspoint.common.ProdukSuggestHelper
import pmluts_gaspoint.databinding.FragmentHomeBinding
import java.io.File
import java.util.Locale

class KasirHomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ProdukKasirAdapter

    private val listProduk = ArrayList<ProdukKasir>()
    private val listFilter = ArrayList<ProdukKasir>()
    private var suggestAdapter: ProdukSuggestHelper.SuggestAdapter? = null

    private var hasUploadedProof = false
    private var buktiUri: Uri? = null
    private var buktiUrl = "" // URL bukti di Supabase Storage
    private var cameraImageUri: Uri? = null

    private val pilihGaleriLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                val path = simpanGambarKeInternalStorage(
                    uri,
                    "bukti_pembayaran_kasir"
                )

                buktiUri = Uri.fromFile(File(path))

                binding.imgBuktiPembayaran.setImageURI(buktiUri)
                uploadBuktiKeSupabase(buktiUri!!)

                Toast.makeText(
                    requireContext(),
                    "Bukti pembayaran berhasil dipilih",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val ambilFotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { berhasil ->
            if (berhasil && cameraImageUri != null) {
                val path = simpanGambarKeInternalStorage(
                    cameraImageUri!!,
                    "bukti_pembayaran_kasir"
                )

                buktiUri = Uri.fromFile(File(path))

                binding.imgBuktiPembayaran.setImageURI(buktiUri)
                uploadBuktiKeSupabase(buktiUri!!)

                Toast.makeText(
                    requireContext(),
                    "Bukti pembayaran berhasil diambil",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val izinKameraLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                bukaKamera()
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
        _binding = FragmentHomeBinding.inflate(
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

        binding.cardCheckout.visibility = View.GONE
        binding.layoutQrisPayment.visibility = View.GONE
        binding.imgBuktiPembayaran.setImageDrawable(null)
        binding.tvProofStatus.text = "Bukti belum diunggah"

        loadProduk()
        setupRecyclerView()
        setupSearch()
        setupKategoriSpinner()
        setupPaymentMethod()
        updateTotal()

        binding.btnLanjut.setOnClickListener {
            val total = listProduk.sumOf {
                it.harga * it.jumlah
            }

            if (total > 0) {
                binding.cardCheckout.visibility = View.VISIBLE
                binding.btnLanjut.visibility = View.GONE
            } else {
                Toast.makeText(
                    requireContext(),
                    "Pilih produk terlebih dahulu",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.btnProsesTransaksi.setOnClickListener {
            prosesTransaksi()
        }
    }

    private fun loadProduk() {
        FirestoreRepository.getAllProduk { cursor ->
            if (_binding == null) {
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
                            jumlah = 0
                        )
                    )
                } while (cursor.moveToNext())
            }

            cursor.close()

            suggestAdapter?.setSemua(listProduk.map { it.nama })
            refreshFilter()
            updateTotal()
        }
    }

    private fun setupRecyclerView() {
        adapter = ProdukKasirAdapter(listFilter) {
            updateTotal()
        }

        binding.rvProdukKasir.layoutManager =
            GridLayoutManager(requireContext(), 2)

        binding.rvProdukKasir.adapter = adapter
    }

    private fun setupSearch() {
        // AutoComplete: saran nama produk (mis. ketik "gal" -> "Galon Aqua 15L")
        suggestAdapter = ProdukSuggestHelper.pasang(requireContext(), binding.etSearchProduk) {
            refreshFilter()
        }
        suggestAdapter?.setSemua(listProduk.map { it.nama })

        binding.etSearchProduk.addTextChangedListener {
            refreshFilter()
        }
    }

    private fun setupKategoriSpinner() {
        val kategoriList = arrayOf(
            "Semua",
            "Gas",
            "Galon"
        )

        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            kategoriList
        )

        binding.spinnerKategori.adapter = spinnerAdapter

        binding.spinnerKategori.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    refreshFilter()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
    }

    private fun setupPaymentMethod() {
        binding.rgPaymentMethod.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rb_qris) {
                binding.layoutQrisPayment.visibility = View.VISIBLE
            } else {
                binding.layoutQrisPayment.visibility = View.GONE
            }
        }

        binding.btnUploadProof.setOnClickListener {
            val pilihan = arrayOf(
                "Ambil dari Kamera",
                "Pilih dari Galeri"
            )

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
            "bukti_qris_${System.currentTimeMillis()}.jpg"
        )

        cameraImageUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            imageFile
        )

        ambilFotoLauncher.launch(cameraImageUri)
    }

    private fun refreshFilter() {
        val keyword = binding.etSearchProduk.text
            .toString()
            .lowercase(Locale.getDefault())

        val kategoriDipilih =
            binding.spinnerKategori.selectedItem.toString()

        listFilter.clear()

        listFilter.addAll(
            listProduk.filter {
                val cocokNama =
                    it.nama.lowercase(Locale.getDefault()).contains(keyword)

                val cocokKategori =
                    kategoriDipilih == "Semua" || it.kategori == kategoriDipilih

                cocokNama && cocokKategori
            }
        )

        adapter.updateData(ArrayList(listFilter))
    }

    private fun updateTotal() {
        val total = listProduk.sumOf {
            it.harga * it.jumlah
        }

        binding.tvTotalHarga.text =
            "Total: Rp " + String.format(
                Locale.getDefault(),
                "%,.0f",
                total
            )
    }

    private fun prosesTransaksi() {
        val produkDibeli = listProduk.filter {
            it.jumlah > 0
        }

        if (produkDibeli.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Belum ada produk yang dipilih",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val metode = if (binding.rbQris.isChecked) {
            "QRIS"
        } else {
            "CASH"
        }

        if (metode == "QRIS" && !hasUploadedProof) {
            Toast.makeText(
                requireContext(),
                "Harap unggah bukti pembayaran QRIS dahulu",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val totalHarga = produkDibeli.sumOf {
            it.harga * it.jumlah
        }

        val jumlahTotal = produkDibeli.sumOf {
            it.jumlah
        }

        val ringkasanProduk = produkDibeli.joinToString("; ") {
            "${it.nama} x${it.jumlah}"
        }

        binding.btnProsesTransaksi.isEnabled = false

        // Simpan dulu jumlah yang dibeli, karena resetUI() akan mengosongkan jumlah produk
        val itemsStok = produkDibeli.map { it.nama to it.jumlah }

        FirestoreRepository.addTransaksiGabungan(
            ringkasanProduk,
            jumlahTotal,
            totalHarga,
            metode,
            buktiUrl
        ) { ok ->
            if (_binding == null) return@addTransaksiGabungan

            if (!ok) {
                binding.btnProsesTransaksi.isEnabled = true

                Toast.makeText(
                    requireContext(),
                    "Gagal menyimpan transaksi",
                    Toast.LENGTH_SHORT
                ).show()
                return@addTransaksiGabungan
            }

            showStrukDialog(
                produkDibeli,
                metode
            )

            Toast.makeText(
                requireContext(),
                "Transaksi berhasil disimpan",
                Toast.LENGTH_SHORT
            ).show()

            resetUI()
            binding.btnProsesTransaksi.isEnabled = true

            FirestoreRepository.kurangiStokBanyak(itemsStok) {
                if (_binding != null) {
                    loadProduk()
                }
            }
        }
    }

    private fun showStrukDialog(
        produkDibeli: List<ProdukKasir>,
        metode: String
    ) {
        val total = produkDibeli.sumOf {
            it.harga * it.jumlah
        }

        val status = if (metode == "QRIS") {
            "Menunggu Validasi"
        } else {
            "Selesai"
        }

        val container = LinearLayout(requireContext())
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(
            dp(22),
            dp(18),
            dp(22),
            dp(10)
        )

        val headerCard = MaterialCardView(requireContext())
        headerCard.radius = dp(22).toFloat()
        headerCard.cardElevation = dp(2).toFloat()
        headerCard.setCardBackgroundColor(
            Color.parseColor("#00BFA6")
        )

        val headerLayout = LinearLayout(requireContext())
        headerLayout.orientation = LinearLayout.VERTICAL
        headerLayout.gravity = Gravity.CENTER
        headerLayout.setPadding(
            dp(18),
            dp(18),
            dp(18),
            dp(18)
        )

        val tvStore = TextView(requireContext())
        tvStore.text = "GASPOINT"
        tvStore.setTextColor(Color.WHITE)
        tvStore.textSize = 22f
        tvStore.setTypeface(null, Typeface.BOLD)
        tvStore.gravity = Gravity.CENTER

        val tvSubtitle = TextView(requireContext())
        tvSubtitle.text = "Struk Pembayaran"
        tvSubtitle.setTextColor(
            Color.parseColor("#EFFFFC")
        )
        tvSubtitle.textSize = 13f
        tvSubtitle.gravity = Gravity.CENTER
        tvSubtitle.setPadding(
            0,
            dp(4),
            0,
            0
        )

        headerLayout.addView(tvStore)
        headerLayout.addView(tvSubtitle)
        headerCard.addView(headerLayout)

        container.addView(headerCard)

        val infoCard = MaterialCardView(requireContext())
        val infoParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        infoParams.setMargins(
            0,
            dp(14),
            0,
            0
        )

        infoCard.layoutParams = infoParams
        infoCard.radius = dp(20).toFloat()
        infoCard.cardElevation = dp(1).toFloat()
        infoCard.setCardBackgroundColor(
            Color.parseColor("#F7F0FF")
        )
        infoCard.strokeColor =
            Color.parseColor("#E1D4EE")
        infoCard.strokeWidth = dp(1)

        val infoLayout = LinearLayout(requireContext())
        infoLayout.orientation = LinearLayout.VERTICAL
        infoLayout.setPadding(
            dp(16),
            dp(14),
            dp(16),
            dp(14)
        )

        infoLayout.addView(
            createStrukRow(
                "Metode",
                metode
            )
        )

        infoLayout.addView(
            createStrukRow(
                "Status",
                status
            )
        )

        infoCard.addView(infoLayout)
        container.addView(infoCard)

        val itemTitle = TextView(requireContext())
        itemTitle.text = "Detail Produk"
        itemTitle.setTextColor(
            Color.parseColor("#222222")
        )
        itemTitle.textSize = 16f
        itemTitle.setTypeface(null, Typeface.BOLD)
        itemTitle.setPadding(
            0,
            dp(18),
            0,
            dp(8)
        )

        container.addView(itemTitle)

        val itemCard = MaterialCardView(requireContext())
        itemCard.radius = dp(20).toFloat()
        itemCard.cardElevation = dp(1).toFloat()
        itemCard.setCardBackgroundColor(Color.WHITE)
        itemCard.strokeColor =
            Color.parseColor("#E1D4EE")
        itemCard.strokeWidth = dp(1)

        val itemLayout = LinearLayout(requireContext())
        itemLayout.orientation = LinearLayout.VERTICAL
        itemLayout.setPadding(
            dp(16),
            dp(10),
            dp(16),
            dp(10)
        )

        for ((index, produk) in produkDibeli.withIndex()) {
            val subtotal = produk.harga * produk.jumlah

            val itemRow = LinearLayout(requireContext())
            itemRow.orientation = LinearLayout.VERTICAL
            itemRow.setPadding(
                0,
                dp(8),
                0,
                dp(8)
            )

            val tvProduk = TextView(requireContext())
            tvProduk.text = "${produk.nama} x${produk.jumlah}"
            tvProduk.setTextColor(
                Color.parseColor("#222222")
            )
            tvProduk.textSize = 14f
            tvProduk.setTypeface(null, Typeface.BOLD)

            val tvSubtotal = TextView(requireContext())
            tvSubtotal.text =
                "Rp " + String.format(
                    Locale.getDefault(),
                    "%,.0f",
                    subtotal
                )
            tvSubtotal.setTextColor(
                Color.parseColor("#00BFA6")
            )
            tvSubtotal.textSize = 14f
            tvSubtotal.setPadding(
                0,
                dp(3),
                0,
                0
            )

            itemRow.addView(tvProduk)
            itemRow.addView(tvSubtotal)

            itemLayout.addView(itemRow)

            if (index != produkDibeli.lastIndex) {
                val divider = View(requireContext())
                divider.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(1)
                )
                divider.setBackgroundColor(
                    Color.parseColor("#EFE7F5")
                )
                itemLayout.addView(divider)
            }
        }

        itemCard.addView(itemLayout)
        container.addView(itemCard)

        val totalCard = MaterialCardView(requireContext())
        val totalParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        totalParams.setMargins(
            0,
            dp(16),
            0,
            0
        )

        totalCard.layoutParams = totalParams
        totalCard.radius = dp(22).toFloat()
        totalCard.cardElevation = dp(2).toFloat()
        totalCard.setCardBackgroundColor(
            Color.parseColor("#FFFDFD")
        )
        totalCard.strokeColor =
            Color.parseColor("#00BFA6")
        totalCard.strokeWidth = dp(1)

        val totalLayout = LinearLayout(requireContext())
        totalLayout.orientation = LinearLayout.VERTICAL
        totalLayout.setPadding(
            dp(18),
            dp(14),
            dp(18),
            dp(14)
        )

        val tvTotalLabel = TextView(requireContext())
        tvTotalLabel.text = "Total Pembayaran"
        tvTotalLabel.setTextColor(
            Color.parseColor("#777777")
        )
        tvTotalLabel.textSize = 13f

        val tvTotal = TextView(requireContext())
        tvTotal.text =
            "Rp " + String.format(
                Locale.getDefault(),
                "%,.0f",
                total
            )
        tvTotal.setTextColor(
            Color.parseColor("#00BFA6")
        )
        tvTotal.textSize = 26f
        tvTotal.setTypeface(null, Typeface.BOLD)
        tvTotal.setPadding(
            0,
            dp(4),
            0,
            0
        )

        totalLayout.addView(tvTotalLabel)
        totalLayout.addView(tvTotal)

        totalCard.addView(totalLayout)
        container.addView(totalCard)

        AlertDialog.Builder(requireContext())
            .setView(container)
            .setPositiveButton("Selesai", null)
            .show()
    }

    private fun createStrukRow(
        label: String,
        value: String
    ): LinearLayout {
        val row = LinearLayout(requireContext())
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(
            0,
            dp(5),
            0,
            dp(5)
        )

        val tvLabel = TextView(requireContext())
        tvLabel.layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        tvLabel.text = label
        tvLabel.setTextColor(
            Color.parseColor("#777777")
        )
        tvLabel.textSize = 13f

        val tvValue = TextView(requireContext())
        tvValue.layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.4f
        )
        tvValue.text = value
        tvValue.setTextColor(
            Color.parseColor("#222222")
        )
        tvValue.textSize = 13f
        tvValue.setTypeface(null, Typeface.BOLD)
        tvValue.gravity = Gravity.END

        row.addView(tvLabel)
        row.addView(tvValue)

        return row
    }

    private fun simpanGambarKeInternalStorage(
        uri: Uri,
        prefix: String
    ): String {
        val inputStream =
            requireContext().contentResolver.openInputStream(uri)

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
            if (_binding == null) return@uploadImage

            if (url != null) {
                buktiUrl = url
                hasUploadedProof = true
                binding.tvProofStatus.text = "Bukti berhasil diunggah"
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

    private fun resetUI() {
        hasUploadedProof = false
        buktiUri = null
        buktiUrl = ""
        cameraImageUri = null

        for (produk in listProduk) {
            produk.jumlah = 0
        }

        binding.tvProofStatus.text = "Bukti belum diunggah"
        binding.imgBuktiPembayaran.setImageDrawable(null)

        binding.rbCash.isChecked = true
        binding.layoutQrisPayment.visibility = View.GONE
        binding.cardCheckout.visibility = View.GONE
        binding.btnLanjut.visibility = View.VISIBLE
        binding.etSearchProduk.setText("", false)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}