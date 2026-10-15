package pmluts_gaspoint.admin

import android.app.DatePickerDialog
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import pmluts_gaspoint.common.FirestoreRepository
import pmluts_gaspoint.databinding.FragmentAdminReportsBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AdminReportsFragment : Fragment() {

    private var _binding: FragmentAdminReportsBinding? = null
    private val binding get() = _binding!!

    // Filter rentang tanggal (null = tidak dibatasi)
    private var tanggalDari: Calendar? = null
    private var tanggalSampai: Calendar? = null

    // Format tanggal transaksi di Firestore: "dd/MM/yyyy HH:mm"
    private val formatTransaksi = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val formatTampil = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminReportsBinding.inflate(
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

        setupFilterTanggal()
        loadReportData()
    }

    // ------------------------------------------------------------------
    // Filter tanggal (DatePickerDialog)
    // ------------------------------------------------------------------

    private fun setupFilterTanggal() {
        updateLabelFilter()

        binding.btnTanggalDari.setOnClickListener {
            tampilkanDatePicker(tanggalDari) { dipilih ->
                // awal hari 00:00:00
                dipilih.set(Calendar.HOUR_OF_DAY, 0)
                dipilih.set(Calendar.MINUTE, 0)
                dipilih.set(Calendar.SECOND, 0)
                dipilih.set(Calendar.MILLISECOND, 0)

                val sampai = tanggalSampai
                if (sampai != null && dipilih.after(sampai)) {
                    Toast.makeText(
                        requireContext(),
                        "Tanggal awal tidak boleh setelah tanggal akhir",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@tampilkanDatePicker
                }

                tanggalDari = dipilih
                updateLabelFilter()
                loadReportData()
            }
        }

        binding.btnTanggalSampai.setOnClickListener {
            tampilkanDatePicker(tanggalSampai) { dipilih ->
                // akhir hari 23:59:59
                dipilih.set(Calendar.HOUR_OF_DAY, 23)
                dipilih.set(Calendar.MINUTE, 59)
                dipilih.set(Calendar.SECOND, 59)
                dipilih.set(Calendar.MILLISECOND, 999)

                val dari = tanggalDari
                if (dari != null && dipilih.before(dari)) {
                    Toast.makeText(
                        requireContext(),
                        "Tanggal akhir tidak boleh sebelum tanggal awal",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@tampilkanDatePicker
                }

                tanggalSampai = dipilih
                updateLabelFilter()
                loadReportData()
            }
        }

        binding.btnResetFilter.setOnClickListener {
            tanggalDari = null
            tanggalSampai = null
            updateLabelFilter()
            loadReportData()
        }
    }

    private fun tampilkanDatePicker(
        awal: Calendar?,
        onDipilih: (Calendar) -> Unit
    ) {
        val kal = awal ?: Calendar.getInstance()

        DatePickerDialog(
            requireContext(),
            { _, tahun, bulan, hari ->
                val hasil = Calendar.getInstance()
                hasil.set(tahun, bulan, hari)
                onDipilih(hasil)
            },
            kal.get(Calendar.YEAR),
            kal.get(Calendar.MONTH),
            kal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateLabelFilter() {
        binding.btnTanggalDari.text =
            tanggalDari?.let { "Dari: " + formatTampil.format(it.time) } ?: "Dari tanggal"

        binding.btnTanggalSampai.text =
            tanggalSampai?.let { "Sampai: " + formatTampil.format(it.time) } ?: "Sampai tanggal"
    }

    // true jika tanggal transaksi berada di dalam rentang filter
    private fun masukRentang(tanggalTransaksi: String): Boolean {
        if (tanggalDari == null && tanggalSampai == null) return true

        val waktu: Date = runCatching { formatTransaksi.parse(tanggalTransaksi) }
            .getOrNull() ?: return false

        tanggalDari?.let { if (waktu.before(it.time)) return false }
        tanggalSampai?.let { if (waktu.after(it.time)) return false }

        return true
    }

    override fun onResume() {
        super.onResume()
        loadReportData()
    }

    private fun loadReportData() {
        FirestoreRepository.getTransaksiByStatus("Selesai") { cursor ->
            if (_binding == null) {
                cursor.close()
                return@getTransaksiByStatus
            }

            tampilkanReport(cursor)
        }
    }

    private fun tampilkanReport(cursor: Cursor) {
        var totalPendapatan = 0.0
        var totalTransaksi = 0
        var totalProdukTerjual = 0

        binding.layoutReportList.removeAllViews()

        if (cursor.moveToFirst()) {
            do {
                val tanggal = cursor.getString(
                    cursor.getColumnIndexOrThrow("tanggal")
                ) ?: "-"

                // lewati transaksi di luar rentang tanggal filter
                if (!masukRentang(tanggal)) continue

                val produk = cursor.getString(
                    cursor.getColumnIndexOrThrow("nama_produk")
                )

                val jumlah = cursor.getInt(
                    cursor.getColumnIndexOrThrow("jumlah")
                )

                val totalHarga = cursor.getDouble(
                    cursor.getColumnIndexOrThrow("total_harga")
                )

                val pembeli = cursor.getString(
                    cursor.getColumnIndexOrThrow("pembeli")
                )

                val metode = cursor.getString(
                    cursor.getColumnIndexOrThrow("metode_pembayaran")
                ) ?: "-"

                totalPendapatan += totalHarga
                totalTransaksi += 1
                totalProdukTerjual += jumlah

                addReportItem(
                    produk = produk,
                    jumlah = jumlah,
                    totalHarga = totalHarga,
                    pembeli = pembeli,
                    metode = metode,
                    tanggal = tanggal
                )

            } while (cursor.moveToNext())
            // ada data, tetapi semuanya di luar rentang filter
            if (totalTransaksi == 0) addEmptyReport()
        } else {
            addEmptyReport()
        }

        cursor.close()

        binding.tvTotalPendapatan.text =
            "Rp " + String.format(
                Locale.getDefault(),
                "%,.0f",
                totalPendapatan
            )

        binding.tvTotalTransaksi.text = totalTransaksi.toString()
        binding.tvProdukTerjual.text = totalProdukTerjual.toString()
    }

    private fun addReportItem(
        produk: String,
        jumlah: Int,
        totalHarga: Double,
        pembeli: String,
        metode: String,
        tanggal: String
    ) {
        val card = MaterialCardView(requireContext())

        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        cardParams.setMargins(
            0,
            0,
            0,
            dp(14)
        )

        card.layoutParams = cardParams
        card.radius = dp(22).toFloat()
        card.cardElevation = dp(2).toFloat()
        card.setCardBackgroundColor(Color.WHITE)
        card.strokeColor = Color.parseColor("#E6DDF0")
        card.strokeWidth = dp(1)

        val container = LinearLayout(requireContext())
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(
            dp(18),
            dp(16),
            dp(18),
            dp(16)
        )

        val topRow = LinearLayout(requireContext())
        topRow.orientation = LinearLayout.HORIZONTAL
        topRow.gravity = Gravity.CENTER_VERTICAL

        val tvProduk = TextView(requireContext())
        tvProduk.layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        tvProduk.text = rapikanNamaProduk(produk)
        tvProduk.setTextColor(Color.parseColor("#222222"))
        tvProduk.textSize = 15f
        tvProduk.setTypeface(null, Typeface.BOLD)
        tvProduk.maxLines = 3

        val tvMetode = TextView(requireContext())
        tvMetode.text = metode
        tvMetode.setTextColor(Color.WHITE)
        tvMetode.textSize = 11f
        tvMetode.setTypeface(null, Typeface.BOLD)
        tvMetode.gravity = Gravity.CENTER
        tvMetode.setPadding(
            dp(10),
            dp(5),
            dp(10),
            dp(5)
        )

        val metodeBg = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()

            setColor(
                if (metode.uppercase(Locale.getDefault()) == "QRIS") {
                    Color.parseColor("#00BFA6")
                } else {
                    Color.parseColor("#7E57C2")
                }
            )
        }

        tvMetode.background = metodeBg

        topRow.addView(tvProduk)
        topRow.addView(tvMetode)

        val tvTotalLabel = TextView(requireContext())
        tvTotalLabel.text = "Total Pembayaran"
        tvTotalLabel.setTextColor(Color.parseColor("#888888"))
        tvTotalLabel.textSize = 12f
        tvTotalLabel.setPadding(
            0,
            dp(14),
            0,
            0
        )

        val tvTotal = TextView(requireContext())
        tvTotal.text =
            "Rp " + String.format(
                Locale.getDefault(),
                "%,.0f",
                totalHarga
            )
        tvTotal.setTextColor(Color.parseColor("#00BFA6"))
        tvTotal.textSize = 24f
        tvTotal.setTypeface(null, Typeface.BOLD)
        tvTotal.setPadding(
            0,
            dp(2),
            0,
            dp(12)
        )

        val divider = View(requireContext())
        divider.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(1)
        )
        divider.setBackgroundColor(Color.parseColor("#F0E8F7"))

        val detailContainer = LinearLayout(requireContext())
        detailContainer.orientation = LinearLayout.VERTICAL
        detailContainer.setPadding(
            0,
            dp(10),
            0,
            0
        )

        detailContainer.addView(
            createDetailRow(
                label = "Jumlah",
                value = "$jumlah item"
            )
        )

        detailContainer.addView(
            createDetailRow(
                label = "Pembeli",
                value = pembeli
            )
        )

        detailContainer.addView(
            createDetailRow(
                label = "Tanggal",
                value = tanggal
            )
        )

        container.addView(topRow)
        container.addView(tvTotalLabel)
        container.addView(tvTotal)
        container.addView(divider)
        container.addView(detailContainer)

        card.addView(container)

        binding.layoutReportList.addView(card)
    }

    private fun createDetailRow(
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
        tvLabel.setTextColor(Color.parseColor("#888888"))
        tvLabel.textSize = 13f

        val tvValue = TextView(requireContext())
        tvValue.layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.7f
        )
        tvValue.text = value
        tvValue.setTextColor(Color.parseColor("#333333"))
        tvValue.textSize = 13f
        tvValue.gravity = Gravity.END

        row.addView(tvLabel)
        row.addView(tvValue)

        return row
    }

    private fun addEmptyReport() {
        val card = MaterialCardView(requireContext())

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        params.setMargins(
            0,
            0,
            0,
            dp(14)
        )

        card.layoutParams = params
        card.radius = dp(22).toFloat()
        card.cardElevation = dp(2).toFloat()
        card.setCardBackgroundColor(Color.WHITE)
        card.strokeColor = Color.parseColor("#E6DDF0")
        card.strokeWidth = dp(1)

        val container = LinearLayout(requireContext())
        container.orientation = LinearLayout.VERTICAL
        container.gravity = Gravity.CENTER
        container.setPadding(
            dp(18),
            dp(28),
            dp(18),
            dp(28)
        )

        val tvTitle = TextView(requireContext())
        tvTitle.text = "Belum ada pesanan selesai"
        tvTitle.setTextColor(Color.parseColor("#222222"))
        tvTitle.textSize = 16f
        tvTitle.setTypeface(null, Typeface.BOLD)
        tvTitle.gravity = Gravity.CENTER

        val tvSubtitle = TextView(requireContext())
        tvSubtitle.text = "Transaksi yang sudah selesai akan tampil di sini."
        tvSubtitle.setTextColor(Color.parseColor("#777777"))
        tvSubtitle.textSize = 13f
        tvSubtitle.gravity = Gravity.CENTER
        tvSubtitle.setPadding(
            0,
            dp(6),
            0,
            0
        )

        container.addView(tvTitle)
        container.addView(tvSubtitle)

        card.addView(container)
        binding.layoutReportList.addView(card)
    }

    private fun rapikanNamaProduk(text: String): String {
        return text
            .replace(";", "\n")
            .replace("  ", " ")
            .trim()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}