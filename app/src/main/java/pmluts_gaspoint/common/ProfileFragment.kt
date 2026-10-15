package pmluts_gaspoint.common

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import pmluts_gaspoint.R
import pmluts_gaspoint.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(
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

        val sharedPref =
            requireActivity().getSharedPreferences(
                "UserSession",
                Context.MODE_PRIVATE
            )

        val username =
            sharedPref.getString(
                "username",
                ""
            ) ?: ""

        val fullName =
            sharedPref.getString(
                "full_name",
                "User Name"
            )

        val email =
            sharedPref.getString(
                "email",
                "user@example.com"
            )

        val role =
            sharedPref.getString(
                "role",
                "Pembeli"
            ) ?: "Pembeli"

        binding.tvProfileName.text = fullName
        binding.tvProfileEmail.text = email

        when (role) {
            "Pembeli" -> setupPembeliProfile()
            "Kurir" -> setupKurirProfile(username)
            "Admin" -> setupAdminProfile()
            "Kasir" -> setupKasirProfile()
            else -> setupRoleUmum(role)
        }

        binding.btnNotifikasi.setOnClickListener {
            startActivity(Intent(requireContext(), MessagingActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            FirestoreRepository.signOut()

            sharedPref.edit()
                .clear()
                .apply()

            val intent = Intent(
                requireContext(),
                pmluts_gaspoint.auth.MainActivity::class.java
            )

            intent.flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK

            startActivity(intent)
            requireActivity().finish()
        }
    }

    private fun setupPembeliProfile() {
        binding.btnManageAddress.text = "Alamat Pengiriman"
        binding.btnEditProfile.text = "Ubah Profil"

        binding.btnManageAddress.setOnClickListener {
            findNavController().navigate(
                R.id.navigation_pembeli_alamat
            )
        }

        binding.btnEditProfile.setOnClickListener {
            findNavController().navigate(
                R.id.navigation_pembeli_edit_profile
            )
        }
    }

    private fun setupKurirProfile(username: String) {
        binding.btnManageAddress.text = "Status Kerja Kurir"
        binding.btnEditProfile.text = "Panduan Kurir"

        binding.btnManageAddress.setOnClickListener {
            FirestoreRepository.isKurirAktif(username) { aktif ->
                if (!isAdded) return@isKurirAktif

                val statusText =
                    if (aktif) {
                        "✅ Aktif menerima tugas"
                    } else {
                        "⛔ Nonaktif"
                    }

                AlertDialog.Builder(requireContext())
                    .setTitle("Status Kerja Kurir")
                    .setMessage(
                        """
                        $statusText
                        
                        Status ini menentukan apakah admin bisa menugaskan pesanan kepadamu.
                        
                        Ketentuan:
                        • Aktif: namamu muncul di daftar kurir admin
                        • Nonaktif: admin tidak bisa menugaskan pesanan
                        • Status diubah dari halaman Beranda Kurir
                        """.trimIndent()
                    )
                    .setPositiveButton("Mengerti", null)
                    .show()
            }
        }

        binding.btnEditProfile.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Panduan Kurir")
                .setMessage(
                    """
                    🚚 Alur kerja kurir
                    
                    1. Aktifkan status kerja di Beranda
                    2. Tunggu admin menugaskan pesanan
                    3. Buka detail pesanan
                    4. Tekan “Tandai Dikirim”
                    5. Upload bukti pengantaran
                    6. Tunggu admin menandai selesai
                    
                    Pastikan bukti pengantaran jelas sebelum diupload.
                    """.trimIndent()
                )
                .setPositiveButton("Paham", null)
                .show()
        }
    }

    private fun setupAdminProfile() {
        binding.btnManageAddress.text = "Panduan Admin"
        binding.btnEditProfile.text = "Manajemen Pesanan"

        binding.btnManageAddress.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Panduan Admin")
                .setMessage(
                    """
                    🛠️ Role Admin
                    
                    Tugas utama admin:
                    
                    • Memvalidasi pembayaran QRIS
                    • Menugaskan pesanan ke kurir aktif
                    • Melihat bukti pembayaran
                    • Melihat bukti pengantaran
                    • Menandai pesanan selesai
                    • Mengelola data produk dan stok
                    """.trimIndent()
                )
                .setPositiveButton("Mengerti", null)
                .show()
        }

        binding.btnEditProfile.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Manajemen Pesanan")
                .setMessage(
                    """
                    📦 Alur pesanan admin
                    
                    1. Cek pesanan masuk
                    2. Validasi bukti pembayaran jika QRIS
                    3. Tugaskan pesanan ke kurir aktif
                    4. Tunggu kurir upload bukti pengantaran
                    5. Tandai pesanan selesai
                    
                    Admin hanya bisa menugaskan pesanan ke kurir yang sedang aktif.
                    """.trimIndent()
                )
                .setPositiveButton("Paham", null)
                .show()
        }
    }

    private fun setupKasirProfile() {
        binding.btnManageAddress.text = "Panduan Kasir"
        binding.btnEditProfile.text = "Transaksi Offline"

        binding.btnManageAddress.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Panduan Kasir")
                .setMessage(
                    """
                    🧾 Role Kasir
                    
                    Tugas utama kasir:
                    
                    • Melayani pembelian langsung di toko
                    • Memilih produk gas atau galon
                    • Memproses pembayaran CASH / QRIS
                    • Mengupload bukti pembayaran QRIS
                    • Menyimpan transaksi offline
                    """.trimIndent()
                )
                .setPositiveButton("Mengerti", null)
                .show()
        }

        binding.btnEditProfile.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Transaksi Offline")
                .setMessage(
                    """
                    🛒 Alur transaksi kasir
                    
                    1. Pilih produk yang dibeli customer
                    2. Tentukan jumlah produk
                    3. Pilih metode pembayaran
                    4. Jika QRIS, upload bukti pembayaran
                    5. Simpan transaksi
                    
                    Beberapa produk dalam satu pembelian akan tercatat sebagai satu transaksi.
                    """.trimIndent()
                )
                .setPositiveButton("Paham", null)
                .show()
        }
    }

    private fun setupRoleUmum(role: String) {
        binding.btnManageAddress.text = "Informasi Role"
        binding.btnEditProfile.text = "Panduan Penggunaan"

        binding.btnManageAddress.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Informasi Role")
                .setMessage(
                    "Kamu sedang login sebagai $role."
                )
                .setPositiveButton("OK", null)
                .show()
        }

        binding.btnEditProfile.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Panduan Penggunaan")
                .setMessage(
                    "Gunakan menu di bagian bawah untuk mengakses fitur sesuai role kamu."
                )
                .setPositiveButton("OK", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}