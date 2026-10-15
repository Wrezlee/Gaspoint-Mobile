package pmluts_gaspoint.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import pmluts_gaspoint.common.FirestoreRepository
import pmluts_gaspoint.databinding.FragmentRegisterBinding

class RegisterFragment : Fragment() {
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Peran yang bisa dipilih saat daftar: Admin, Kasir, Kurir, Pembeli
        val roles = FirestoreRepository.REGISTER_ROLES
        binding.actRole.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, roles)
        )
        binding.actRole.setText("Pembeli", false)

        binding.ivBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.tvLogin.setOnClickListener {
            findNavController().navigateUp()
        }

        // Tombol daftar hanya aktif jika syarat & ketentuan dicentang
        binding.btnRegister.isEnabled = binding.cbTerms.isChecked
        binding.cbTerms.setOnCheckedChangeListener { _, isChecked ->
            binding.btnRegister.isEnabled = isChecked
        }

        binding.btnRegister.setOnClickListener {
            val username = binding.tilUsername.editText?.text.toString().trim()
            val fullName = binding.tilName.editText?.text.toString().trim()
            val email = binding.tilEmail.editText?.text.toString().trim()
            val password = binding.tilPassword.editText?.text.toString().trim()
            val role = binding.actRole.text.toString().trim()

            if (username.isEmpty() || password.isEmpty() || role.isEmpty() || fullName.isEmpty() || email.isEmpty()) {
                Toast.makeText(context, "Mohon lengkapi semua data", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (role !in roles) {
                Toast.makeText(context, "Pilih peran: Admin, Kasir, Kurir, atau Pembeli", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnRegister.isEnabled = false

            FirestoreRepository.registerUser(username, password, role, fullName, email) { ok, error ->
                if (_binding == null) return@registerUser
                binding.btnRegister.isEnabled = binding.cbTerms.isChecked

                if (ok) {
                    Toast.makeText(context, "Pendaftaran Berhasil! Silakan Login.", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                } else {
                    Toast.makeText(context, error ?: "Pendaftaran Gagal.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
