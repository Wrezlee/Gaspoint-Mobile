package pmluts_gaspoint.common

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.ImageView
import coil.load
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pmluts_gaspoint.R
import java.util.UUID

/**
 * Supabase Storage (sesuai modul Pertemuan 7).
 *
 * Alur: file dipilih -> diunggah ke bucket Supabase -> URL publik disimpan
 * di Firestore (field gambar / bukti_pembayaran / bukti_pengantaran).
 */
object SupabaseStorageHelper {

    private const val TAG = "SupabaseStorage"

    // Isi dengan data project Supabase kamu (Settings > Data API & API Keys)
    private const val SUPABASE_URL = "https://ajsxopttanccdqzeupks.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFqc3hvcHR0YW5jY2RxemV1cGtzIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTE0NjQ0NDIsImV4cCI6MjEwNzA0MDQ0Mn0.9tvihwWLTcJ8egHPPb6T7VfR36moUKAYX4kWDYOpaMo"

    // Nama bucket (Storage > New bucket, centang Public bucket)
    const val BUCKET_NAME = "gaspoint"

    private const val MAX_SIZE_BYTES = 10 * 1024 * 1024 // sama dengan batas bucket (10 MB)

    // koneksi supabase storage
    private val supabase by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_KEY
        ) {
            install(Storage)
        }
    }

    // scope sendiri agar upload tidak terhenti ketika fragment/dialog ditutup
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Unggah gambar ke Supabase Storage.
     * onResult dipanggil di main thread: URL publik jika berhasil, null jika gagal.
     */
    fun uploadImage(
        context: Context,
        uri: Uri,
        folder: String,
        onResult: (String?) -> Unit
    ) {
        val appContext = context.applicationContext

        scope.launch {
            val url = try {
                val fileBytes = appContext.contentResolver
                    .openInputStream(uri)
                    ?.use { it.readBytes() }
                    ?: throw IllegalStateException("File tidak dapat dibaca")

                if (fileBytes.size > MAX_SIZE_BYTES) {
                    throw IllegalStateException("Ukuran file melebihi 10 MB")
                }

                val mime = appContext.contentResolver.getType(uri)
                val ext = MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(mime) ?: "jpg"
                val path = "$folder/${System.currentTimeMillis()}_" +
                        "${UUID.randomUUID().toString().take(8)}.$ext"

                val bucket = supabase.storage[BUCKET_NAME]
                bucket.upload(path, fileBytes, upsert = true)
                bucket.publicUrl(path)
            } catch (e: Exception) {
                Log.e(TAG, "Upload file gagal, error:", e)
                null
            }

            withContext(Dispatchers.Main) { onResult(url) }
        }
    }

    fun isRemote(source: String?): Boolean =
        source != null && (source.startsWith("http://") || source.startsWith("https://"))

    /**
     * Tampilkan gambar: URL Supabase dimuat dengan Coil,
     * data lama (URI lokal) tetap ditampilkan seperti sebelumnya.
     */
    fun loadImage(
        imageView: ImageView,
        source: String?,
        fallbackRes: Int = R.drawable.ic_launcher_background
    ) {
        when {
            source.isNullOrEmpty() -> imageView.setImageResource(fallbackRes)

            isRemote(source) -> imageView.load(source) {
                placeholder(fallbackRes)
                error(fallbackRes)
            }

            else -> try {
                imageView.setImageURI(Uri.parse(source))
            } catch (e: Exception) {
                imageView.setImageResource(fallbackRes)
            }
        }
    }
}
