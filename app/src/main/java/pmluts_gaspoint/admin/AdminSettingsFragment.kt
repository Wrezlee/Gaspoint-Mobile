package pmluts_gaspoint.admin
import pmluts_gaspoint.common.FirestoreRepository
import pmluts_gaspoint.R

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import pmluts_gaspoint.databinding.FragmentAdminSettingsBinding

class AdminSettingsFragment : Fragment() {
    private var _binding: FragmentAdminSettingsBinding? = null
    private val binding get() = _binding!!

    /** Data satu akun pada daftar; disimpan di tag baris agar ContextMenu tahu akun mana yang ditekan lama. */
    private data class UserItem(
        val uid: String,
        val fullName: String,
        val username: String,
        val email: String,
        val role: String,
        val kurirAktif: Boolean
    )

    private val daftarRole = listOf("Admin", "Kasir", "Kurir", "Pembeli")

    // Role yang sedang ditampilkan (untuk memuat ulang daftar setelah edit/hapus)
    private var currentRole: String? = null

    // Akun yang dipilih lewat tekan lama
    private var selectedUser: UserItem? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup Klik Tombol Manajemen
        binding.btnDataAdmin.setOnClickListener { toggleUserList("Admin") }
        binding.btnDataKasir.setOnClickListener { toggleUserList("Kasir") }
        binding.btnDataKurir.setOnClickListener { toggleUserList("Kurir") }
        binding.btnDataPembeli.setOnClickListener { toggleUserList("Pembeli") }

        binding.btnNotifikasi.setOnClickListener {
            startActivity(Intent(requireContext(), pmluts_gaspoint.common.MessagingActivity::class.java))
        }

