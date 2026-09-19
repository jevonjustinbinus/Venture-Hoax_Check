package com.example.cekhoaks

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import androidx.core.graphics.createBitmap

/**
 * Menyimpan satu sesi rekam layar selama tombol cek aktif dan menghasilkan Bitmap layar penuh.
 *
 * Cara kerjanya: MediaProjection "menyalin" layar HP ke sebuah layar virtual (VirtualDisplay).
 * Isi layar virtual itu dikirim ke ImageReader sebagai rangkaian frame. Kelas ini selalu
 * memegang satu frame terbaru, lalu mengubahnya menjadi Bitmap saat [ambil] dipanggil.
 *
 * VirtualDisplay dan ImageReader dibuat satu kali saja. Sejak Android 14, createVirtualDisplay
 * tidak boleh dipanggil lebih dari sekali pada MediaProjection yang sama, jadi saat layar
 * diputar ukurannya disesuaikan lewat [sesuaikanUkuran], tidak dibuat ulang.
 *
 * Pola yang diadaptasi (lisensi MIT):
 * - MediaProjection.Callback didaftarkan sebelum createVirtualDisplay, dan konversi Image ke
 *   Bitmap dengan memperhitungkan padding baris: Ervareza Naurian, screen-translator,
 *   https://github.com/ervareza/screen-translator
 */
