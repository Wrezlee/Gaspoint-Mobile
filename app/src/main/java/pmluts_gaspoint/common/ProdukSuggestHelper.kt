package pmluts_gaspoint.common

import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Filter
import pmluts_gaspoint.R
import java.util.Locale

/**
 * AutoCompleteTextView untuk pencarian produk (Kasir & Pembeli).
 *
 * Saran diambil dari nama produk yang tersedia di Firestore. Pencocokan memakai "contains"
 * (tidak peduli huruf besar/kecil), sehingga mengetik "gal" menyarankan "Galon Aqua 15L",
 * dan "qua" juga tetap menemukan "Galon Aqua 15L".
 */
object ProdukSuggestHelper {

    class SuggestAdapter(context: Context) :
        ArrayAdapter<String>(context, R.layout.item_suggest_produk, mutableListOf()) {

        private var semua: List<String> = emptyList()

        fun setSemua(daftarNama: List<String>) {
            semua = daftarNama.distinct().sortedBy { it.lowercase(Locale.getDefault()) }
            clear()
            addAll(semua)
            notifyDataSetChanged()
        }

        override fun getFilter(): Filter = object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val kata = constraint?.toString()?.trim()?.lowercase(Locale.getDefault()) ?: ""
                val hasil = if (kata.isEmpty()) {
                    emptyList()
                } else {
                    // nama yang diawali kata dahulu, baru yang hanya memuat kata
                    val (awal, memuat) = semua
                        .filter { it.lowercase(Locale.getDefault()).contains(kata) }
                        .partition { it.lowercase(Locale.getDefault()).startsWith(kata) }
                    awal + memuat
                }
                return FilterResults().apply {
                    values = hasil
                    count = hasil.size
                }
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                clear()
                addAll((results?.values as? List<String>) ?: emptyList())
                if (results != null && results.count > 0) notifyDataSetChanged()
                else notifyDataSetInvalidated()
            }

            override fun convertResultToString(resultValue: Any?): CharSequence =
                resultValue?.toString() ?: ""
        }
    }

    /** Pasang adapter saran ke kolom pencarian. [onPilih] dipanggil saat saran diketuk. */
    fun pasang(context: Context, kolom: AutoCompleteTextView, onPilih: () -> Unit): SuggestAdapter {
        val adapter = SuggestAdapter(context)
        kolom.setAdapter(adapter)
        kolom.threshold = 1
        kolom.setOnItemClickListener { _, _, _, _ ->
            sembunyikanKeyboard(context, kolom)
            kolom.clearFocus()
            onPilih()
        }
        return adapter
    }

    private fun sembunyikanKeyboard(context: Context, kolom: AutoCompleteTextView) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(kolom.windowToken, 0)
    }
}
