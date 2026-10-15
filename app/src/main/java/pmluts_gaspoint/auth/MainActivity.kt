package pmluts_gaspoint.auth
import pmluts_gaspoint.R
import android.content.Intent
import pmluts_gaspoint.common.FcmHelper
import pmluts_gaspoint.common.FirestoreRepository
import pmluts_gaspoint.common.MessagingActivity

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import pmluts_gaspoint.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Pertahankan sesi login: jika masih login, langsung ke dashboard sesuai role
        val role = getSharedPreferences("UserSession", MODE_PRIVATE).getString("role", "") ?: ""
        val target = when (role) {
            "Admin" -> pmluts_gaspoint.admin.AdminDashboardActivity::class.java
            "Kasir" -> pmluts_gaspoint.kasir.KasirDashboardActivity::class.java
            "Pembeli" -> pmluts_gaspoint.pembeli.PembeliDashboardActivity::class.java
            "Kurir" -> pmluts_gaspoint.kurir.KurirDashboardActivity::class.java
            else -> null
        }

        if (FirestoreRepository.isLoggedIn() && target != null) {
            val dashboard = Intent(this, target)
            // deep link gaspoint://pesan dari tombol In-App Messaging: buka layar pesan (khusus Pembeli)
            if (role == "Pembeli" && isPesanDeepLink()) {
                dashboard.putExtra(EXTRA_OPEN_PESAN, true)
                dashboard.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(dashboard)
            // notifikasi FCM yang diketuk saat app di background: tampilkan isinya
            if (hasFcmExtras()) openMessaging()
            finish()
            return
        }

        if (FirestoreRepository.isLoggedIn()) {
            FirestoreRepository.signOut()
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // FCM: channel, izin notifikasi, dan berlangganan topic
        FcmHelper.setup(this)
        if (hasFcmExtras()) openMessaging()
    }

    private fun isPesanDeepLink(): Boolean =
        intent?.data?.let { it.scheme == "gaspoint" && it.host == "pesan" } == true

    companion object {
        const val EXTRA_OPEN_PESAN = "open_pesan"
    }

    private fun hasFcmExtras(): Boolean {
        val extras = intent.extras ?: return false
        return extras.containsKey("google.message_id") || extras.containsKey(FcmHelper.KEY_PROMO_ID)
    }

    private fun openMessaging() {
        val i = Intent(this, MessagingActivity::class.java)
        intent.extras?.let { i.putExtras(it) }
        startActivity(i)
    }
}
