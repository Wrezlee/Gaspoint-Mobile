package pmluts_gaspoint.common

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging

/**
 * Pengaturan Firebase Cloud Messaging (FCM) untuk GasPoint, mengikuti modul Pertemuan 5:
 *  - notification channel
 *  - izin POST_NOTIFICATIONS (Android 13+)
 *  - berlangganan topic INFO_DISKON_DAN_PROMO
 */
object FcmHelper {

    const val TAG = "FcmHelper"

    /** Topic sekaligus ID notification channel (sama seperti modul). */
    const val TOPIC = "INFO_DISKON_DAN_PROMO"

    /** Key data tambahan yang dikirim lewat Custom data di Firebase Console. */
    const val KEY_PROMO_ID = "promoId"
    const val KEY_PROMO = "promo"
    const val KEY_PROMO_UNTIL = "promoUntil"
    const val KEY_TITLE = "title"
    const val KEY_BODY = "body"

    /** Dipanggil sekali di onCreate() Activity. */
    fun setup(activity: ComponentActivity) {
        createChannel(activity)
        subscribeToTopic(activity)
        askNotificationPermission(activity)
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(
                NotificationChannel(TOPIC, TOPIC, NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    fun subscribeToTopic(context: Context) {
        Firebase.messaging.subscribeToTopic(TOPIC)
            .addOnCompleteListener { task ->
                val msg = if (task.isSuccessful) "Berhasil subscribe" else "Tidak berhasil subscribe"
                Log.d(TAG, "$msg ke topic $TOPIC")
            }
    }

    private fun askNotificationPermission(activity: ComponentActivity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) return

        activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                Toast.makeText(activity, "Izin notifikasi diberikan", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    activity, "FCM tidak dapat berjalan tanpa izin notifikasi", Toast.LENGTH_LONG
                ).show()
            }
        }.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