class PengambilLayar(
    private val context: Context,
    private val proyeksi: MediaProjection,
    private val onDihentikanSistem: () -> Unit,
) {

    private val handlerUtama = Handler(Looper.getMainLooper())

    // Thread latar khusus untuk ImageReader, agar penerimaan frame dan konversi Bitmap
    // tidak membebani thread utama (thread yang menggambar tampilan).
    private val threadTangkap = HandlerThread("CekHoaksTangkap").apply { start() }
    private val handlerTangkap = Handler(threadTangkap.looper)

    // Setelah mulai(), empat nilai ini hanya diakses dari threadTangkap.
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var gambarTerakhir: Image? = null
    private var ukuran: Ukuran? = null

    @Volatile
    private var berhenti = false

    // Dipanggil saat sesi dihentikan dari luar aplikasi, misalnya lewat panel sistem.
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() {
            if (berhenti) return
            Log.i(TAG, "Sesi rekam layar dihentikan oleh sistem")
            onDihentikanSistem()
        }
    }

    private val pendengarFrame = ImageReader.OnImageAvailableListener { reader ->
        // Frame lama ditutup lebih dulu. ImageReader hanya punya beberapa slot buffer, dan
        // frame yang tidak ditutup membuat frame baru tidak bisa masuk.
        gambarTerakhir?.close()
        gambarTerakhir = try {
            reader.acquireLatestImage()
        } catch (e: IllegalStateException) {
            // ImageReader sudah ditutup (misalnya tepat saat layar diputar).
            null
        }
    }

    /** Memulai layar virtual. Dipanggil sekali dari thread utama. Mengembalikan false jika gagal. */
    fun mulai(): Boolean {
        // Wajib sebelum createVirtualDisplay sejak Android 14.
        proyeksi.registerCallback(callback, handlerUtama)

        val ukuranAwal = ukuranLayar()
        val reader = buatImageReader(ukuranAwal)
        val display = try {
            proyeksi.createVirtualDisplay(
                NAMA_LAYAR_VIRTUAL,
                ukuranAwal.lebar,
                ukuranAwal.tinggi,
                ukuranAwal.dpi,
                // AUTO_MIRROR: layar virtual menampilkan salinan layar utama HP.
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                null,
            )
        } catch (e: RuntimeException) {
            Log.e(TAG, "Gagal membuat layar virtual", e)
            null
        }

        imageReader = reader
        ukuran = ukuranAwal
        virtualDisplay = display
        return display != null
    }

    /**
     * Mengubah frame terbaru menjadi Bitmap. [onSelesai] dipanggil di thread utama dengan
     * Bitmap hasilnya, atau null jika belum ada frame atau konversi gagal.
     */
    fun ambil(onSelesai: (Bitmap?) -> Unit) {
        handlerTangkap.post {
            val gambar = gambarTerakhir
            gambarTerakhir = null
            val bitmap = try {
                gambar?.let(::keBitmap)
            } catch (e: RuntimeException) {
                Log.e(TAG, "Gagal mengubah frame menjadi Bitmap", e)
                null
            } finally {
                gambar?.close()
            }
            handlerUtama.post { if (!berhenti) onSelesai(bitmap) }
        }
    }

    /** Menyesuaikan ukuran layar virtual dengan ukuran layar HP saat ini, misalnya setelah diputar. */
    fun sesuaikanUkuran() {
        val ukuranBaru = ukuranLayar()
        handlerTangkap.post {
            val display = virtualDisplay ?: return@post
            if (berhenti || ukuranBaru == ukuran) return@post

            // Buffer ImageReader berukuran tetap, jadi dibuat ImageReader baru seukuran layar baru.
            val readerLama = imageReader
            val readerBaru = buatImageReader(ukuranBaru)
            display.resize(ukuranBaru.lebar, ukuranBaru.tinggi, ukuranBaru.dpi)
            display.surface = readerBaru.surface

            gambarTerakhir?.close()
            gambarTerakhir = null
            readerLama?.setOnImageAvailableListener(null, null)
            readerLama?.close()
            imageReader = readerBaru
            ukuran = ukuranBaru
            Log.d(TAG, "Ukuran layar virtual disesuaikan: ${ukuranBaru.lebar} x ${ukuranBaru.tinggi}")
        }
    }

    /** Melepas layar virtual, menutup ImageReader, dan mengakhiri sesi rekam layar. */
    fun hentikan() {
        if (berhenti) return
        berhenti = true
        // Dilepas dulu agar proyeksi.stop() di bawah tidak memicu onStop milik kita sendiri.
        proyeksi.unregisterCallback(callback)

        handlerTangkap.post {
            virtualDisplay?.release()
            virtualDisplay = null
            gambarTerakhir?.close()
            gambarTerakhir = null
            imageReader?.setOnImageAvailableListener(null, null)
            imageReader?.close()
            imageReader = null
            proyeksi.stop()
        }
        // quitSafely tetap menjalankan tugas yang sudah antre (termasuk pembersihan di atas).
        threadTangkap.quitSafely()
    }

    private fun keBitmap(gambar: Image): Bitmap {
        val bidang = gambar.planes[0]
        // Panjang satu baris di memori (rowStride) bisa lebih besar dari lebar gambar karena
        // ada padding untuk perataan memori. Bitmap dibuat selebar baris memori dulu, lalu
        // kolom padding di sisi kanan dipotong.
        val lebarBaris = bidang.rowStride / bidang.pixelStride
        val mentah = createBitmap(lebarBaris, gambar.height)
        mentah.copyPixelsFromBuffer(bidang.buffer)
        if (lebarBaris == gambar.width) return mentah

        val hasil = Bitmap.createBitmap(mentah, 0, 0, gambar.width, gambar.height)
        mentah.recycle()
        return hasil
    }

    // Format RGBA_8888 berasal dari PixelFormat, sedangkan lint mengharapkan konstanta ImageFormat.
    @SuppressLint("WrongConstant")
    private fun buatImageReader(ukuran: Ukuran): ImageReader =
        ImageReader.newInstance(ukuran.lebar, ukuran.tinggi, PixelFormat.RGBA_8888, MAKS_FRAME).apply {
            setOnImageAvailableListener(pendengarFrame, handlerTangkap)
        }

    /** Ukuran layar fisik penuh, termasuk status bar dan navigation bar, sesuai orientasi saat ini. */
    private fun ukuranLayar(): Ukuran {
        val metrik = DisplayMetrics()
        val layar = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        if (layar != null) {
            @Suppress("DEPRECATION")
            layar.getRealMetrics(metrik)
        } else {
            metrik.setTo(context.resources.displayMetrics)
        }
        return Ukuran(metrik.widthPixels, metrik.heightPixels, metrik.densityDpi)
    }

    private data class Ukuran(val lebar: Int, val tinggi: Int, val dpi: Int)

    companion object {
        private const val TAG = "CekHoaks"
        private const val NAMA_LAYAR_VIRTUAL = "CekHoaksLayar"
        private const val MAKS_FRAME = 2
    }
}
