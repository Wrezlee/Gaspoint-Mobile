package pmluts_gaspoint.kasir

import pmluts_gaspoint.common.SupabaseStorageHelper

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import pmluts_gaspoint.R
import pmluts_gaspoint.databinding.ItemProdukKasirBinding
import java.util.Locale

class ProdukKasirAdapter(
    private var listProduk: ArrayList<ProdukKasir>,
    private val onJumlahBerubah: () -> Unit
) : RecyclerView.Adapter<ProdukKasirAdapter.ProdukViewHolder>() {

    class ProdukViewHolder(val binding: ItemProdukKasirBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProdukViewHolder {
        val binding = ItemProdukKasirBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return ProdukViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProdukViewHolder, position: Int) {
        val produk = listProduk[position]

        SupabaseStorageHelper.loadImage(
            holder.binding.imgProduk,
            produk.gambar,
            getDefaultImage(produk.nama)
        )

        holder.binding.tvKategoriProduk.text =
            produk.kategori.uppercase(Locale.getDefault())

        holder.binding.tvNamaProduk.text = produk.nama

        holder.binding.tvHargaProduk.text =
            "Rp " + String.format(Locale.getDefault(), "%,.0f", produk.harga)

        holder.binding.tvStokProduk.text =
            if (produk.stok <= 0) {
                "Stok: Habis"
            } else {
                "Stok: ${produk.stok}"
            }

        holder.binding.tvDeskripsiProduk.text = produk.deskripsi

        if (produk.stok <= 0) {
            produk.jumlah = 0
        }

        holder.binding.tvJumlah.text = produk.jumlah.toString()

        if (produk.stok <= 0) {
            holder.binding.tvStatusHabis.visibility = View.VISIBLE
            holder.binding.btnPlus.isEnabled = false
            holder.binding.btnMinus.isEnabled = false
        } else {
            holder.binding.tvStatusHabis.visibility = View.GONE
            holder.binding.btnPlus.isEnabled = true
            holder.binding.btnMinus.isEnabled = true
        }

        holder.binding.btnPlus.setOnClickListener {
            if (produk.jumlah < produk.stok) {
                produk.jumlah++
                holder.binding.tvJumlah.text = produk.jumlah.toString()
                onJumlahBerubah()
            }
        }

        holder.binding.btnMinus.setOnClickListener {
            if (produk.jumlah > 0) {
                produk.jumlah--
                holder.binding.tvJumlah.text = produk.jumlah.toString()
                onJumlahBerubah()
            }
        }
    }

    override fun getItemCount(): Int {
        return listProduk.size
    }

    fun updateData(dataBaru: ArrayList<ProdukKasir>) {
        listProduk = dataBaru
        notifyDataSetChanged()
    }

    private fun getDefaultImage(nama: String): Int {
        val namaLower = nama.lowercase(Locale.getDefault())

        return when {
            namaLower.contains("lpg 3kg") || namaLower.contains("gas lpg 3kg") ->
                R.drawable.gas_lpg_3kg

            namaLower.contains("lpg 12kg") || namaLower.contains("gas lpg 12kg") ->
                R.drawable.gas_lpg_12kg

            namaLower.contains("aqua") ->
                R.drawable.galon_aqua

            namaLower.contains("cleo") ->
                R.drawable.galon_cleo

            namaLower.contains("club") ->
                R.drawable.galon_club

            namaLower.contains("le minerale") ->
                R.drawable.galon_leminerale

            else ->
                R.drawable.ic_launcher_background
        }
    }
}