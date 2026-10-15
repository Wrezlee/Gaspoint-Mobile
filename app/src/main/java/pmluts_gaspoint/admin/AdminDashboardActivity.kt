package pmluts_gaspoint.admin

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.navigation.NavOptions
import pmluts_gaspoint.auth.MainActivity
import pmluts_gaspoint.common.MessagingActivity
import pmluts_gaspoint.common.FcmHelper
import pmluts_gaspoint.common.InAppMessagingHelper
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import pmluts_gaspoint.R
import pmluts_gaspoint.common.FirestoreRepository
import pmluts_gaspoint.databinding.ActivityAdminDashboardBinding

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Tema NoActionBar: Toolbar dipasang manual sebagai ActionBar agar OptionsMenu tampil
        setSupportActionBar(binding.toolbarAdmin)
        FirestoreRepository.ensureSeedData()

        // FCM: channel, izin notifikasi, dan berlangganan topic
        FcmHelper.setup(this)

        // In-App Messaging: hanya Pembeli yang menerima pesan promo
        InAppMessagingHelper.setup("Admin")

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment

        val navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)
    }

    // ------------------------------------------------------------------
    // OptionsMenu
    // ------------------------------------------------------------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.admin_options_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_pesan_masuk -> {
                startActivity(Intent(this, MessagingActivity::class.java))
                true
            }

            R.id.action_muat_ulang -> {
                muatUlangData()
                true
            }

            R.id.action_keluar -> {
                konfirmasiLogout()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Memuat ulang halaman yang sedang dibuka dengan membuat ulang fragment-nya.
     * popUpTo(inklusif) mencegah halaman yang sama menumpuk di back stack.
     */
    private fun muatUlangData() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val tujuan = navController.currentDestination?.id ?: return

        navController.navigate(
            tujuan,
            null,
            NavOptions.Builder().setPopUpTo(tujuan, true).build()
        )
        Toast.makeText(this, "Data dimuat ulang", Toast.LENGTH_SHORT).show()
    }

    private fun konfirmasiLogout() {
        AlertDialog.Builder(this)
            .setTitle("Keluar")
            .setMessage("Apakah Anda yakin ingin keluar dari akun Admin?")
            .setPositiveButton("Keluar") { _, _ -> logout() }
            .setNegativeButton("Batal", null)
            .show()
    }

    /** Logika logout (dipindah dari AdminSettingsFragment). */
    fun logout() {
        FirestoreRepository.signOut()
        getSharedPreferences("UserSession", MODE_PRIVATE).edit().clear().apply()

        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment

        val navController = navHostFragment.navController

        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        } else {
            super.onBackPressed()
        }
    }
}