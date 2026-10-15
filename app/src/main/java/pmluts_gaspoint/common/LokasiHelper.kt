package pmluts_gaspoint.common

import android.Manifest
import android.annotation.SuppressLint
import android.location.Location
import androidx.fragment.app.Fragment
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.permissionx.guolindev.PermissionX

/**
 * Helper GPS untuk Fragment.
 * Izin diminta lewat PermissionX (dipakai juga di repo referensi).
 * Koordinat diambil dengan FusedLocationProviderClient, library Google yang juga dipakai
 * AirLocation. AirLocation sengaja tidak dipakai karena butuh penerusan hasil izin dari Activity,
 * sedangkan di sini semuanya bisa berjalan dari dalam Fragment.
 */
object LokasiHelper {

    fun ambilLokasiSaatIni(
        fragment: Fragment,
        onIzinDitolak: () -> Unit,
        onHasil: (Location?) -> Unit
    ) {
        PermissionX.init(fragment)
            .permissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            .request { allGranted, _, _ ->
                if (!fragment.isAdded) return@request

                if (allGranted) {
                    ambilDenganIzin(fragment, onHasil)
                } else {
                    onIzinDitolak()
                }
            }
    }

    @SuppressLint("MissingPermission")
    private fun ambilDenganIzin(fragment: Fragment, onHasil: (Location?) -> Unit) {
        val client = LocationServices.getFusedLocationProviderClient(fragment.requireContext())
        val token = CancellationTokenSource()

        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
            .addOnSuccessListener { lokasi ->
                if (fragment.isAdded) onHasil(lokasi)
            }
            .addOnFailureListener {
                if (fragment.isAdded) onHasil(null)
            }
    }
}
