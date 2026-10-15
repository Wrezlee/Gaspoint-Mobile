package pmluts_gaspoint.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import pmluts_gaspoint.R
import pmluts_gaspoint.common.FirestoreRepository
import pmluts_gaspoint.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.ivBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.tilEmail.editText?.text.toString().trim()
            val password = binding.tilPassword.editText?.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(context, "Silakan lengkapi email dan password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tilEmail.error = "Format email tidak valid"
                return@setOnClickListener
            }
            binding.tilEmail.error = null

            binding.btnLogin.isEnabled = false

            FirestoreRepository.login(email, password) { profile, error ->
                if (_binding == null) return@login
                binding.btnLogin.isEnabled = true

                if (profile == null) {
                    Toast.makeText(context, error ?: "Email atau Password salah", Toast.LENGTH_SHORT).show()
                    return@login
                }

                // Peran diambil dari data akun di Firestore, bukan dari pilihan pengguna
                val role = profile.role

                val intent = when (role) {
                    "Admin" -> Intent(requireContext(), pmluts_gaspoint.admin.AdminDashboardActivity::class.java)
                    "Kasir" -> Intent(requireContext(), pmluts_gaspoint.kasir.KasirDashboardActivity::class.java)
                    "Pembeli" -> Intent(requireContext(), pmluts_gaspoint.pembeli.PembeliDashboardActivity::class.java)
                    "Kurir" -> Intent(requireContext(), pmluts_gaspoint.kurir.KurirDashboardActivity::class.java)
                    else -> null
                }

                if (intent == null) {
                    FirestoreRepository.signOut()
                    Toast.makeText(context, "Peran akun tidak dikenali", Toast.LENGTH_SHORT).show()
                    return@login
                }

                requireActivity()
                    .getSharedPreferences("UserSession", Context.MODE_PRIVATE)
                    .edit()
                    .putString("username", profile.username)
                    .putString("full_name", profile.fullName)
                    .putString("email", profile.email)
                    .putString("role", role)
                    .apply()

                Toast.makeText(context, "Login Berhasil sebagai $role", Toast.LENGTH_SHORT).show()
                startActivity(intent)
                requireActivity().finish()
            }
        }

        binding.tvRegister.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
