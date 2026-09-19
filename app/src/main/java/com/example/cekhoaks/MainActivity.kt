package com.example.cekhoaks

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.cekhoaks.ui.theme.CekHoaksTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // mutableStateOf membuat tampilan Compose otomatis diperbarui saat nilainya berubah.
    private var izinOverlay by mutableStateOf(false)
    private var tampilkanPenjelasanRekam by mutableStateOf(false)
    private var sedangMemintaIzinRekam = false
    private val snackbarHostState = SnackbarHostState()

    // Hasil dialog izin notifikasi diabaikan: tombol cek tetap bisa aktif meskipun izin ditolak.
    private val mintaIzinNotifikasi =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            mintaIzinRekamLayar()
        }

    // Dialog izin rekam layar milik sistem. Hasilnya (kode + Intent) adalah "tiket" satu kali
    // yang diteruskan ke service untuk membuka sesi rekam layar.
    private val peluncurIzinRekam =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { hasil ->
            sedangMemintaIzinRekam = false
            val data = hasil.data
            if (hasil.resultCode == RESULT_OK && data != null) {
                ContextCompat.startForegroundService(
                    this,
                    FloatingButtonService.buatIntentMulai(this, hasil.resultCode, data),
                )
            } else {
                tampilkanPesan(getString(R.string.rekam_ditolak))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CekHoaksTheme {
                val tombolAktif by FloatingButtonService.berjalan.collectAsState()
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { innerPadding ->
                    LayarUtama(
                        izinOverlay = izinOverlay,
                        tombolAktif = tombolAktif,
                        tampilkanPetunjukIzin = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                        onBerikanIzin = ::bukaPengaturanIzin,
                        onAlihkanTombol = { if (tombolAktif) matikanTombolCek() else aktifkanTombolCek() },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
                if (tampilkanPenjelasanRekam) {
                    DialogPenjelasanRekam(
                        onLanjutkan = ::lanjutkanSetelahPenjelasan,
                        onBatal = { tampilkanPenjelasanRekam = false },
                    )
                }
            }
        }
    }

    // onResume dipanggil setiap kali aplikasi kembali tampil, termasuk sepulang dari Pengaturan.
    override fun onResume() {
        super.onResume()
        izinOverlay = Settings.canDrawOverlays(this)
    }

    private fun bukaPengaturanIzin() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Beberapa HP tidak mendukung halaman izin khusus per aplikasi.
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    // Alur mengaktifkan tombol: penjelasan, lalu izin notifikasi (Android 13+), lalu dialog
    // izin rekam layar, lalu service dijalankan.
    private fun aktifkanTombolCek() {
        tampilkanPenjelasanRekam = true
    }

    private fun lanjutkanSetelahPenjelasan() {
        tampilkanPenjelasanRekam = false
        val perluIzinNotifikasi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (perluIzinNotifikasi) {
            mintaIzinNotifikasi.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            mintaIzinRekamLayar()
        }
    }

    private fun mintaIzinRekamLayar() {
        izinOverlay = Settings.canDrawOverlays(this)
        // Penjaga agar dialog sistem tidak dibuka dua kali karena ketukan ganda.
        if (!izinOverlay || sedangMemintaIzinRekam) return

        val manager = getSystemService(MediaProjectionManager::class.java)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: dialog sistem hanya menawarkan perekaman seluruh layar.
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            manager.createScreenCaptureIntent()
        }
        sedangMemintaIzinRekam = true
        try {
            peluncurIzinRekam.launch(intent)
        } catch (_: ActivityNotFoundException) {
            sedangMemintaIzinRekam = false
            tampilkanPesan(getString(R.string.rekam_ditolak))
        }
    }

    private fun tampilkanPesan(pesan: String) {
        lifecycleScope.launch {
            snackbarHostState.showSnackbar(pesan, duration = SnackbarDuration.Long)
        }
    }

    private fun matikanTombolCek() {
        stopService(Intent(this, FloatingButtonService::class.java))
    }
}

@Composable
fun LayarUtama(
    izinOverlay: Boolean,
    tombolAktif: Boolean,
    tampilkanPetunjukIzin: Boolean,
    onBerikanIzin: () -> Unit,
    onAlihkanTombol: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = colorResource(R.color.utama),
        )
        Text(
            text = stringResource(R.string.main_penjelasan),
            style = MaterialTheme.typography.bodyLarge,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.main_label_izin),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(
                        if (izinOverlay) R.string.main_izin_diberikan else R.string.main_izin_belum,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (izinOverlay) colorResource(R.color.utama) else MaterialTheme.colorScheme.error,
                )
            }
        }

        if (!izinOverlay) {
            if (tampilkanPetunjukIzin) {
                Text(
                    text = stringResource(R.string.main_petunjuk_izin, stringResource(R.string.app_name)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = onBerikanIzin,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.utama)),
            ) {
                Text(stringResource(R.string.main_tombol_berikan_izin))
            }
        } else {
            Text(
                text = stringResource(
                    if (tombolAktif) R.string.main_status_tombol_aktif else R.string.main_status_tombol_mati,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (tombolAktif) {
                OutlinedButton(
                    onClick = onAlihkanTombol,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Text(stringResource(R.string.main_tombol_matikan))
                }
                Text(
                    text = stringResource(R.string.rekam_catatan_notifikasi),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Button(
                    onClick = onAlihkanTombol,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.utama)),
                ) {
                    Text(stringResource(R.string.main_tombol_aktifkan))
                }
            }
        }
    }
}

@Composable
fun DialogPenjelasanRekam(onLanjutkan: () -> Unit, onBatal: () -> Unit) {
    AlertDialog(
        onDismissRequest = onBatal,
        title = { Text(stringResource(R.string.rekam_judul)) },
        text = {
            Text(
                stringResource(R.string.rekam_penjelasan) + "\n\n" +
                    stringResource(R.string.rekam_catatan_notifikasi),
            )
        },
        confirmButton = {
            Button(
                onClick = onLanjutkan,
                colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.utama)),
            ) {
                Text(stringResource(R.string.rekam_lanjutkan))
            }
        },
        dismissButton = {
            TextButton(onClick = onBatal) {
                Text(stringResource(R.string.rekam_batal))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
fun LayarUtamaPreview() {
    CekHoaksTheme {
        LayarUtama(
            izinOverlay = false,
            tombolAktif = false,
            tampilkanPetunjukIzin = true,
            onBerikanIzin = {},
            onAlihkanTombol = {},
        )
    }
}
