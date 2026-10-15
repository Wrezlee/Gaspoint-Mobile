package pmluts_gaspoint.admin

import pmluts_gaspoint.common.SupabaseStorageHelper

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import pmluts_gaspoint.R
import pmluts_gaspoint.common.FirestoreRepository
import com.google.firebase.firestore.ListenerRegistration
import pmluts_gaspoint.databinding.FragmentAdminStockBinding
import java.util.Locale

class AdminStockFragment : Fragment() {

    private var _binding: FragmentAdminStockBinding? = null
    private val binding get() = _binding!!

    private var listener: ListenerRegistration? = null

    // disimpan agar ContextMenu bisa membaca data produk pada posisi yang ditekan lama
    private var produkAdapter: SimpleCursorAdapter? = null

    private var selectedImageUri: Uri? = null
    private var previewImageView: ImageView? = null

    private val pilihGambarLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                selectedImageUri = uri
                previewImageView?.setImageURI(uri)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminStockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadProdukData()

        // ContextMenu: tekan lama pada baris produk
        registerForContextMenu(binding.lvProduk)

        binding.fabAddProduct.setOnClickListener {
            showProdukDialog(null)
        }
    }

    private fun loadProdukData() {
        val adapter = object : SimpleCursorAdapter(
            requireContext(),
            R.layout.item_produk,
            null,
            arrayOf("nama", "harga", "stok"),
            intArrayOf(
                R.id.tv_nama_produk,
                R.id.tv_harga_produk,
                R.id.tv_stok_produk
            ),
            0
        ) {
            override fun bindView(
                view: View,
                context: android.content.Context?,
                cursor: android.database.Cursor
            ) {
                val tvNama = view.findViewById<TextView>(R.id.tv_nama_produk)
                val tvHarga = view.findViewById<TextView>(R.id.tv_harga_produk)
                val tvStok = view.findViewById<TextView>(R.id.tv_stok_produk)
                val btnEdit = view.findViewById<ImageButton>(R.id.btn_edit)
                val btnDelete = view.findViewById<ImageButton>(R.id.btn_delete)

                val id = cursor.getInt(cursor.getColumnIndexOrThrow("_id"))
                val nama = cursor.getString(cursor.getColumnIndexOrThrow("nama"))
                val harga = cursor.getDouble(cursor.getColumnIndexOrThrow("harga"))
                val stok = cursor.getInt(cursor.getColumnIndexOrThrow("stok"))
                val deskripsi = cursor.getString(cursor.getColumnIndexOrThrow("deskripsi"))
                val kategori = cursor.getString(cursor.getColumnIndexOrThrow("kategori"))
                val gambar = cursor.getString(cursor.getColumnIndexOrThrow("gambar")) ?: ""

                tvNama.text = nama
                tvHarga.text = "Rp " + String.format(Locale.getDefault(), "%,.0f", harga)
                tvStok.text = if (stok <= 0) {
                    "Stok: HABIS | $kategori"
                } else {
                    "Stok: $stok | $kategori"
                }

                btnEdit.setOnClickListener {
                    showProdukDialog(
                        Produk(
                            id = id,
                            nama = nama,
                            harga = harga,
                            stok = stok,
                            deskripsi = deskripsi,
                            kategori = kategori,
                            gambar = gambar
                        )
                    )
                }

                btnDelete.setOnClickListener {
                    konfirmasiHapusProduk(id, nama)
                }
            }
        }

        produkAdapter = adapter
        binding.lvProduk.adapter = adapter

        listener?.remove()
        listener = FirestoreRepository.observeProduk { cursor ->
            if (_binding == null) {
                cursor.close()
            } else {
                adapter.changeCursor(cursor)
            }
        }
    }

    // ------------------------------------------------------------------
    // ContextMenu (tekan lama pada produk)
    // ------------------------------------------------------------------

    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)

        if (v.id == R.id.lv_produk) {
            val info = menuInfo as? AdapterView.AdapterContextMenuInfo ?: return
            val produk = produkDiPosisi(info.position) ?: return

            menu.setHeaderTitle(produk.nama)
            requireActivity().menuInflater.inflate(R.menu.admin_produk_context_menu, menu)
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val info = item.menuInfo as? AdapterView.AdapterContextMenuInfo ?: return false
        val produk = produkDiPosisi(info.position) ?: return false

        return when (item.itemId) {
            R.id.ctx_edit_produk -> {
                showProdukDialog(produk)
                true
            }

            R.id.ctx_tambah_stok -> {
                showTambahStokDialog(produk)
                true
            }

            R.id.ctx_hapus_produk -> {
                konfirmasiHapusProduk(produk.id, produk.nama)
                true
            }

            else -> super.onContextItemSelected(item)
        }
    }

    /** Membaca produk dari cursor adapter pada posisi baris yang ditekan lama. */
    private fun produkDiPosisi(position: Int): Produk? {
        val cursor = produkAdapter?.getItem(position) as? android.database.Cursor ?: return null

        return Produk(
            id = cursor.getInt(cursor.getColumnIndexOrThrow("_id")),
            nama = cursor.getString(cursor.getColumnIndexOrThrow("nama")) ?: "",
            harga = cursor.getDouble(cursor.getColumnIndexOrThrow("harga")),
            stok = cursor.getInt(cursor.getColumnIndexOrThrow("stok")),
            deskripsi = cursor.getString(cursor.getColumnIndexOrThrow("deskripsi")) ?: "",
            kategori = cursor.getString(cursor.getColumnIndexOrThrow("kategori")) ?: "",
            gambar = cursor.getString(cursor.getColumnIndexOrThrow("gambar")) ?: ""
        )
    }

    private fun showTambahStokDialog(produk: Produk) {
        val etJumlah = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Jumlah stok yang ditambahkan"
        }

        val padding = (20 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(requireContext()).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(etJumlah)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Tambah Stok: ${produk.nama}")
            .setMessage("Stok saat ini: ${produk.stok}")
            .setView(container)
            .setPositiveButton("Tambah") { _, _ ->
                val jumlah = etJumlah.text.toString().trim().toIntOrNull()

                if (jumlah == null || jumlah <= 0) {
                    Toast.makeText(
                        requireContext(),
                        "Masukkan jumlah stok yang valid",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    FirestoreRepository.tambahStokProduk(produk.id, jumlah) { ok ->
                        if (isAdded) {
                            Toast.makeText(
                                requireContext(),
                                if (ok) "Stok ${produk.nama} bertambah $jumlah" else "Gagal menambah stok",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    /** Dipakai bersama oleh tombol hapus di baris dan menu konteks "Hapus produk". */
    private fun konfirmasiHapusProduk(id: Int, nama: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus Produk")
            .setMessage("Apakah Anda yakin ingin menghapus $nama?")
            .setPositiveButton("Ya") { _, _ ->
                FirestoreRepository.deleteProduk(id) { ok ->
                    if (isAdded) {
                        Toast.makeText(
                            requireContext(),
                            if (ok) "Produk dihapus" else "Gagal menghapus produk",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("Tidak", null)
            .show()
    }

    private fun showProdukDialog(produk: Produk?) {
        val isEdit = produk != null
        val dialogView = layoutInflater.inflate(R.layout.dialog_produk, null)

        val imgPreview = dialogView.findViewById<ImageView>(R.id.img_preview_produk)
        val btnPilihGambar = dialogView.findViewById<Button>(R.id.btn_pilih_gambar)
        val etNama = dialogView.findViewById<EditText>(R.id.et_nama)
        val spinnerKategori = dialogView.findViewById<Spinner>(R.id.spinner_kategori_produk)
        val etHarga = dialogView.findViewById<EditText>(R.id.et_harga)
        val etStok = dialogView.findViewById<EditText>(R.id.et_stok)
        val etDeskripsi = dialogView.findViewById<EditText>(R.id.et_deskripsi)

        previewImageView = imgPreview
        selectedImageUri = null

        val kategoriList = arrayOf("Galon", "Gas")

        val kategoriAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            kategoriList
        )
        kategoriAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerKategori.adapter = kategoriAdapter

        if (isEdit && produk != null) {
            etNama.setText(produk.nama)
            etHarga.setText(produk.harga.toInt().toString())
            etStok.setText(produk.stok.toString())
            etDeskripsi.setText(produk.deskripsi)

            val posisiKategori = kategoriList.indexOf(produk.kategori)
            if (posisiKategori >= 0) {
                spinnerKategori.setSelection(posisiKategori)
            }

            // gambar lama (URL Supabase / URI lama) hanya sebagai preview,
            // selectedImageUri baru terisi jika admin memilih gambar baru
            SupabaseStorageHelper.loadImage(
                imgPreview,
                produk.gambar,
                getDefaultImage(produk.nama)
            )
        } else {
            imgPreview.setImageResource(R.drawable.ic_launcher_background)
        }

        btnPilihGambar.setOnClickListener {
            pilihGambarLauncher.launch(arrayOf("image/*"))
        }

        AlertDialog.Builder(requireContext())
            .setTitle(if (isEdit) "Edit Produk" else "Tambah Produk Baru")
            .setView(dialogView)
            .setPositiveButton(if (isEdit) "Simpan" else "Tambah") { _, _ ->

                val nama = etNama.text.toString().trim()
                val hargaStr = etHarga.text.toString().trim()
                val stokStr = etStok.text.toString().trim()
                val deskripsi = etDeskripsi.text.toString().trim()
                val kategori = spinnerKategori.selectedItem.toString()
                val gambarBaru = selectedImageUri
                val gambarLama = produk?.gambar ?: ""

                if (nama.isNotEmpty() && hargaStr.isNotEmpty() && stokStr.isNotEmpty()) {
                    val harga = hargaStr.toDouble()
                    val stok = stokStr.toInt()

                    // simpan data produk ke Firestore (gambar = URL Supabase Storage)
                    val simpan: (String) -> Unit = { gambar ->
                        if (isEdit && produk != null) {
                            FirestoreRepository.updateProduk(
                                produk.id,
                                nama,
                                harga,
                                stok,
                                deskripsi,
                                kategori,
                                gambar
                            ) { ok ->
                                if (isAdded) {
                                    Toast.makeText(
                                        requireContext(),
                                        if (ok) "Produk diperbarui" else "Gagal memperbarui produk",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        } else {
                            FirestoreRepository.addProduk(
                                nama,
                                harga,
                                stok,
                                deskripsi,
                                kategori,
                                gambar
                            ) { ok ->
                                if (isAdded) {
                                    Toast.makeText(
                                        requireContext(),
                                        if (ok) "Produk ditambahkan" else "Gagal menambahkan produk",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }

                    if (gambarBaru != null) {
                        // gambar baru: unggah ke Supabase Storage dulu, lalu simpan URL-nya
                        Toast.makeText(
                            requireContext(),
                            "Mengunggah gambar...",
                            Toast.LENGTH_SHORT
                        ).show()

                        SupabaseStorageHelper.uploadImage(
                            requireContext(),
                            gambarBaru,
                            "produk"
                        ) { url ->
                            if (url != null) {
                                simpan(url)
                            } else if (isAdded) {
                                Toast.makeText(
                                    requireContext(),
                                    "Gagal mengunggah gambar produk",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } else {
                        simpan(gambarLama)
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Harap isi nama, harga, dan stok",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
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

    data class Produk(
        val id: Int,
        val nama: String,
        val harga: Double,
        val stok: Int,
        val deskripsi: String,
        val kategori: String,
        val gambar: String
    )

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
        listener = null
        produkAdapter = null
        _binding = null
    }
}