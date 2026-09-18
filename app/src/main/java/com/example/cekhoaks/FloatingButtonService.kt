package com.example.cekhoaks

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service yang menampilkan tombol cek di atas aplikasi lain.
 * Foreground service ditandai dengan notifikasi tetap, sehingga sistem tidak mematikannya
 * saat pengguna berpindah ke aplikasi lain.
 *
 * Pola yang diadaptasi (lisensi MIT):
 * - LayoutParams overlay dan aksi "Stop" di notifikasi lewat PendingIntent.getService:
 *   Ervareza Naurian, screen-translator, https://github.com/ervareza/screen-translator
 * - Penanda "sudah terpasang" agar overlay tidak dipasang dua kali, dan removeView yang aman:
 *   SavinduK, SnapCrop, https://github.com/SavinduK/SnapCrop
 */
class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private var tombol: FloatingButtonView? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        buatChannelNotifikasi()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_MATIKAN) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Service yang dimulai dengan startForegroundService wajib segera memanggil
        // startForeground, bahkan jika sesudahnya langsung dihentikan. Jika tidak, aplikasi crash.
        val tipe = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, ID_NOTIFIKASI, buatNotifikasi(), tipe)

        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Izin tampil di atas aplikasi lain belum diberikan, service dihentikan")
            stopSelf()
            return START_NOT_STICKY
        }

        pasangTombol()
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Ukuran layar berubah saat diputar, jadi posisi 40% tinggi layar dihitung ulang.
        val view = tombol ?: return
        try {
            windowManager.updateViewLayout(view, buatLayoutParams())
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Gagal memperbarui posisi tombol", e)
        }
    }

    override fun onDestroy() {
        lepasTombol()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        _berjalan.value = false
        super.onDestroy()
    }

    private fun pasangTombol() {
        if (tombol != null) return

        val view = FloatingButtonView(this)
        try {
            windowManager.addView(view, buatLayoutParams())
        } catch (e: RuntimeException) {
            // Misalnya izin dicabut tepat sebelum tombol dipasang.
            Log.e(TAG, "Gagal memasang tombol cek", e)
            stopSelf()
            return
        }
        tombol = view
        _berjalan.value = true
    }

    private fun lepasTombol() {
        val view = tombol ?: return
        try {
            windowManager.removeView(view)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Tombol cek sudah tidak terpasang", e)
        } finally {
            tombol = null
        }
    }

    private fun buatLayoutParams(): WindowManager.LayoutParams {
        val ruangBayangan = resources.getDimensionPixelSize(R.dimen.tombol_cek_ruang_bayangan)
        val jarakTepi = resources.getDimensionPixelSize(R.dimen.tombol_cek_jarak_tepi)
        val tinggiLayar = resources.displayMetrics.heightPixels

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // NOT_FOCUSABLE: tidak mengambil keyboard, dan sentuhan di luar tombol
            // diteruskan ke aplikasi di bawahnya.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            // Dengan gravity END, x adalah jarak dari tepi kanan. Ruang bayangan dikurangkan
            // agar lingkarannya sendiri berjarak 14dp dari tepi.
            x = jarakTepi - ruangBayangan
            y = (tinggiLayar * POSISI_VERTIKAL).toInt() - ruangBayangan
        }
    }

    private fun buatChannelNotifikasi() {
        val channel = NotificationChannel(
            ID_CHANNEL,
            getString(R.string.notif_channel_nama),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_deskripsi)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buatNotifikasi(): Notification {
        // PendingIntent adalah "titipan" Intent yang dijalankan sistem saat notifikasi diketuk.
        val bukaAplikasi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val matikan = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingButtonService::class.java).setAction(ACTION_MATIKAN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, ID_CHANNEL)
            .setSmallIcon(R.drawable.ic_verified_user)
            .setContentTitle(getString(R.string.notif_judul))
            .setContentText(getString(R.string.notif_isi))
            .setContentIntent(bukaAplikasi)
            .addAction(R.drawable.ic_verified_user, getString(R.string.notif_aksi_matikan), matikan)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "CekHoaks"
        private const val ID_CHANNEL = "tombol_cek"
        private const val ID_NOTIFIKASI = 1
        private const val ACTION_MATIKAN = "com.example.cekhoaks.action.MATIKAN_TOMBOL_CEK"
        private const val POSISI_VERTIKAL = 0.4f

        private val _berjalan = MutableStateFlow(false)

        /** Bernilai true selama tombol cek sedang terpasang di layar. */
        val berjalan: StateFlow<Boolean> = _berjalan.asStateFlow()
    }
}
