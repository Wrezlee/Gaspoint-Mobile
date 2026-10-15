package pmluts_gaspoint.admin

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import pmluts_gaspoint.common.FirestoreRepository
import pmluts_gaspoint.databinding.FragmentAdminProfileBinding
import java.util.Locale

class AdminProfileFragment : Fragment() {

    private var _binding: FragmentAdminProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminProfileBinding.inflate(
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

        loadUserCounts()

        binding.btnDataAdmin.setOnClickListener {
            tampilkanUserByRole("Admin")
        }

        binding.btnDataKasir.setOnClickListener {
            tampilkanUserByRole("Kasir")
        }

        binding.btnDataKurir.setOnClickListener {
            tampilkanUserByRole("Kurir")
        }

        binding.btnDataPembeli.setOnClickListener {
            tampilkanUserByRole("Pembeli")
        }

        binding.btnLogout.setOnClickListener {
            FirestoreRepository.signOut()

            val sharedPref =
                requireActivity().getSharedPreferences(
                    "UserSession",
                    Context.MODE_PRIVATE
                )

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

    private fun loadUserCounts() {
        FirestoreRepository.getUserCountsByRole { counts ->
            if (_binding == null) return@getUserCountsByRole

            val totalAdmin = counts["Admin"] ?: 0
            val totalKasir = counts["Kasir"] ?: 0
            val totalKurir = counts["Kurir"] ?: 0
            val totalPembeli = counts["Pembeli"] ?: 0

            val totalUser = totalAdmin + totalKasir + totalKurir + totalPembeli

            binding.tvTotalUserAdmin.text = "Total user: $totalUser"

            binding.tvCountAdmin.text = totalAdmin.toString()
            binding.tvCountKasir.text = totalKasir.toString()
            binding.tvCountKurir.text = totalKurir.toString()
            binding.tvCountPembeli.text = totalPembeli.toString()
        }
    }

    private fun tampilkanUserByRole(role: String) {
        binding.layoutResultContainer.visibility = View.VISIBLE
        binding.tvDaftarTitle.text = "Daftar $role"

        binding.layoutUserList.removeAllViews()

        FirestoreRepository.getUsersByRole(role) { cursor ->
            if (_binding == null) {
                cursor.close()
                return@getUsersByRole
            }

            binding.layoutUserList.removeAllViews()

            if (cursor.moveToFirst()) {
                do {
                    val fullName = cursor.getString(cursor.getColumnIndexOrThrow("full_name"))
                    val username = cursor.getString(cursor.getColumnIndexOrThrow("username"))
                    val email = cursor.getString(cursor.getColumnIndexOrThrow("email"))

                    addUserCard(
                        name = fullName,
                        username = username,
                        email = email,
                        role = role
                    )
                } while (cursor.moveToNext())
            } else {
                addEmptyCard(role)
            }

            cursor.close()
        }
    }

    private fun addUserCard(
        name: String,
        username: String,
        email: String,
        role: String
    ) {
        val card = MaterialCardView(requireContext())

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        params.setMargins(
            0,
            0,
            0,
            dp(12)
        )

        card.layoutParams = params
        card.radius = dp(22).toFloat()
        card.cardElevation = dp(2).toFloat()
        card.setCardBackgroundColor(Color.WHITE)
        card.strokeColor = Color.parseColor("#E6DDF0")
        card.strokeWidth = dp(1)

        val row = LinearLayout(requireContext())
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(
            dp(16),
            dp(14),
            dp(16),
            dp(14)
        )
        row.gravity = android.view.Gravity.CENTER_VERTICAL

        val avatar = TextView(requireContext())
        avatar.layoutParams = LinearLayout.LayoutParams(
            dp(44),
            dp(44)
        )
        avatar.gravity = android.view.Gravity.CENTER
        avatar.text = role.first().toString().uppercase(Locale.getDefault())
        avatar.setTextColor(Color.WHITE)
        avatar.textSize = 17f
        avatar.setTypeface(null, Typeface.BOLD)

        avatar.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#00BFA6"))
        }

        val textContainer = LinearLayout(requireContext())
        textContainer.layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        textContainer.orientation = LinearLayout.VERTICAL
        textContainer.setPadding(
            dp(14),
            0,
            0,
            0
        )

        val tvName = TextView(requireContext())
        tvName.text = name
        tvName.setTextColor(Color.parseColor("#222222"))
        tvName.textSize = 15f
        tvName.setTypeface(null, Typeface.BOLD)

        val tvUsername = TextView(requireContext())
        tvUsername.text = "@$username"
        tvUsername.setTextColor(Color.parseColor("#777777"))
        tvUsername.textSize = 13f
        tvUsername.setPadding(
            0,
            dp(3),
            0,
            0
        )

        val tvEmail = TextView(requireContext())
        tvEmail.text = email
        tvEmail.setTextColor(Color.parseColor("#00BFA6"))
        tvEmail.textSize = 12f
        tvEmail.setPadding(
            0,
            dp(2),
            0,
            0
        )

        textContainer.addView(tvName)
        textContainer.addView(tvUsername)
        textContainer.addView(tvEmail)

        row.addView(avatar)
        row.addView(textContainer)

        card.addView(row)

        binding.layoutUserList.addView(card)
    }

    private fun addEmptyCard(role: String) {
        val card = MaterialCardView(requireContext())

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        params.setMargins(
            0,
            0,
            0,
            dp(12)
        )

        card.layoutParams = params
        card.radius = dp(22).toFloat()
        card.cardElevation = dp(2).toFloat()
        card.setCardBackgroundColor(Color.WHITE)
        card.strokeColor = Color.parseColor("#E6DDF0")
        card.strokeWidth = dp(1)

        val textView = TextView(requireContext())
        textView.text = "Belum ada user dengan role $role."
        textView.setTextColor(Color.parseColor("#777777"))
        textView.textSize = 14f
        textView.setPadding(
            dp(18),
            dp(18),
            dp(18),
            dp(18)
        )

        card.addView(textView)

        binding.layoutUserList.addView(card)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}