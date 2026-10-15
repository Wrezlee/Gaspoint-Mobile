package pmluts_gaspoint.common

import android.content.Context
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay
import java.io.File

/**
 * Helper peta OpenStreetMap (osmdroid).
 * Pola mengikuti ReaderLocationActivity di repo referensi (Wrezlee/tarot).
 */
object PetaHelper {

    /** Pusat default (tengah Indonesia) saat belum ada titik lokasi. */
    val PUSAT_INDONESIA = GeoPoint(-2.5, 118.0)

    /**
     * Wajib dipanggil SEBELUM MapView dibuat (baik lewat code maupun inflate XML).
     * User-Agent dibutuhkan agar server tile OSM tidak memblokir request.
     */
    fun inisialisasi(context: Context) {
        val konfig = Configuration.getInstance()
        konfig.load(
            context,
            context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )
        konfig.userAgentValue = context.packageName

        // Cache tile disimpan di cache app, sehingga tidak butuh izin penyimpanan
        val basePath = File(context.cacheDir, "osmdroid")
        konfig.osmdroidBasePath = basePath
        konfig.osmdroidTileCache = File(basePath, "tiles")
    }

    fun siapkanPeta(mapView: MapView, pusat: GeoPoint, zoom: Double) {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(zoom)
        mapView.controller.setCenter(pusat)
    }

    /** Menambahkan marker baru dan mengembalikannya agar bisa dipindah/dihapus. */
    fun tambahMarker(mapView: MapView, titik: GeoPoint, judul: String): Marker {
        val marker = Marker(mapView)
        marker.position = titik
        marker.title = judul
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        mapView.overlays.add(marker)
        mapView.invalidate()
        return marker
    }

    /** Memanggil onKetuk setiap pengguna mengetuk peta. Overlay ditaruh paling bawah agar marker tetap di atas. */
    fun pasangKetukPeta(mapView: MapView, onKetuk: (GeoPoint) -> Unit) {
        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                onKetuk(p)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        }
        mapView.overlays.add(0, MapEventsOverlay(receiver))
    }
}
