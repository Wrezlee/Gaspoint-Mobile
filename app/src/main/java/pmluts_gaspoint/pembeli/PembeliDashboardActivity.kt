package pmluts_gaspoint.pembeli

import android.content.Intent
import android.os.Bundle
import pmluts_gaspoint.auth.MainActivity
import pmluts_gaspoint.common.FcmHelper
import pmluts_gaspoint.common.InAppMessagingHelper
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import pmluts_gaspoint.R
import pmluts_gaspoint.databinding.ActivityPembeliDashboardBinding

class PembeliDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPembeliDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPembeliDashboardBinding.inflate(layoutInflater)

        setContentView(binding.root)

        // FCM: channel, izin notifikasi, dan berlangganan topic
        FcmHelper.setup(this)

        // In-App Messaging: hanya Pembeli yang menerima pesan promo
        InAppMessagingHelper.setup("Pembeli")

        val navHostFragment =
            supportFragmentManager.findFragmentById(
                R.id.pembeli_nav_host_fragment
            ) as NavHostFragment

        val navController = navHostFragment.navController

        binding.pembeliBottomNavigation.setupWithNavController(
            navController
        )

        // dibuka dari tombol In-App Messaging (gaspoint://pesan)
        handleDeepLink(intent)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {

                    if (navController.previousBackStackEntry != null) {

                        navController.popBackStack()

                    } else {

                        isEnabled = false

                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    // activity sudah terbuka (singleTop) lalu tombol pop-up ditekan
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        if (intent?.getBooleanExtra(MainActivity.EXTRA_OPEN_PESAN, false) != true) return
        intent.removeExtra(MainActivity.EXTRA_OPEN_PESAN)
        val navHost = supportFragmentManager.findFragmentById(
            R.id.pembeli_nav_host_fragment
        ) as NavHostFragment
        navHost.navController.navigate(R.id.navigation_pembeli_pesan)
    }
}