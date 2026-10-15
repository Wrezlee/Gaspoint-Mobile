package pmluts_gaspoint.pembeli

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import pmluts_gaspoint.R
import pmluts_gaspoint.common.InAppMessagingHelper
import pmluts_gaspoint.databinding.FragmentPembeliHomeBinding

class PembeliHomeFragment : Fragment() {

    private var _binding: FragmentPembeliHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentPembeliHomeBinding.inflate(
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

        val sharedPref = requireActivity().getSharedPreferences(
            "UserSession",
            Context.MODE_PRIVATE
        )

        val fullName = sharedPref.getString(
            "full_name",
            "Pembeli"
        )

        binding.tvWelcome.text = "Halo, $fullName!"

        // In-App Messaging: picu campaign berbasis event kustom saat beranda dibuka
        InAppMessagingHelper.trigger(InAppMessagingHelper.EVENT_PEMBELI_HOME)

        binding.btnGoPesan.setOnClickListener {

            findNavController().navigate(
                R.id.navigation_pembeli_pesan
            )
        }

        binding.cardTokoOffline.setOnLongClickListener {

            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "https://maps.app.goo.gl/FPjcn93XpoH1M7xK7?g_st=aw"
                )
            )

            startActivity(intent)

            true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}