        // Setup Logout
        // Logika logout kini satu tempat di AdminDashboardActivity (juga dipakai OptionsMenu "Keluar")
        binding.btnLogout.setOnClickListener {
            (requireActivity() as AdminDashboardActivity).logout()
        }
    }

    private fun toggleUserList(role: String) {
        // Jika list sedang tampil dengan role yang sama, maka tutup (hide)
        if (binding.layoutUserList.visibility == View.VISIBLE && binding.tvListTitle.text.contains(role)) {
            binding.layoutUserList.visibility = View.GONE
            currentRole = null
        } else {
            // Tampilkan list dan muat data
            binding.layoutUserList.visibility = View.VISIBLE
            binding.tvListTitle.text = "Daftar Terdaftar ($role):"
            currentRole = role
            loadUserListByRole(role)
        }
    }

    private fun loadUserListByRole(role: String) {
        binding.containerUsers.removeAllViews() // Bersihkan list sebelumnya

        FirestoreRepository.getUsersByRole(role) { cursor ->
            if (_binding == null) {
                cursor.close()
                return@getUsersByRole
            }

            binding.containerUsers.removeAllViews()

            if (cursor.moveToFirst()) {
                do {
                    val user = UserItem(
                        uid = cursor.getString(cursor.getColumnIndexOrThrow("uid")) ?: "",
                        fullName = cursor.getString(cursor.getColumnIndexOrThrow("full_name")) ?: "",
                        username = cursor.getString(cursor.getColumnIndexOrThrow("username")) ?: "",
                        email = cursor.getString(cursor.getColumnIndexOrThrow("email")) ?: "",
                        role = role,
                        kurirAktif = cursor.getInt(cursor.getColumnIndexOrThrow("kurir_aktif")) == 1
                    )

                    val itemView = LayoutInflater.from(requireContext())
                        .inflate(android.R.layout.simple_list_item_2, binding.containerUsers, false)
                    itemView.findViewById<TextView>(android.R.id.text1).text = user.fullName
                    itemView.findViewById<TextView>(android.R.id.text2).text = "@${user.username}"

                    // Efek sentuhan + padding agar baris nyaman ditekan lama
                    val outValue = TypedValue()
                    requireContext().theme.resolveAttribute(
                        android.R.attr.selectableItemBackground, outValue, true
                    )
                    itemView.setBackgroundResource(outValue.resourceId)
                    itemView.setPadding(dp(8), dp(10), dp(8), dp(10))

                    // ContextMenu: tekan lama pada akun (menggantikan klik biasa)
                    itemView.tag = user
                    registerForContextMenu(itemView)

                    binding.containerUsers.addView(itemView)
                } while (cursor.moveToNext())
            } else {
                val noData = TextView(requireContext()).apply {
                    text = "Tidak ada data user dengan peran ini."
                    setPadding(16, 16, 16, 16)
                }
                binding.containerUsers.addView(noData)
            }

            cursor.close()
        }
    }

    // ------------------------------------------------------------------
    // ContextMenu (tekan lama pada akun)
    // ------------------------------------------------------------------

    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)

        val user = v.tag as? UserItem ?: return
        selectedUser = user

        menu.setHeaderTitle(user.fullName)
        requireActivity().menuInflater.inflate(R.menu.admin_user_context_menu, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val user = selectedUser ?: return false

        return when (item.itemId) {
            R.id.ctx_detail_user -> {
                showUserDetailDialog(user)
                true
            }

            R.id.ctx_edit_user -> {
                showEditUserDialog(user)
                true
            }

            R.id.ctx_hapus_user -> {
                konfirmasiHapusUser(user)
                true
            }

            else -> super.onContextItemSelected(item)
        }
    }

    private fun isAkunSendiri(user: UserItem): Boolean =
        user.uid.isNotEmpty() && user.uid == FirestoreRepository.currentUid()

    // ------------------------------------------------------------------
    // Detail
    // ------------------------------------------------------------------

    private fun showUserDetailDialog(user: UserItem) {
        val status = if (user.role == "Kurir") {
            "\nStatus Kerja: ${if (user.kurirAktif) "Aktif" else "Tidak aktif"}"
        } else ""

        val message = """
            Nama Lengkap: ${user.fullName}
            Username: ${user.username}
            Email: ${user.email}
            Peran: ${user.role}$status
        """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle("Informasi Detail User")
            .setMessage(message)
            .setPositiveButton("Tutup", null)
            .show()
    }

    // ------------------------------------------------------------------
    // Edit
    // ------------------------------------------------------------------

    private fun showEditUserDialog(user: UserItem) {
        val ctx = requireContext()
        val akunSendiri = isAkunSendiri(user)

        val form = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), 0)
        }

        val tvInfo = TextView(ctx).apply {
            text = "Username: @${user.username}\nEmail: ${user.email}"
            setTextColor(Color.parseColor("#777777"))
            textSize = 13f
        }

        val etNama = EditText(ctx).apply {
            hint = "Nama lengkap"
            setText(user.fullName)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine()
        }

        val tvPeran = TextView(ctx).apply {
            text = if (akunSendiri) "Peran (akun Anda sendiri, tidak bisa diubah)" else "Peran"
            setPadding(0, dp(12), 0, 0)
            textSize = 13f
        }

        val spPeran = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, daftarRole)
            setSelection(daftarRole.indexOf(user.role).coerceAtLeast(0))
            isEnabled = !akunSendiri
        }

        form.addView(tvInfo)
        form.addView(etNama)
        form.addView(tvPeran)
        form.addView(spPeran)

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("Edit User")
            .setView(form)
            .setPositiveButton("Simpan", null) // di-override agar dialog tidak tertutup saat input salah
            .setNegativeButton("Batal", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val namaBaru = etNama.text.toString().trim()
                val roleBaru = spPeran.selectedItem as String

                if (namaBaru.isEmpty()) {
                    etNama.error = "Nama tidak boleh kosong"
                    return@setOnClickListener
                }

                if (namaBaru == user.fullName && roleBaru == user.role) {
                    dialog.dismiss()
                    return@setOnClickListener
                }

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false

                FirestoreRepository.updateUserByAdmin(user.uid, namaBaru, roleBaru) { ok ->
                    if (_binding == null) return@updateUserByAdmin

                    if (ok) {
                        Toast.makeText(requireContext(), "Data user diperbarui", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        currentRole?.let { loadUserListByRole(it) }
                    } else {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        Toast.makeText(requireContext(), "Gagal memperbarui user", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialog.show()
    }

    // ------------------------------------------------------------------
    // Hapus
    // ------------------------------------------------------------------

    private fun konfirmasiHapusUser(user: UserItem) {
        if (isAkunSendiri(user)) {
            Toast.makeText(requireContext(), "Akun yang sedang Anda pakai tidak bisa dihapus", Toast.LENGTH_LONG).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Hapus User")
            .setMessage(
                "Hapus akun ${user.fullName} (@${user.username})?\n\n" +
                        "Data profil akan dihapus dan user tidak bisa masuk lagi. " +
                        "Tindakan ini tidak dapat dibatalkan."
            )
            .setPositiveButton("Hapus") { _, _ ->
                FirestoreRepository.deleteUserByAdmin(user.uid, user.username) { ok ->
                    if (_binding == null) return@deleteUserByAdmin

                    Toast.makeText(
                        requireContext(),
                        if (ok) "User dihapus" else "Gagal menghapus user",
                        Toast.LENGTH_SHORT
                    ).show()

                    if (ok) currentRole?.let { loadUserListByRole(it) }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
