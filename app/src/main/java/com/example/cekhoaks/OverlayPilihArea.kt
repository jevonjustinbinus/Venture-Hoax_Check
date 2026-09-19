package com.example.cekhoaks

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Jendela layar penuh tempat pengguna memilih bagian layar yang ingin dicek.
 *
 * Objek ini memegang Bitmap layar penuh sejak dibuat dan selalu membuangnya (recycle) saat
 * selesai, baik lewat Batal, Back, rotasi layar, maupun Cek Sekarang. Setiap objek hanya
 * dipakai untuk satu kali pemilihan.
 *
 * @param onBatal dipanggil saat pengguna membatalkan (tombol Batal, tombol Back, atau layar diputar).
 * @param onPotong dipanggil dengan Bitmap potongan, atau null jika pemotongan gagal.
 */
class OverlayPilihArea(
    context: Context,
    private val windowManager: WindowManager,
    private val layarPenuh: Bitmap,
    private val onBatal: () -> Unit,
    private val onPotong: (Bitmap?) -> Unit,
) {

    // Service tidak punya tema tampilan seperti Activity, jadi tema aplikasi dipasang manual
    // agar tombol dan teks di panel tampil konsisten.
    private val context = ContextThemeWrapper(context, R.style.Theme_CekHoaks)
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val rotasiAwal = rotasiLayar()

    private var akar: AkarOverlay? = null
    private var tampilanSeleksi: CropSelectionView? = null
    private var panel: View? = null
    private var selesai = false

    // Layar diputar 180 derajat tidak memicu onConfigurationChanged, jadi rotasi dipantau
    // langsung dari DisplayManager.
    private val pemantauLayar = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY && rotasiLayar() != rotasiAwal) {
                Log.i(TAG, "Layar diputar saat memilih area, pemilihan dibatalkan")
                batal()
            }
        }
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
    }

    /** Memasang jendela. Jika gagal, Bitmap layar penuh langsung dibuang dan hasilnya false. */
    fun tampilkan(): Boolean {
        val akarBaru = AkarOverlay(context, onKembali = ::batal)
        val seleksi = CropSelectionView(context, layarPenuh, onInteraksi = ::aturPanel)
        akarBaru.addView(seleksi, FrameLayout.LayoutParams(MATCH, MATCH))

        val panelBaru = LayoutInflater.from(context).inflate(R.layout.overlay_pilih_area_panel, akarBaru, false)
        akarBaru.addView(panelBaru)
        panelBaru.findViewById<View>(R.id.pilih_batal).setOnClickListener { batal() }
        panelBaru.findViewById<View>(R.id.pilih_cek).setOnClickListener { cek() }
        panelBaru.findViewById<View>(R.id.pilih_seluruh_layar).setOnClickListener { seleksi.pilihSeluruhLayar() }
        aturJarakPanel(akarBaru, panelBaru)

        // Kilatan putih singkat sebagai tanda layar sudah diambil.
        val kilatan = View(context).apply {
            setBackgroundColor(Color.WHITE)
            alpha = ALPHA_KILATAN
        }
        akarBaru.addView(kilatan, FrameLayout.LayoutParams(MATCH, MATCH))

        try {
            windowManager.addView(akarBaru, buatLayoutParams())
        } catch (e: RuntimeException) {
            Log.e(TAG, "Gagal memasang layar pilih area", e)
            selesai = true
            layarPenuh.recycle()
            return false
        }
        akar = akarBaru
        tampilanSeleksi = seleksi
        panel = panelBaru
        displayManager.registerDisplayListener(pemantauLayar, Handler(Looper.getMainLooper()))

        kilatan.animate().alpha(0f).setDuration(DURASI_ANIMASI_MS)
            .withEndAction { kilatan.visibility = View.GONE }
        panelBaru.alpha = 0f
        panelBaru.animate().alpha(1f).setStartDelay(DURASI_ANIMASI_MS).setDuration(DURASI_ANIMASI_MS)
        return true
    }

    /**
     * Menutup jendela dan membuang Bitmap layar penuh tanpa memanggil callback, misalnya saat
     * service dimatikan. Aman dipanggil lebih dari sekali.
     */
    fun tutup() {
        if (selesai) return
        selesai = true
        lepasJendela()
        layarPenuh.recycle()
    }

    private fun batal() {
        if (selesai) return
        tutup()
        onBatal()
    }

    private fun cek() {
        if (selesai) return
        val seleksi = tampilanSeleksi ?: return
        val piksel = seleksi.seleksiDalamPiksel()
        val potongan = try {
            Bitmap.createBitmap(layarPenuh, piksel.x, piksel.y, piksel.lebar, piksel.tinggi)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Area potongan tidak valid: $piksel", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Memori tidak cukup untuk memotong layar", e)
            null
        }

        selesai = true
        lepasJendela()
        // Jika seluruh layar dipilih, createBitmap boleh mengembalikan objek yang sama dengan
        // sumbernya. Dalam kasus itu Bitmap tidak boleh dibuang karena justru itulah hasilnya.
        if (potongan !== layarPenuh) layarPenuh.recycle()
        Log.i(TAG, "Potongan: ${piksel.lebar} x ${piksel.tinggi} dari (${piksel.x}, ${piksel.y})")
        onPotong(potongan)
    }

    private fun lepasJendela() {
        displayManager.unregisterDisplayListener(pemantauLayar)
        val view = akar ?: return
        try {
            // Versi "Immediate" melepas jendela saat itu juga, sehingga Bitmap yang dibuang
            // sesudahnya tidak sempat digambar lagi.
            windowManager.removeViewImmediate(view)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Layar pilih area sudah tidak terpasang", e)
        } finally {
            akar = null
            tampilanSeleksi = null
            panel = null
        }
    }

    /** Panel memudar saat jari sedang mengatur kotak, agar tidak menutupi bagian bawah layar. */
    private fun aturPanel(sedangDiatur: Boolean) {
        val view = panel ?: return
        view.animate().cancel()
        view.animate().alpha(if (sedangDiatur) 0f else 1f).setStartDelay(0).setDuration(DURASI_ANIMASI_MS)
    }

    /**
     * Menjauhkan panel dari navigation bar dan cutout. Insets adalah ukuran bagian layar yang
     * tertutup elemen sistem; WindowInsetsCompat menyamakan cara membacanya di semua versi Android.
     */
    private fun aturJarakPanel(akar: View, panel: View) {
        val jarakSamping = context.resources.getDimensionPixelSize(R.dimen.pilih_panel_jarak_samping)
        val jarakBawah = context.resources.getDimensionPixelSize(R.dimen.pilih_panel_jarak_bawah)
        ViewCompat.setOnApplyWindowInsetsListener(akar) { _, insets ->
            val bilah = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            // Sebagian HP tidak mengirim insets ke jendela overlay. Kalau semuanya 0, dipakai
            // tinggi navigation bar dari resource sistem sebagai cadangan.
            val tanpaInsets = bilah.left == 0 && bilah.top == 0 && bilah.right == 0 && bilah.bottom == 0
            val bawah = if (tanpaInsets) context.dimenSistem("navigation_bar_height") else bilah.bottom
            panel.setPadding(jarakSamping + bilah.left, panel.paddingTop, jarakSamping + bilah.right, jarakBawah + bawah)
            insets
        }
    }

    private fun buatLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            MATCH,
            MATCH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // LAYOUT_IN_SCREEN + LAYOUT_NO_LIMITS: jendela menutupi seluruh layar fisik, termasuk
            // di bawah status bar dan navigation bar, agar gambar beku sejajar dengan layar asli.
            // Tanpa FLAG_NOT_FOCUSABLE, jendela ini menerima fokus sehingga tombol Back sampai
            // ke sini. Fokus kembali ke aplikasi di bawahnya begitu jendela dilepas.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Izinkan jendela menutupi area cutout (poni kamera). Mode ALWAYS baru ada di Android 11.
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Sejak Android 11, jendela otomatis dijauhkan dari status bar dan navigation bar.
                // Nilai 0 mematikan perilaku itu agar jendela benar-benar selebar layar fisik.
                fitInsetsTypes = 0
            }
        }

    private fun rotasiLayar(): Int = displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: 0

    /** Akar tampilan jendela; menerjemahkan tombol Back menjadi Batal. */
    @SuppressLint("ViewConstructor")
    private class AkarOverlay(context: Context, private val onKembali: () -> Unit) : FrameLayout(context) {

        // Bertipe Any agar kelas OnBackInvokedCallback (Android 13+) tidak dimuat di versi lama.
        private var callbackKembali: Any? = null

        // Android 12 ke bawah (dan 13+ yang belum memakai "predictive back") mengirim Back
        // sebagai KeyEvent biasa.
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onKembali()
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) daftarkanKembali()
        }

        override fun onDetachedFromWindow() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) lepaskanKembali()
            super.onDetachedFromWindow()
        }

        // Aplikasi dengan targetSdk 36 di Android 16 tidak lagi menerima Back sebagai KeyEvent,
        // melainkan lewat OnBackInvokedCallback yang didaftarkan ke jendela.
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private fun daftarkanKembali() {
            val callback = OnBackInvokedCallback { onKembali() }
            findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback,
            )
            callbackKembali = callback
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private fun lepaskanKembali() {
            val callback = callbackKembali as? OnBackInvokedCallback ?: return
            findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(callback)
            callbackKembali = null
        }
    }

    companion object {
        private const val TAG = "CekHoaks"
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val ALPHA_KILATAN = 0.85f
        private const val DURASI_ANIMASI_MS = 150L
    }
}
