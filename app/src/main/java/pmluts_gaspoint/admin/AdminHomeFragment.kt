package pmluts_gaspoint.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import pmluts_gaspoint.common.FirestoreRepository
import pmluts_gaspoint.databinding.FragmentAdminHomeBinding

class AdminHomeFragment : Fragment() {

    private var _binding: FragmentAdminHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminHomeBinding.inflate(
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

        loadDashboardSummary()
    }

    override fun onResume() {
        super.onResume()
        loadDashboardSummary()
    }

    private fun loadDashboardSummary() {
        FirestoreRepository.getDashboardSummary { summary ->
            if (_binding == null) return@getDashboardSummary

            binding.tvTotalProduk.text = summary.totalProduk.toString()
            binding.tvTotalUser.text = summary.totalUser.toString()
            binding.tvPesananAktif.text = summary.pesananAktif.toString()
            binding.tvPesananSelesai.text = summary.pesananSelesai.toString()

            binding.tvAdminNote.text =
                if (summary.menungguValidasi > 0) {
                    "Ada ${summary.menungguValidasi} pembayaran QRIS yang perlu divalidasi."
                } else {
                    "Tidak ada pembayaran yang menunggu validasi saat ini."
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}