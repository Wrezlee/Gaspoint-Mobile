package pmluts_gaspoint.pembeli

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import pmluts_gaspoint.R
import pmluts_gaspoint.common.FirestoreRepository
import pmluts_gaspoint.databinding.FragmentPembeliEditProfileBinding

class PembeliEditProfileFragment : Fragment(R.layout.fragment_pembeli_edit_profile) {

    private lateinit var binding: FragmentPembeliEditProfileBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentPembeliEditProfileBinding.bind(view)

        val sharedPref = requireActivity().getSharedPreferences(
            "UserSession",
            Context.MODE_PRIVATE
        )

        val fullName = sharedPref.getString("full_name", "") ?: ""
        val email = sharedPref.getString("email", "") ?: ""

        binding.etFullName.setText(fullName)
        binding.etEmail.setText(email)
        // Email adalah identitas login Firebase Authentication, jadi tidak bisa diubah di sini
        binding.etEmail.isEnabled = false

        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnSimpan.setOnClickListener {
            val namaBaru = binding.etFullName.text.toString().trim()
            val passwordBaru = binding.etPassword.text.toString().trim()

            if (namaBaru.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Nama tidak boleh kosong",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            binding.btnSimpan.isEnabled = false

            FirestoreRepository.updateProfilPembeli(
                namaBaru,
                passwordBaru
            ) { ok, error ->
                if (!isAdded) return@updateProfilPembeli

                binding.btnSimpan.isEnabled = true

                if (ok) {
                    sharedPref.edit()
                        .putString("full_name", namaBaru)
                        .apply()

                    Toast.makeText(
                        requireContext(),
                        "Profil berhasil diperbarui",
                        Toast.LENGTH_SHORT
                    ).show()

                    requireActivity().onBackPressedDispatcher.onBackPressed()
                } else {
                    Toast.makeText(
                        requireContext(),
                        error ?: "Gagal memperbarui profil",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}