package pmluts_gaspoint.common

import android.database.Cursor
import android.database.MatrixCursor
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UserProfile(
    val username: String,
    val role: String,
    val fullName: String,
    val email: String
)

data class DashboardSummary(
    val totalProduk: Int,
    val totalUser: Int,
    val pesananAktif: Int,
    val pesananSelesai: Int,
    val menungguValidasi: Int
)

/**
 * Pengganti DatabaseHelper (SQLite). Semua data sekarang disimpan di Cloud Firestore,
 * dan identitas pengguna dikelola oleh Firebase Authentication.
 *
 * Semua fungsi bersifat asinkron (memakai callback). Fungsi yang mengembalikan daftar data
 * memakai MatrixCursor dengan nama kolom yang sama seperti tabel SQLite sebelumnya,
 * sehingga SimpleCursorAdapter di setiap layar tetap bisa dipakai.
 *
 * Koleksi Firestore:
 *  - users/{uid}          : username, role, full_name, email, kurir_aktif
 *  - usernames/{username} : email, uid  (untuk login memakai username)
 *  - produk/{id}          : id, nama, harga, stok, deskripsi, kategori, gambar
 *  - transaksi/{id}       : id, nama_produk, jumlah, status, pembeli, alamat, total_harga,
 *                           tanggal, metode_pembayaran, bukti_pembayaran,
 *                           kurir_username, bukti_pengantaran,
 *                           jadwal_antar, tukar_tabung
 *  - alamat/{id}          : id, username, label, detail
 *  - counters/{nama}      : value (penghasil ID berurutan)
 */
object FirestoreRepository {

    private const val TAG = "FirestoreRepository"

    private const val USERS = "users"
    private const val USERNAMES = "usernames"
    private const val PRODUK = "produk"
    private const val TRANSAKSI = "transaksi"
    private const val ALAMAT = "alamat"
    private const val COUNTERS = "counters"

    private val STATUS_AKTIF = listOf(
        "Menunggu Validasi",
        "Diproses",
        "Ditugaskan",
        "Dikirim",
        "Menunggu Konfirmasi Selesai"
    )

    private val STATUS_KURIR = listOf(
        "Ditugaskan",
        "Dikirim",
        "Menunggu Konfirmasi Selesai",
        "Selesai"
    )

    private val TRX_COLS = arrayOf(
        "_id", "nama_produk", "jumlah", "status", "pembeli", "alamat",
        "total_harga", "tanggal", "metode_pembayaran", "bukti_pembayaran",
        "kurir_username", "bukti_pengantaran", "can_cancel",
        "jadwal_antar", "tukar_tabung", "alamat_lat", "alamat_lng"
    )

    private val PRODUK_COLS = arrayOf(
        "_id", "nama", "harga", "stok", "deskripsi", "kategori", "gambar"
    )

    private val USER_COLS = arrayOf(
        "_id", "username", "role", "full_name", "email", "kurir_aktif", "uid"
    )

    private val ALAMAT_COLS = arrayOf(
        "_id", "id", "username", "label", "detail", "lat", "lng"
    )

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    // ------------------------------------------------------------------
    // Helper umum
    // ------------------------------------------------------------------

    private fun now(): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

    private fun idOf(d: DocumentSnapshot): Long = d.getLong("id") ?: 0L

    private fun isValidUsername(username: String): Boolean {
        if (username.isEmpty() || username == "." || username == ".." || username.startsWith("__")) {
            return false
        }
        return Regex("^[A-Za-z0-9._-]+$").matches(username)
    }

    private fun emptyCursor(columns: Array<String>): Cursor = MatrixCursor(columns)

    private fun buildCursor(
        columns: Array<String>,
        docs: List<DocumentSnapshot>,
        rowOf: (Int, DocumentSnapshot) -> Array<Any?>
    ): Cursor {
        val cursor = MatrixCursor(columns, docs.size)
        docs.forEachIndexed { index, doc -> cursor.addRow(rowOf(index, doc)) }
        return cursor
    }

