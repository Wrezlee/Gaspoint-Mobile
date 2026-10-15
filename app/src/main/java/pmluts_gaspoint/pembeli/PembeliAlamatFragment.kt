package pmluts_gaspoint.pembeli

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import pmluts_gaspoint.common.LokasiHelper
import pmluts_gaspoint.common.PetaHelper
import pmluts_gaspoint.R
import pmluts_gaspoint.common.FirestoreRepository
import pmluts_gaspoint.databinding.FragmentPembeliAlamatBinding

class PembeliAlamatFragment : Fragment() {

    private var _binding: FragmentPembeliAlamatBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentPembeliAlamatBinding.inflate(
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

        loadAlamat()

        /* BUTTON BACK */
        binding.btnBack.setOnClickListener {

            requireActivity()
                .onBackPressedDispatcher
                .onBackPressed()
        }

        /* BUTTON TAMBAH ALAMAT */
        binding.fabAddAlamat.setOnClickListener {

            showAlamatDialog()
        }
    }

    private fun loadAlamat() {

        val sharedPref = requireActivity()
            .getSharedPreferences(
                "UserSession",
                Context.MODE_PRIVATE
            )

        val username = sharedPref.getString(
            "username",
            ""
        ) ?: ""

        val adapter = object : SimpleCursorAdapter(
            requireContext(),
            R.layout.item_alamat,
            null,
            arrayOf(
                "label",
                "detail"
            ),
            intArrayOf(
                R.id.tv_label_alamat,
                R.id.tv_detail_alamat
            ),
            0
        ) {

            override fun bindView(
                view: View,
                context: Context?,
                cursor: android.database.Cursor
            ) {

                val tvLabel =
                    view.findViewById<TextView>(
                        R.id.tv_label_alamat
                    )

                val tvDetail =
                    view.findViewById<TextView>(
                        R.id.tv_detail_alamat
                    )

                val btnDelete =
                    view.findViewById<ImageButton>(
                        R.id.btn_delete_alamat
                    )

                val id = cursor.getInt(
                    cursor.getColumnIndexOrThrow("id")
                )

                val label = cursor.getString(
                    cursor.getColumnIndexOrThrow("label")
                )

                val detail = cursor.getString(
                    cursor.getColumnIndexOrThrow("detail")
                )

                tvLabel.text = label
                tvDetail.text = detail

                btnDelete.setOnClickListener {

                    AlertDialog.Builder(requireContext())
                        .setTitle("Hapus Alamat")
                        .setMessage(
                            "Apakah Anda yakin ingin menghapus alamat '$label'?"
                        )

                        .setPositiveButton("Hapus") { _, _ ->

                            FirestoreRepository.deleteAlamat(id) { ok ->
                                if (isAdded) {
                                    if (ok) loadAlamat()

                                    Toast.makeText(
                                        requireContext(),
                                        if (ok) "Alamat berhasil dihapus" else "Gagal menghapus alamat",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }

                        .setNegativeButton(
                            "Batal",
                            null
                        )

                        .show()
                }
            }
        }

        binding.lvAlamat.adapter = adapter

        FirestoreRepository.getAlamatByUsername(username) { cursor ->
            if (_binding == null) {
                cursor.close()
            } else {
                adapter.changeCursor(cursor)
            }
        }
    }

    private fun showAlamatDialog() {

        // Konfigurasi osmdroid harus siap sebelum MapView di-inflate
        PetaHelper.inisialisasi(requireContext())

        val dialogView = layoutInflater.inflate(
            R.layout.dialog_alamat,
            null
        )

        val etLabel =
            dialogView.findViewById<EditText>(
                R.id.et_label_alamat
            )

        val etDetail =
            dialogView.findViewById<EditText>(
                R.id.et_detail_alamat
            )

        val btnGps =
            dialogView.findViewById<MaterialButton>(
                R.id.btn_gunakan_gps
            )

        val tvStatus =
            dialogView.findViewById<TextView>(
                R.id.tv_status_lokasi
            )

        val mapPilih =
            dialogView.findViewById<MapView>(
                R.id.map_pilih_lokasi
            )

        // Koordinat yang dipilih (null = belum dipilih, alamat tetap bisa disimpan)
        var pilihanLat: Double? = null
        var pilihanLng: Double? = null
        var markerPilihan: Marker? = null

        PetaHelper.siapkanPeta(
            mapPilih,
            PetaHelper.PUSAT_INDONESIA,
            5.0
        )

        // Memindahkan penanda ke titik baru dan memperbarui status koordinat
        fun pasangTitik(titik: GeoPoint) {
            pilihanLat = titik.latitude
            pilihanLng = titik.longitude

            markerPilihan?.let { mapPilih.overlays.remove(it) }
            markerPilihan = PetaHelper.tambahMarker(
                mapPilih,
                titik,
                "Lokasi alamat"
            )

            tvStatus.text = "📍 Titik dipilih: %.6f, %.6f".format(titik.latitude, titik.longitude)
        }

        // OSM: ketuk peta untuk memindahkan penanda
        PetaHelper.pasangKetukPeta(mapPilih) { titik ->
            pasangTitik(titik)
        }

        // GPS: ambil lokasi saat ini lalu pusatkan peta ke sana
        btnGps.setOnClickListener {

            btnGps.isEnabled = false
            tvStatus.text = "Mengambil lokasi..."

            LokasiHelper.ambilLokasiSaatIni(
                this,
                onIzinDitolak = {
                    btnGps.isEnabled = true
                    tvStatus.text = "Izin lokasi ditolak. Ketuk peta untuk memilih titik."
                }
            ) { lokasi ->

                btnGps.isEnabled = true

                if (lokasi == null) {
                    tvStatus.text = "Lokasi tidak terdeteksi. Pastikan GPS aktif, atau ketuk peta."
                } else {
                    val titik = GeoPoint(lokasi.latitude, lokasi.longitude)
                    mapPilih.controller.setZoom(17.0)
                    mapPilih.controller.setCenter(titik)
                    pasangTitik(titik)
                }
            }
        }

        val dialog = AlertDialog.Builder(requireContext())

            .setTitle("Tambah Alamat Baru")

            .setView(dialogView)

            .setPositiveButton("Simpan") { _, _ ->

                val label = etLabel.text
                    .toString()
                    .trim()

                val detail = etDetail.text
                    .toString()
                    .trim()

                val sharedPref =
                    requireActivity()
                        .getSharedPreferences(
                            "UserSession",
                            Context.MODE_PRIVATE
                        )

                val username =
                    sharedPref.getString(
                        "username",
                        ""
                    ) ?: ""

                if (
                    label.isNotEmpty() &&
                    detail.isNotEmpty()
                ) {

                    FirestoreRepository.addAlamat(
                        username,
                        label,
                        detail,
                        pilihanLat,
                        pilihanLng
                    ) { ok ->
                        if (isAdded) {
                            if (ok) loadAlamat()

                            Toast.makeText(
                                requireContext(),
                                if (ok) "Alamat berhasil disimpan" else "Gagal menyimpan alamat",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                } else {

                    Toast.makeText(
                        context,
                        "Harap isi semua field",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            .setNegativeButton(
                "Batal",
                null
            )

            .create()

        // Siklus hidup MapView mengikuti dialog
        dialog.setOnShowListener { mapPilih.onResume() }
        dialog.setOnDismissListener {
            mapPilih.onPause()
            mapPilih.onDetach()
        }

        dialog.show()
    }

    override fun onDestroyView() {

        super.onDestroyView()

        _binding = null
    }
}