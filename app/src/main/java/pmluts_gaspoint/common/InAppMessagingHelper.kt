package pmluts_gaspoint.common

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.inappmessaging.inAppMessaging
import com.google.firebase.installations.FirebaseInstallations

/**
 * Pengaturan Firebase In-App Messaging (FIAM) untuk GasPoint, mengikuti modul Pertemuan 5:
 *  - library firebase-inappmessaging-display sudah terpasang di build.gradle.kts
 *  - Installation ID dicatat di Logcat (filter: FIAM.Headless) untuk "Test on device"
 *  - pesan hanya ditampilkan kepada Pembeli; Admin/Kasir/Kurir tidak diganggu pop-up promo
 *
 * Event bawaan (app_launch, on_foreground) dipicu otomatis oleh Firebase Analytics,
 * jadi tidak perlu kode tambahan agar campaign dari Firebase Console muncul.
 */
object InAppMessagingHelper {

    const val TAG = "FIAM"

    /** Event kustom opsional, bisa dipakai sebagai trigger campaign di Console. */
    const val EVENT_PEMBELI_HOME = "pembeli_home_opened"

    /** Dipanggil di onCreate() dashboard. Pesan hanya tampil jika [role] == "Pembeli". */
    fun setup(role: String) {
        val allowed = role == "Pembeli"
        Firebase.inAppMessaging.isAutomaticDataCollectionEnabled = true
        Firebase.inAppMessaging.setMessagesSuppressed(!allowed)
        Log.d(TAG, "pesan in-app ${if (allowed) "aktif" else "ditahan"} untuk role $role")
    }

    /** Mengambil Installation ID perangkat ini (dipakai di fitur "Test on device"). */
    fun getInstallationId(onResult: (String?) -> Unit) {
        FirebaseInstallations.getInstance().id.addOnCompleteListener { task ->
            val id = if (task.isSuccessful) task.result else null
            Log.d(TAG, "Installation ID = $id")
            onResult(id)
        }
    }

    /** Memicu campaign yang memakai event kustom [event]. */
    fun trigger(event: String) {
        Firebase.inAppMessaging.triggerEvent(event)
        Log.d(TAG, "trigger event $event")
    }
}
