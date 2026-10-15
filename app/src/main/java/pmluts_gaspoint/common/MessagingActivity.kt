package pmluts_gaspoint.common

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import pmluts_gaspoint.databinding.ActivityMessagingBinding

/**
 * Layar "Pesan Masuk" (INCOMING MESSAGE): menampilkan token FCM perangkat
 * Installation ID (FIAM), serta isi notifikasi dan data tambahan (promoId, promo, promoUntil).
 */
class MessagingActivity : AppCompatActivity() {

    private lateinit var b: ActivityMessagingBinding
    private val tag = "MessagingActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        b = ActivityMessagingBinding.inflate(layoutInflater)
        setContentView(b.root)

        ViewCompat.setOnApplyWindowInsetsListener(b.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        b.btnBack.setOnClickListener { finish() }

        FcmHelper.setup(this)
        loadToken()
        loadInstallationId()
        showExtras(intent.extras)
    }

    // pesan baru masuk saat layar ini sudah terbuka
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showExtras(intent.extras)
    }

    // meminta token sebagai identifier device yang terhubung ke FCM
    private fun loadToken() {
        Firebase.messaging.token.addOnCompleteListener {
            if (!it.isSuccessful) return@addOnCompleteListener
            val token = it.result
            Log.d(FcmHelper.TOPIC, "Token = $token")
            b.edToken.setText(token)
        }
    }

    // Installation ID dipakai untuk "Test on device" pada campaign In-App Messaging
    private fun loadInstallationId() {
        InAppMessagingHelper.getInstallationId { id ->
            b.edInstallationId.setText(id.orEmpty())
        }
    }

    // menampilkan data tambahan yang dikirim (jika ada)
    private fun showExtras(bundle: Bundle?) {
        if (bundle == null) return

        for (key in bundle.keySet()) {
            Log.d(tag, "$key : ${bundle.getString(key)}")
        }

        b.edPromoId.setText(bundle.getString(FcmHelper.KEY_PROMO_ID).orEmpty())
        b.edPromo.setText(bundle.getString(FcmHelper.KEY_PROMO).orEmpty())
        b.edPromoUntil.setText(bundle.getString(FcmHelper.KEY_PROMO_UNTIL).orEmpty())

        // pesan notifikasi dari service memakai key title/body;
        // saat background, sistem memakai key gcm.notification.*
        b.edTitle.setText(
            bundle.getString(FcmHelper.KEY_TITLE)
                ?: bundle.getString("gcm.notification.title").orEmpty()
        )
        b.edBody.setText(
            bundle.getString(FcmHelper.KEY_BODY)
                ?: bundle.getString("gcm.notification.body").orEmpty()
        )
    }
}
