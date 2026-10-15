package pmluts_gaspoint.kasir

import android.os.Bundle
import pmluts_gaspoint.common.FcmHelper
import pmluts_gaspoint.common.InAppMessagingHelper
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import pmluts_gaspoint.R
import pmluts_gaspoint.databinding.ActivityKasirDashboardBinding

class KasirDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKasirDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityKasirDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // FCM: channel, izin notifikasi, dan berlangganan topic
        FcmHelper.setup(this)

        // In-App Messaging: hanya Pembeli yang menerima pesan promo
        InAppMessagingHelper.setup("Kasir")

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.kasir_nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        binding.kasirBottomNavigation.setupWithNavController(navController)
    }

    override fun onBackPressed() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.kasir_nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        } else {
            super.onBackPressed()
        }
    }
}
