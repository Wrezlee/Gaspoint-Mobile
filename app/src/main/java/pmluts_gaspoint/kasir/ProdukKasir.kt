package pmluts_gaspoint.kasir

data class ProdukKasir(
    val nama: String,
    val harga: Double,
    val stok: Int,
    val deskripsi: String,
    val kategori: String,
    val gambar: String,
    val id: Int = 0,
    var jumlah: Int = 0
)