    /** Membuat ID bilangan bulat berurutan (1, 2, 3, ...) secara atomik. */
    private fun nextId(counter: String, onResult: (Int?) -> Unit) {
        val ref = db.collection(COUNTERS).document(counter)

        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val next = (snap.getLong("value") ?: 0L) + 1L
            tx.set(ref, mapOf("value" to next))
            next
        }.addOnSuccessListener {
            onResult(it.toInt())
        }.addOnFailureListener {
            Log.e(TAG, "Gagal membuat ID untuk $counter", it)
            onResult(null)
        }
    }

    // ------------------------------------------------------------------
    // Konversi dokumen -> baris cursor
    // ------------------------------------------------------------------

    private fun trxRow(d: DocumentSnapshot): Array<Any?> = arrayOf(
        idOf(d),
        d.getString("nama_produk") ?: "",
        d.getLong("jumlah") ?: 0L,
        d.getString("status") ?: "",
        d.getString("pembeli") ?: "",
        d.getString("alamat") ?: "",
        d.getDouble("total_harga") ?: 0.0,
        d.getString("tanggal") ?: "",
        d.getString("metode_pembayaran") ?: "",
        d.getString("bukti_pembayaran") ?: "",
        d.getString("kurir_username") ?: "",
        d.getString("bukti_pengantaran") ?: "",
        if (canCancelOrder(d)) 1 else 0,
        d.getString("jadwal_antar") ?: "",
        if (d.getBoolean("tukar_tabung") == true) 1 else 0,
        d.getDouble("alamat_lat"),
        d.getDouble("alamat_lng")
    )

    private fun canCancelOrder(d: DocumentSnapshot): Boolean =
        d.getString("status") in listOf("Menunggu Validasi", "Diproses") &&
                (d.get("items") as? List<*>)?.isNotEmpty() == true

    private fun produkRow(d: DocumentSnapshot): Array<Any?> = arrayOf(
        idOf(d),
        d.getString("nama") ?: "",
        d.getDouble("harga") ?: 0.0,
        d.getLong("stok") ?: 0L,
        d.getString("deskripsi") ?: "",
        d.getString("kategori") ?: "",
        d.getString("gambar") ?: ""
    )

    private fun transaksiCursor(docs: List<DocumentSnapshot>): Cursor =
        buildCursor(
            TRX_COLS,
            docs.sortedByDescending { idOf(it) }
        ) { _, d -> trxRow(d) }

    private fun produkCursor(docs: List<DocumentSnapshot>): Cursor =
        buildCursor(
            PRODUK_COLS,
            docs.sortedBy { idOf(it) }
        ) { _, d -> produkRow(d) }

    private fun userCursor(docs: List<DocumentSnapshot>): Cursor =
        buildCursor(
            USER_COLS,
            docs.sortedBy { it.getString("username") ?: "" }
        ) { index, d ->
            arrayOf(
                index.toLong(),
                d.getString("username") ?: "",
                d.getString("role") ?: "",
                d.getString("full_name") ?: "",
                d.getString("email") ?: "",
                if (d.getBoolean("kurir_aktif") == true) 1L else 0L,
                d.id
            )
        }

    // ------------------------------------------------------------------
    // Firebase Authentication
    // ------------------------------------------------------------------

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun signOut() {
        auth.signOut()
    }

    fun login(
        input: String,
        password: String,
        onResult: (UserProfile?, String?) -> Unit
    ) {
        if (input.contains("@")) {
            signInAndLoadProfile(input, password, onResult)
            return
        }

        if (!isValidUsername(input)) {
            onResult(null, "Username/Email atau Password salah")
            return
        }

        db.collection(USERNAMES).document(input).get()
            .addOnSuccessListener { snap ->
                val email = snap.getString("email")

                if (!snap.exists() || email.isNullOrEmpty()) {
                    onResult(null, "Username/Email atau Password salah")
                } else {
                    signInAndLoadProfile(email, password, onResult)
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Gagal mencari username", it)
                onResult(null, "Gagal terhubung ke server. Periksa koneksi internet.")
            }
    }

    private fun signInAndLoadProfile(
        email: String,
        password: String,
        onResult: (UserProfile?, String?) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid

                if (uid == null) {
                    onResult(null, "Login gagal")
                    return@addOnSuccessListener
                }

                db.collection(USERS).document(uid).get()
                    .addOnSuccessListener { d ->
                        if (!d.exists()) {
                            auth.signOut()
                            onResult(null, "Data akun tidak ditemukan")
                        } else {
                            onResult(
                                UserProfile(
                                    username = d.getString("username") ?: "",
                                    role = d.getString("role") ?: "",
                                    fullName = d.getString("full_name") ?: "",
                                    email = d.getString("email") ?: email
                                ),
                                null
                            )
                        }
                    }
                    .addOnFailureListener {
                        Log.e(TAG, "Gagal memuat profil", it)
                        auth.signOut()
                        onResult(null, "Gagal memuat profil pengguna")
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Gagal login", e)

                onResult(
                    null,
                    if (e is FirebaseNetworkException) {
                        "Tidak ada koneksi internet"
                    } else {
                        "Username/Email atau Password salah"
                    }
                )
            }
    }

    /** Peran yang boleh dipilih saat daftar akun. */
    val REGISTER_ROLES = listOf("Admin", "Kasir", "Kurir", "Pembeli")

    fun registerUser(
        username: String,
        pass: String,
        role: String,
        fullName: String,
        email: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (role !in REGISTER_ROLES) {
            onResult(false, "Peran tidak valid. Pilih Admin, Kasir, Kurir, atau Pembeli")
            return
        }

        if (!isValidUsername(username)) {
            onResult(false, "Username hanya boleh berisi huruf, angka, titik, garis bawah, atau strip")
            return
        }

        if (pass.length < 6) {
            onResult(false, "Password minimal 6 karakter")
            return
        }

        db.collection(USERNAMES).document(username).get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    onResult(false, "Username sudah digunakan")
                    return@addOnSuccessListener
                }

                auth.createUserWithEmailAndPassword(email, pass)
                    .addOnSuccessListener { result ->
                        val user = result.user

                        if (user == null) {
                            onResult(false, "Pendaftaran gagal")
                            return@addOnSuccessListener
                        }

                        createProfileDocs(user.uid, username, role, fullName, email) { ok ->
                            if (ok) {
                                auth.signOut()
                                onResult(true, null)
                            } else {
                                user.delete()
                                onResult(false, "Gagal menyimpan profil. Silakan coba lagi.")
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Gagal membuat akun", e)

                        onResult(
                            false,
                            when (e) {
                                is FirebaseAuthUserCollisionException -> "Email sudah terdaftar"
                                is FirebaseAuthWeakPasswordException -> "Password terlalu lemah"
                                is FirebaseAuthInvalidCredentialsException -> "Format email tidak valid"
                                is FirebaseNetworkException -> "Tidak ada koneksi internet"
                                else -> "Pendaftaran gagal: ${e.message}"
                            }
                        )
                    }
            }
            .addOnFailureListener {
                Log.e(TAG, "Gagal memeriksa username", it)
                onResult(false, "Gagal terhubung ke server. Periksa koneksi internet.")
            }
    }

    private fun createProfileDocs(
        uid: String,
        username: String,
        role: String,
        fullName: String,
        email: String,
        onDone: (Boolean) -> Unit
    ) {
        val batch = db.batch()

        batch.set(
            db.collection(USERS).document(uid),
            mapOf(
                "username" to username,
                "role" to role,
                "full_name" to fullName,
                "email" to email,
                "kurir_aktif" to false
            )
        )

        batch.set(
            db.collection(USERNAMES).document(username),
            mapOf(
                "email" to email,
                "uid" to uid
            )
        )

        batch.commit().addOnCompleteListener { onDone(it.isSuccessful) }
    }

    /**
     * Mengubah nama dan (opsional) password pembeli yang sedang login.
     * Email tidak diubah di sini karena email adalah identitas login Firebase Auth.
     */
    fun updateProfilPembeli(
        fullName: String,
        password: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val user = auth.currentUser

        if (user == null) {
            onResult(false, "Sesi login berakhir. Silakan login ulang.")
            return
        }

        db.collection(USERS).document(user.uid).update("full_name", fullName)
            .addOnSuccessListener {
                if (password.isEmpty()) {
                    onResult(true, null)
                } else {
                    user.updatePassword(password)
                        .addOnSuccessListener { onResult(true, null) }
                        .addOnFailureListener {
                            onResult(false, "Nama tersimpan, tetapi password gagal diubah: ${it.message}")
                        }
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Gagal memperbarui profil", it)
                onResult(false, "Gagal memperbarui profil")
            }
    }

    // ------------------------------------------------------------------
    // Produk awal hanya dibuat oleh akun Admin yang telah diprovisikan secara tepercaya.
    // ------------------------------------------------------------------

    fun ensureSeedData() {
        val uid = auth.currentUser?.uid ?: return
        val userRef = db.collection(USERS).document(uid)

        userRef.get()
            .addOnSuccessListener { user ->
                if (user.getString("role") != "Admin") return@addOnSuccessListener

                db.collection(PRODUK).limit(1).get()
                    .addOnSuccessListener { products ->
                        if (products.isEmpty) seedProducts()
                    }
                    .addOnFailureListener {
                        Log.e(TAG, "Gagal memeriksa produk awal", it)
                    }
            }
            .addOnFailureListener {
                Log.e(TAG, "Gagal memeriksa akun Admin", it)
            }
    }

    private fun seedProducts() {
        val products = listOf(
            arrayOf<Any>("Gas LPG 3kg", 23000.0, 50, "Tabung gas isi 3kg", "Gas"),
            arrayOf<Any>("Gas LPG 12kg", 225000.0, 20, "Tabung gas isi 12kg", "Gas"),
            arrayOf<Any>("Galon Aqua 19L", 22000.0, 30, "Air mineral isi 19L", "Galon"),
            arrayOf<Any>("Galon Le Minerale 19L", 18000.0, 40, "Air mineral isi 19L", "Galon"),
            arrayOf<Any>("Galon Cleo 19L", 19000.0, 30, "Air mineral isi 19L", "Galon"),
            arrayOf<Any>("Galon Club 19L", 17000.0, 30, "Air mineral isi 19L", "Galon")
        )

        val batch = db.batch()
        products.forEachIndexed { index, p ->
            val id = index + 1

            batch.set(
                db.collection(PRODUK).document(id.toString()),
                mapOf(
                    "id" to id,
                    "nama" to p[0],
                    "harga" to p[1],
                    "stok" to p[2],
                    "deskripsi" to p[3],
                    "kategori" to p[4],
                    "gambar" to ""
                )
            )
        }

        batch.set(
            db.collection(COUNTERS).document(PRODUK),
            mapOf("value" to products.size)
        )

        batch.commit().addOnCompleteListener {
            if (!it.isSuccessful) {
                Log.e(TAG, "Gagal mengisi data awal", it.exception)
            }
        }
    }

    // ------------------------------------------------------------------
    // Users / Kurir
    // ------------------------------------------------------------------

    private fun findUserDoc(username: String, onResult: (DocumentSnapshot?) -> Unit) {
        db.collection(USERS).whereEqualTo("username", username).limit(1).get()
            .addOnSuccessListener { onResult(it.documents.firstOrNull()) }
            .addOnFailureListener {
                Log.e(TAG, "Gagal mencari user $username", it)
                onResult(null)
            }
    }

    fun isKurirAktif(username: String, onResult: (Boolean) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onResult(false)
            return
        }

        db.collection(USERS).document(user.uid).get()
            .addOnSuccessListener { doc ->
                onResult(
                    doc.getString("username") == username &&
                            doc.getString("role") == "Kurir" &&
                            doc.getBoolean("kurir_aktif") == true
                )
            }
            .addOnFailureListener {
                Log.e(TAG, "Gagal memuat status kerja kurir", it)
                onResult(false)
            }
    }

    fun updateStatusKurir(username: String, isAktif: Boolean, onResult: (Boolean) -> Unit = {}) {
        val user = auth.currentUser
        if (user == null) {
            onResult(false)
            return
        }

        val userRef = db.collection(USERS).document(user.uid)
        db.runTransaction { tx ->
            val doc = tx.get(userRef)
            check(doc.getString("username") == username && doc.getString("role") == "Kurir") {
                "Akun bukan kurir yang valid"
            }
            tx.update(userRef, "kurir_aktif", isAktif)
        }.addOnSuccessListener {
            onResult(true)
        }.addOnFailureListener {
            Log.e(TAG, "Gagal memperbarui status kerja kurir", it)
            onResult(false)
        }
    }

    fun getUsersByRole(role: String, onResult: (Cursor) -> Unit) {
        db.collection(USERS).whereEqualTo("role", role).get()
            .addOnSuccessListener { onResult(userCursor(it.documents)) }
            .addOnFailureListener {
                Log.e(TAG, "Gagal memuat user $role", it)
                onResult(emptyCursor(USER_COLS))
            }
    }

    /** UID akun yang sedang login (dipakai agar admin tidak menghapus/mengubah akunnya sendiri). */
    fun currentUid(): String? = auth.currentUser?.uid

    /** Admin mengubah nama lengkap dan peran user. Email/username tidak diubah (identitas login). */
    fun updateUserByAdmin(
        uid: String,
        fullName: String,
        role: String,
        onResult: (Boolean) -> Unit
    ) {
        val data = mutableMapOf<String, Any>(
            "full_name" to fullName,
            "role" to role
        )
        // Status kerja hanya relevan untuk Kurir
        if (role != "Kurir") data["kurir_aktif"] = false

        db.collection(USERS).document(uid).update(data)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener {
                Log.e(TAG, "Gagal mengubah user $uid", it)
                onResult(false)
            }
    }

    /**
     * Admin menghapus profil user. Dokumen users dihapus lebih dulu (sehingga akun tidak bisa
     * masuk lagi: "Data akun tidak ditemukan"), lalu pemesanan username dilepas secara
     * best-effort. Akun Firebase Authentication tidak bisa dihapus dari sisi klien.
     */
    fun deleteUserByAdmin(
        uid: String,
        username: String,
        onResult: (Boolean) -> Unit
    ) {
        if (uid == auth.currentUser?.uid) {
            onResult(false)
            return
        }

        db.collection(USERS).document(uid).delete()
            .addOnSuccessListener {
                if (username.isNotEmpty() && isValidUsername(username)) {
                    db.collection(USERNAMES).document(username).delete()
                        .addOnFailureListener {
                            Log.w(TAG, "Username $username belum bisa dilepas (cek firestore.rules)", it)
                        }
                }
                onResult(true)
            }
            .addOnFailureListener {
                Log.e(TAG, "Gagal menghapus user $uid", it)
                onResult(false)
            }
    }

    fun getSemuaKurirAktif(onResult: (Cursor) -> Unit) {
        db.collection(USERS)
            .whereEqualTo("role", "Kurir")
            .whereEqualTo("kurir_aktif", true)
            .get()
            .addOnSuccessListener { onResult(userCursor(it.documents)) }
            .addOnFailureListener {
                Log.e(TAG, "Gagal memuat kurir aktif", it)
                onResult(emptyCursor(USER_COLS))
            }
    }

    fun getUserCountsByRole(onResult: (Map<String, Int>) -> Unit) {
        db.collection(USERS).get()
            .addOnSuccessListener { snap ->
                onResult(
                    snap.documents
                        .groupingBy { it.getString("role") ?: "" }
                        .eachCount()
                )
            }
            .addOnFailureListener {
                Log.e(TAG, "Gagal menghitung user", it)
                onResult(emptyMap())
            }
    }

    // ------------------------------------------------------------------
    // Produk
    // ------------------------------------------------------------------

    fun getAllProduk(onResult: (Cursor) -> Unit) {
        db.collection(PRODUK).get()
            .addOnSuccessListener { onResult(produkCursor(it.documents)) }
            .addOnFailureListener {
                Log.e(TAG, "Gagal memuat produk", it)
                onResult(emptyCursor(PRODUK_COLS))
            }
    }

    /** Real-time: callback dipanggil setiap data produk berubah. */
    fun observeProduk(onResult: (Cursor) -> Unit): ListenerRegistration {
        return db.collection(PRODUK).addSnapshotListener { snap, error ->
            if (error != null || snap == null) {
                Log.e(TAG, "Listener produk gagal", error)
                onResult(emptyCursor(PRODUK_COLS))
            } else {
                onResult(produkCursor(snap.documents))
            }
        }
    }

    fun addProduk(
        nama: String,
        harga: Double,
        stok: Int,
        deskripsi: String,
        kategori: String,
        gambar: String,
        onResult: (Boolean) -> Unit
    ) {
        nextId(PRODUK) { id ->
            if (id == null) {
                onResult(false)
                return@nextId
            }

            db.collection(PRODUK).document(id.toString()).set(
                mapOf(
                    "id" to id,
                    "nama" to nama,
                    "harga" to harga,
                    "stok" to stok,
                    "deskripsi" to deskripsi,
                    "kategori" to kategori,
                    "gambar" to gambar
                )
            ).addOnCompleteListener { onResult(it.isSuccessful) }
        }
    }

    fun updateProduk(
        id: Int,
        nama: String,
        harga: Double,
        stok: Int,
        deskripsi: String,
        kategori: String,
        gambar: String,
        onResult: (Boolean) -> Unit
    ) {
        db.collection(PRODUK).document(id.toString()).update(
            mapOf(
                "nama" to nama,
                "harga" to harga,
                "stok" to stok,
                "deskripsi" to deskripsi,
                "kategori" to kategori,
                "gambar" to gambar
            )
        ).addOnCompleteListener { onResult(it.isSuccessful) }
    }

    /** Menambah stok produk secara atomik (dipakai ContextMenu "Tambah stok"). */
    fun tambahStokProduk(id: Int, jumlah: Int, onResult: (Boolean) -> Unit = {}) {
        db.collection(PRODUK).document(id.toString())
            .update("stok", FieldValue.increment(jumlah.toLong()))
            .addOnCompleteListener { onResult(it.isSuccessful) }
    }

    fun deleteProduk(id: Int, onResult: (Boolean) -> Unit = {}) {
        db.collection(PRODUK).document(id.toString()).delete()
            .addOnCompleteListener { onResult(it.isSuccessful) }
    }

    fun kurangiStokProduk(namaProduk: String, jumlah: Int) {
        db.collection(PRODUK).whereEqualTo("nama", namaProduk).limit(1).get()
            .addOnSuccessListener { snap ->
                snap.documents.firstOrNull()?.reference
                    ?.update("stok", FieldValue.increment(-jumlah.toLong()))
            }
            .addOnFailureListener {
                Log.e(TAG, "Gagal mengurangi stok $namaProduk", it)
            }
    }

    /** Mengurangi stok beberapa produk sekaligus; onDone dipanggil setelah semuanya selesai. */
    fun kurangiStokBanyak(items: List<Pair<String, Int>>, onDone: () -> Unit) {
        if (items.isEmpty()) {
            onDone()
            return
        }

        val tasks = items.map { (nama, jumlah) ->
            db.collection(PRODUK).whereEqualTo("nama", nama).limit(1).get()
                .continueWithTask<Void> { t ->
                    val doc = t.result?.documents?.firstOrNull()

                    if (doc == null) {
                        Tasks.forResult<Void>(null)
                    } else {
                        doc.reference.update("stok", FieldValue.increment(-jumlah.toLong()))
                    }
                }
        }

        Tasks.whenAllComplete(tasks).addOnCompleteListener { onDone() }
    }

    // ------------------------------------------------------------------
    // Transaksi / Pesanan
    // ------------------------------------------------------------------

    private fun insertTransaksi(data: Map<String, Any>, onResult: (Boolean) -> Unit) {
        nextId(TRANSAKSI) { id ->
            if (id == null) {
                onResult(false)
                return@nextId
            }

            db.collection(TRANSAKSI).document(id.toString())
                .set(data + ("id" to id))
                .addOnCompleteListener { onResult(it.isSuccessful) }
        }
    }

    /** Transaksi offline dari kasir (langsung selesai). */
    fun addTransaksiGabungan(
        produkRingkasan: String,
        jumlahTotal: Int,
        total: Double,
        metode: String,
        bukti: String = "",
        onResult: (Boolean) -> Unit
    ) {
        insertTransaksi(
            mapOf(
                "nama_produk" to produkRingkasan,
                "jumlah" to jumlahTotal,
                "status" to "Selesai",
                "pembeli" to "Offline (Kasir)",
                "alamat" to "Toko",
                "total_harga" to total,
                "tanggal" to now(),
                "metode_pembayaran" to metode,
                "bukti_pembayaran" to bukti,
                "kurir_username" to "",
                "bukti_pengantaran" to ""
            ),
            onResult
        )
    }

    /** Pesanan online dari pembeli. */
    fun tambahPesananPembeliGabungan(
        produkRingkasan: String,
        jumlahTotal: Int,
        pembeli: String,
        alamat: String,
        total: Double,
        metode: String,
        bukti: String = "",
        onResult: (Boolean) -> Unit
    ) {
        insertTransaksi(
            mapOf(
                "nama_produk" to produkRingkasan,
                "jumlah" to jumlahTotal,
                "status" to if (metode == "QRIS") "Menunggu Validasi" else "Diproses",
                "pembeli" to pembeli,
                "alamat" to alamat,
                "total_harga" to total,
                "tanggal" to now(),
                "metode_pembayaran" to metode,
                "bukti_pembayaran" to bukti,
                "kurir_username" to "",
                "bukti_pengantaran" to ""
            ),
            onResult
        )
    }

    data class OrderItem(
        val productId: Int,
        val quantity: Int
    )

    fun buatPesananPembeli(
        items: List<OrderItem>,
        pembeli: String,
        alamat: String,
        metode: String,
        expectedTotal: Double,
        bukti: String = "",
        jadwalAntar: String = "",
        tukarTabung: Boolean = false,
        alamatLat: Double? = null,
        alamatLng: Double? = null,
        onResult: (Boolean) -> Unit
    ) {
        if (metode !in listOf("COD", "QRIS") || (metode == "QRIS" && bukti.isBlank()) ||
            alamat.isBlank() || pembeli.isBlank() ||
            items.isEmpty() || items.any { it.productId <= 0 || it.quantity <= 0 } ||
            items.map { it.productId }.distinct().size != items.size
        ) {
            onResult(false)
            return
        }

        val counterRef = db.collection(COUNTERS).document(TRANSAKSI)
        val productRefs = items.map { db.collection(PRODUK).document(it.productId.toString()) }

        db.runTransaction { tx ->
            val counterSnapshot = tx.get(counterRef)
            val productSnapshots = productRefs.map(tx::get)
            val lines = items.zip(productSnapshots).map { (item, product) ->
                check(product.exists()) { "Produk tidak ditemukan" }
                val stock = product.getLong("stok")?.toInt() ?: 0
                check(stock >= item.quantity) { "Stok produk tidak mencukupi" }
                val price = product.getDouble("harga")
                    ?: product.getLong("harga")?.toDouble()
                    ?: error("Harga produk tidak valid")

                mapOf(
                    "product_id" to item.productId,
                    "name" to (product.getString("nama") ?: ""),
                    "quantity" to item.quantity,
                    "unit_price" to price
                )
            }

            val next = (counterSnapshot.getLong("value") ?: 0L) + 1L
            val total = lines.sumOf {
                (it["unit_price"] as Double) * (it["quantity"] as Int)
            }
            check(total == expectedTotal) { "Harga produk berubah saat checkout" }
            val productSummary = lines.joinToString("; ") {
                "${it["name"]} x${it["quantity"]}"
            }
            val itemsByProduct = lines.associate { line ->
                (line["product_id"] as Int).toString() to mapOf(
                    "name" to line["name"],
                    "quantity" to line["quantity"]
                )
            }

            items.zip(productRefs).forEach { (item, ref) ->
                tx.update(
                    ref,
                    mapOf(
                        "stok" to FieldValue.increment(-item.quantity.toLong()),
                        "reserve_order_id" to next.toString()
                    )
                )
            }
            tx.set(counterRef, mapOf("value" to next))
            tx.set(
                db.collection(TRANSAKSI).document(next.toString()),
                mapOf(
                    "id" to next,
                    "nama_produk" to productSummary,
                    "items" to lines,
                    "items_by_product" to itemsByProduct,
                    "jumlah" to items.sumOf { it.quantity },
                    "status" to if (metode == "QRIS") "Menunggu Validasi" else "Diproses",
                    "pembeli" to pembeli,
                    "alamat" to alamat,
                    "total_harga" to total,
                    "tanggal" to now(),
                    "metode_pembayaran" to metode,
                    "bukti_pembayaran" to bukti,
                    "kurir_username" to "",
                    "bukti_pengantaran" to "",
                    "jadwal_antar" to jadwalAntar,
                    "tukar_tabung" to tukarTabung,
                    "alamat_lat" to alamatLat,
                    "alamat_lng" to alamatLng
                )
            )
            next
        }.addOnSuccessListener {
            onResult(true)
        }.addOnFailureListener {
            Log.e(TAG, "Gagal membuat pesanan pembeli", it)
            onResult(false)
        }
    }

    fun batalkanPesananPembeli(id: Int, pembeli: String, onResult: (Boolean) -> Unit) {
        val orderRef = db.collection(TRANSAKSI).document(id.toString())

        db.runTransaction { tx ->
            val order = tx.get(orderRef)
            check(order.exists() && order.getString("pembeli") == pembeli) {
                "Pesanan tidak ditemukan"
            }
            check(order.getString("status") in listOf("Menunggu Validasi", "Diproses")) {
                "Pesanan sudah tidak dapat dibatalkan"
            }
            val rawLines = order.get("items") as? List<*>
                ?: error("Detail barang pesanan tidak tersedia")
            check(rawLines.isNotEmpty()) { "Detail barang pesanan tidak tersedia" }
            val items = rawLines.map { rawLine ->
                val line = rawLine as? Map<*, *>
                    ?: error("Detail barang pesanan tidak valid")
                val productId = (line["product_id"] as? Number)?.toInt()
                    ?: error("ID produk pesanan tidak valid")
                val quantity = (line["quantity"] as? Number)?.toInt()
                    ?: error("Jumlah produk pesanan tidak valid")
                check(quantity > 0) { "Jumlah produk pesanan tidak valid" }
                OrderItem(productId, quantity)
            }
            val productRefs = items.map { item ->
                db.collection(PRODUK).document(item.productId.toString()) to
                        item.quantity.toLong()
            }
            val products = productRefs.map { (ref, _) -> tx.get(ref) }
            check(products.all { it.exists() }) { "Produk pesanan tidak ditemukan" }

            productRefs.forEach { (ref, quantity) ->
                tx.update(
                    ref,
                    mapOf(
                        "stok" to FieldValue.increment(quantity),
                        "cancel_order_id" to id.toString()
                    )
                )
            }
            tx.update(
                orderRef,
                mapOf(
                    "status" to "Dibatalkan",
                    "dibatalkan_pada" to FieldValue.serverTimestamp()
                )
            )
            true
        }.addOnSuccessListener {
            onResult(it)
        }.addOnFailureListener {
            Log.e(TAG, "Gagal membatalkan pesanan $id", it)
            onResult(false)
        }
    }

    private fun updateTransaksi(
        id: Int,
        fields: Map<String, Any>,
        onResult: (Boolean) -> Unit
    ) {
        db.collection(TRANSAKSI).document(id.toString()).update(fields)
            .addOnCompleteListener {
                if (!it.isSuccessful) {
                    Log.e(TAG, "Gagal memperbarui transaksi $id", it.exception)
                }

                onResult(it.isSuccessful)
            }
    }

    fun updateStatus(id: Int, statusBaru: String, onResult: (Boolean) -> Unit = {}) {
        updateTransaksi(id, mapOf("status" to statusBaru), onResult)
    }

    fun tandaiDikirim(id: Int, onResult: (Boolean) -> Unit = {}) {
        updateStatus(id, "Dikirim", onResult)
    }

    fun tandaiSelesai(id: Int, onResult: (Boolean) -> Unit = {}) {
        updateStatus(id, "Selesai", onResult)
    }

    fun validasiPembayaran(id: Int, onResult: (Boolean) -> Unit = {}) {
        updateStatus(id, "Diproses", onResult)
    }

    fun assignKurir(id: Int, usernameKurir: String, onResult: (Boolean) -> Unit = {}) {
        updateTransaksi(
            id,
            mapOf(
                "kurir_username" to usernameKurir,
                "status" to "Ditugaskan"
            ),
            onResult
        )
    }

    fun uploadBuktiPengantaran(id: Int, bukti: String, onResult: (Boolean) -> Unit = {}) {
        updateTransaksi(
            id,
            mapOf(
                "bukti_pengantaran" to bukti,
                "status" to "Menunggu Konfirmasi Selesai"
            ),
            onResult
        )
    }

    /**
     * QR: isi QR pesanan. Dibuat pembeli, dipindai kurir.
     */
    fun isiQrPesanan(id: Int): String = "GASPOINT-$id"

    /** Kebalikan isiQrPesanan: ambil ID pesanan dari isi QR, atau null jika bukan QR GasPoint. */
    fun idDariQr(isi: String?): Int? {
        val teks = isi?.trim() ?: return null
        if (!teks.startsWith("GASPOINT-")) return null
        return teks.removePrefix("GASPOINT-").toIntOrNull()
    }

    /**
     * QR: kurir memindai QR penerima, pesanan langsung Selesai.
     * Field konfirmasi_qr menandai bahwa penyelesaian berasal dari pemindaian QR.
     */
    fun konfirmasiPengantaranQr(id: Int, onResult: (Boolean) -> Unit = {}) {
        updateTransaksi(
            id,
            mapOf(
                "status" to "Selesai",
                "konfirmasi_qr" to true
            ),
            onResult
        )
    }

    fun deleteTransaksi(id: Int, onResult: (Boolean) -> Unit = {}) {
        db.collection(TRANSAKSI).document(id.toString()).delete()
            .addOnCompleteListener { onResult(it.isSuccessful) }
    }

    fun getTransaksiById(id: Int, onResult: (Cursor) -> Unit) {
        db.collection(TRANSAKSI).document(id.toString()).get()
            .addOnSuccessListener { doc ->
                onResult(
                    if (doc.exists()) transaksiCursor(listOf(doc)) else emptyCursor(TRX_COLS)
                )
            }
            .addOnFailureListener {
                Log.e(TAG, "Gagal memuat transaksi $id", it)
                onResult(emptyCursor(TRX_COLS))
            }
    }

    fun getTransaksiByStatus(status: String, onResult: (Cursor) -> Unit) {
        db.collection(TRANSAKSI).whereEqualTo("status", status).get()
            .addOnSuccessListener { onResult(transaksiCursor(it.documents)) }
            .addOnFailureListener {
                Log.e(TAG, "Gagal memuat transaksi $status", it)
                onResult(emptyCursor(TRX_COLS))
            }
    }

    private fun observeTransaksi(
        query: Query,
        filter: (DocumentSnapshot) -> Boolean,
        onResult: (Cursor) -> Unit
    ): ListenerRegistration {
        return query.addSnapshotListener { snap, error ->
            if (error != null || snap == null) {
                Log.e(TAG, "Listener transaksi gagal", error)
                onResult(emptyCursor(TRX_COLS))
            } else {
                onResult(transaksiCursor(snap.documents.filter(filter)))
            }
        }
    }

    /** Real-time: semua transaksi (untuk admin). */
    fun observeAllTransaksi(onResult: (Cursor) -> Unit): ListenerRegistration =
        observeTransaksi(db.collection(TRANSAKSI), { true }, onResult)

    /** Real-time: pesanan milik satu pembeli. */
    fun observeTransaksiByPembeli(
        username: String,
        onlySelesai: Boolean = false,
        onResult: (Cursor) -> Unit
    ): ListenerRegistration =
        observeTransaksi(
            db.collection(TRANSAKSI).whereEqualTo("pembeli", username),
            { !onlySelesai || it.getString("status") == "Selesai" },
            onResult
        )

    /** Real-time: pesanan yang ditugaskan ke satu kurir. */
    fun observePesananKurir(
        username: String,
        onResult: (Cursor) -> Unit
    ): ListenerRegistration =
        observeTransaksi(
            db.collection(TRANSAKSI).whereEqualTo("kurir_username", username),
            { it.getString("status") in STATUS_KURIR },
            onResult
        )

    fun getDashboardSummary(onResult: (DashboardSummary) -> Unit) {
        val tProduk = db.collection(PRODUK).get()
        val tUsers = db.collection(USERS).get()
        val tTransaksi = db.collection(TRANSAKSI).get()

        Tasks.whenAllSuccess<QuerySnapshot>(tProduk, tUsers, tTransaksi)
            .addOnSuccessListener { results ->
                val statuses = results[2].documents.map { it.getString("status") ?: "" }

                onResult(
                    DashboardSummary(
                        totalProduk = results[0].size(),
                        totalUser = results[1].size(),
                        pesananAktif = statuses.count { it in STATUS_AKTIF },
                        pesananSelesai = statuses.count { it == "Selesai" },
                        menungguValidasi = statuses.count { it == "Menunggu Validasi" }
                    )
                )
            }
            .addOnFailureListener {
                Log.e(TAG, "Gagal memuat ringkasan dashboard", it)
                onResult(DashboardSummary(0, 0, 0, 0, 0))
            }
    }

    // ------------------------------------------------------------------
    // Alamat
    // ------------------------------------------------------------------

    fun getAlamatByUsername(username: String, onResult: (Cursor) -> Unit) {
        db.collection(ALAMAT).whereEqualTo("username", username).get()
            .addOnSuccessListener { snap ->
                onResult(
                    buildCursor(
                        ALAMAT_COLS,
                        snap.documents.sortedBy { idOf(it) }
                    ) { _, d ->
                        arrayOf(
                            idOf(d),
                            idOf(d),
                            d.getString("username") ?: "",
                            d.getString("label") ?: "",
                            d.getString("detail") ?: "",
                            d.getDouble("lat"),
                            d.getDouble("lng")
                        )
                    }
                )
            }
            .addOnFailureListener {
                Log.e(TAG, "Gagal memuat alamat", it)
                onResult(emptyCursor(ALAMAT_COLS))
            }
    }

    fun addAlamat(
        username: String,
        label: String,
        detail: String,
        lat: Double? = null,
        lng: Double? = null,
        onResult: (Boolean) -> Unit
    ) {
        nextId(ALAMAT) { id ->
            if (id == null) {
                onResult(false)
                return@nextId
            }

            db.collection(ALAMAT).document(id.toString()).set(
                mapOf(
                    "id" to id,
                    "username" to username,
                    "label" to label,
                    "detail" to detail,
                    "lat" to lat,
                    "lng" to lng
                )
            ).addOnCompleteListener { onResult(it.isSuccessful) }
        }
    }

    fun deleteAlamat(id: Int, onResult: (Boolean) -> Unit = {}) {
        db.collection(ALAMAT).document(id.toString()).delete()
            .addOnCompleteListener { onResult(it.isSuccessful) }
    }
}