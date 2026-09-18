package com.example.cekhoaks

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat

/**
 * Tombol cek berbentuk lingkaran yang dipasang di atas aplikasi lain oleh [FloatingButtonService].
 * Untuk tahap ini, ketukan hanya memberi efek visual dan satu baris log.
 */
@SuppressLint("ViewConstructor")
class FloatingButtonView(context: Context) : FrameLayout(context) {

    init {
        val ukuran = resources.getDimensionPixelSize(R.dimen.tombol_cek_ukuran)
        val ukuranIkon = resources.getDimensionPixelSize(R.dimen.tombol_cek_ikon)
        val ruangBayangan = resources.getDimensionPixelSize(R.dimen.tombol_cek_ruang_bayangan)

        // Jendela overlay seukuran isinya (WRAP_CONTENT), jadi bayangan butuh ruang tambahan
        // di sekeliling lingkaran agar tidak terpotong.
        setPadding(ruangBayangan, ruangBayangan, ruangBayangan, ruangBayangan)
        clipToPadding = false

        val lingkaran = object : ImageView(context) {
            // setPressed dipanggil sistem saat jari menyentuh dan saat dilepas.
            override fun setPressed(pressed: Boolean) {
                super.setPressed(pressed)
                val skala = if (pressed) SKALA_DITEKAN else 1f
                animate().scaleX(skala).scaleY(skala).setDuration(DURASI_ANIMASI_MS).start()
            }
        }.apply {
            setImageResource(R.drawable.ic_verified_user)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val jarakIkon = (ukuran - ukuranIkon) / 2
            setPadding(jarakIkon, jarakIkon, jarakIkon, jarakIkon)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(context, R.color.tombol_cek_latar))
            }
            // Elevation menghasilkan bayangan; outline BACKGROUND membuat bayangannya ikut bulat.
            elevation = resources.getDimension(R.dimen.tombol_cek_bayangan)
            outlineProvider = ViewOutlineProvider.BACKGROUND
            contentDescription = context.getString(R.string.tombol_cek_deskripsi)
            isClickable = true
            isFocusable = true
            setOnClickListener { Log.d(TAG, "Tombol cek ditekan") }
        }

        addView(lingkaran, LayoutParams(ukuran, ukuran, Gravity.CENTER))
    }

    companion object {
        private const val TAG = "CekHoaks"
        private const val SKALA_DITEKAN = 0.88f
        private const val DURASI_ANIMASI_MS = 100L
    }
}
