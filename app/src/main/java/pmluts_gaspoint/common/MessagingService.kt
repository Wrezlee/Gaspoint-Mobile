package pmluts_gaspoint.common

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Menerima pesan FCM saat aplikasi sedang dibuka (foreground).
 * Saat aplikasi di background, notifikasi ditampilkan sistem dan datanya
 * diteruskan ke MainActivity lewat extras intent.
 */
class MessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val RC_INTENT = 100
    }

    // ketika FCM memberikan token baru
    override fun onNewToken(token: String) {
        Log.d(TAG, "token baru = $token")
    }

    // ketika aplikasi menerima pesan dari FCM
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "msg = ${message.data}")

        val intent = Intent(this, MessagingActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        // pesan data-only tidak punya bagian notification, jadi pakai nilai cadangan
        val title = message.notification?.title ?: message.data[FcmHelper.KEY_TITLE] ?: "GasPoint"
        val body = message.notification?.body ?: message.data[FcmHelper.KEY_BODY] ?: ""

        intent.putExtra(FcmHelper.KEY_TITLE, title)
        intent.putExtra(FcmHelper.KEY_BODY, body)

        // jika pesan berisi data tambahan, kirim ke MessagingActivity
        for ((key, value) in message.data) {
            intent.putExtra(key, value)
        }

        sendNotification(title, body, intent)
    }

    // menampilkan notifikasi pada perangkat
    private fun sendNotification(title: String, messageBody: String, intent: Intent) {
        val pendingIntent = PendingIntent.getActivity(
            this, RC_INTENT, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // pastikan channel sudah ada
        FcmHelper.createChannel(this)

        val notificationBuilder = NotificationCompat.Builder(this, FcmHelper.TOPIC)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setLargeIcon(
                BitmapFactory.decodeResource(resources, android.R.drawable.ic_dialog_info)
            )
            .setContentTitle(title)
            .setContentText(messageBody)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(RC_INTENT, notificationBuilder.build())
    }
}
