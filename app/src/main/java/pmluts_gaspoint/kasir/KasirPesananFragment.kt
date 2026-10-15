package pmluts_gaspoint.kasir

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import pmluts_gaspoint.R
import pmluts_gaspoint.databinding.FragmentKasirPesananBinding
import java.util.Locale

class KasirPesananFragment : Fragment(R.layout.fragment_kasir_pesanan) {

    private lateinit var binding: FragmentKasirPesananBinding
    private lateinit var adapter: ProdukKasirAdapter

    private val listProduk = arrayListOf(
        ProdukKasir(
            nama = "DJOES BUAH NAGA",
            harga = 10000.0,
            stok = 50,
            deskripsi = "Jus buah naga segar",
            kategori = "Galon",
            gambar = ""
        ),
        ProdukKasir(
            nama = "DJOES SIRSAK",
            harga = 10000.0,
            stok = 50,
            deskripsi = "Jus sirsak segar",
            kategori = "Galon",
            gambar = ""
        ),
        ProdukKasir(
            nama = "DJOES JAMBU",
            harga = 10000.0,
            stok = 50,
            deskripsi = "Jus jambu segar",
            kategori = "Galon",
            gambar = ""
        ),
        ProdukKasir(
            nama = "DJOES NANAS",
            harga = 10000.0,
            stok = 50,
            deskripsi = "Jus nanas segar",
            kategori = "Galon",
            gambar = ""
        ),
        ProdukKasir(
            nama = "DJOES ALPUKAT",
            harga = 12000.0,
            stok = 50,
            deskripsi = "Jus alpukat segar",
            kategori = "Galon",
            gambar = ""
        ),
        ProdukKasir(
            nama = "DJOES MANGGA",
            harga = 12000.0,
            stok = 50,
            deskripsi = "Jus mangga segar",
            kategori = "Galon",
            gambar = ""
        )
    )

    private val listFilter = ArrayList<ProdukKasir>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentKasirPesananBinding.bind(view)

        listFilter.clear()
        listFilter.addAll(listProduk)

        adapter = ProdukKasirAdapter(listFilter) {
            updateTotal()
        }

        binding.rvProduk.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvProduk.adapter = adapter

        binding.edtSearchProduk.addTextChangedListener {
            refreshFilter()
        }

        binding.btnSimpanTransaksi.setOnClickListener {
            simpanTransaksi()
        }

        updateTotal()
    }

    private fun refreshFilter() {
        val keyword = binding.edtSearchProduk.text.toString().lowercase(Locale.getDefault())

        listFilter.clear()

        if (keyword.isEmpty()) {
            listFilter.addAll(listProduk)
        } else {
            listFilter.addAll(
                listProduk.filter { produk ->
                    produk.nama.lowercase(Locale.getDefault()).contains(keyword)
                }
            )
        }

        adapter.updateData(ArrayList(listFilter))
    }

    private fun updateTotal() {
        val total = listProduk.sumOf { produk ->
            produk.harga * produk.jumlah
        }

        binding.tvTotalTransaksi.text =
            "Total: Rp " + String.format(Locale.getDefault(), "%,.0f", total)
    }

    private fun simpanTransaksi() {
        val produkDibeli = listProduk.filter { produk ->
            produk.jumlah > 0
        }

        if (produkDibeli.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Belum ada produk yang dipilih",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val total = produkDibeli.sumOf { produk ->
            produk.harga * produk.jumlah
        }

        Toast.makeText(
            requireContext(),
            "Transaksi berhasil disimpan\nTotal: Rp " +
                    String.format(Locale.getDefault(), "%,.0f", total),
            Toast.LENGTH_LONG
        ).show()

        for (produk in listProduk) {
            produk.jumlah = 0
        }

        refreshFilter()
        updateTotal()
    }
}