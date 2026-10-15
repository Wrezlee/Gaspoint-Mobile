package pmluts_gaspoint.kurir
import pmluts_gaspoint.R

import android.os.Bundle
import pmluts_gaspoint.common.FcmHelper
import pmluts_gaspoint.common.InAppMessagingHelper
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import pmluts_gaspoint.databinding.ActivityKurirDashboardBinding

class KurirDashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityKurirDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKurirDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // FCM: channel, izin notifikasi, dan berlangganan topic
        FcmHelper.setup(this)

        // In-App Messaging: hanya Pembeli yang menerima pesan promo
        InAppMessagingHelper.setup("Kurir")

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.kurir_nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        binding.kurirBottomNavigation.setupWithNavController(navController)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (navController.previousBackStackEntry != null) {
                    navController.popBackStack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
}
