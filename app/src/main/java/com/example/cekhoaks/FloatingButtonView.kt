package com.example.cekhoaks

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Tombol cek berbentuk lingkaran yang dipasang di atas aplikasi lain oleh [FloatingButtonService].
 * View ini hanya membedakan ketukan dan geseran. Posisi jendelanya diatur oleh service.
 *
 * Pola yang diadaptasi (lisensi MIT): pembedaan ketukan dan geseran dengan touch slop memakai
 * rawY dan posisi awal saat jari turun, dari SavinduK, SnapCrop, https://github.com/SavinduK/SnapCrop
 *
 * @param onKetuk dipanggil saat tombol diketuk (bukan digeser).
 * @param onSentuhanMulai dipanggil saat jari mulai menyentuh tombol.
 * @param onGeser dipanggil selama tombol digeser, dengan jarak vertikal (px) dari titik awal sentuhan.
 */
// ClickableViewAccessibility: tanganiSentuhan memanggil performClick saat ketukan, jadi klik
// tetap bisa dipicu pembaca layar (TalkBack).
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class FloatingButtonView(
    context: Context,
    private val onKetuk: () -> Unit,
    private val onSentuhanMulai: () -> Unit,
    private val onGeser: (selisihY: Int) -> Unit,
) : FrameLayout(context) {

    // Jarak minimum (px) jari harus bergerak sebelum dianggap menggeser. Nilainya dari sistem,
    // sama dengan yang dipakai daftar dan tombol bawaan Android.
    private val ambangGeser = ViewConfiguration.get(context).scaledTouchSlop
    private var yAwalJari = 0f
    private var sedangDigeser = false

    init {
        val ukuran = resources.getDimensionPixelSize(R.dimen.tombol_cek_ukuran)
        val ukuranIkon = resources.getDimensionPixelSize(R.dimen.tombol_cek_ikon)
        val ruangBayangan = resources.getDimensionPixelSize(R.dimen.tombol_cek_ruang_bayangan)

        // Jendela overlay seukuran isinya (WRAP_CONTENT), jadi bayangan butuh ruang tambahan
        // di sekeliling lingkaran agar tidak terpotong.
        setPadding(ruangBayangan, ruangBayangan, ruangBayangan, ruangBayangan)
        clipToPadding = false

        val lingkaran = object : ImageView(context) {
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
            setOnClickListener { onKetuk() }
            setOnTouchListener(::tanganiSentuhan)
        }

        addView(lingkaran, LayoutParams(ukuran, ukuran, Gravity.CENTER))
    }

    private fun tanganiSentuhan(view: View, event: MotionEvent): Boolean {
        // rawY adalah posisi jari di layar. Koordinat biasa (y) relatif terhadap view, padahal
        // jendela tombol ikut berpindah saat digeser, sehingga nilainya akan ikut bergeser.
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                yAwalJari = event.rawY
                sedangDigeser = false
                view.isPressed = true
                onSentuhanMulai()
            }
            MotionEvent.ACTION_MOVE -> {
                val selisih = event.rawY - yAwalJari
                if (!sedangDigeser && abs(selisih) > ambangGeser) {
                    sedangDigeser = true
                    view.isPressed = false
                }
                if (sedangDigeser) onGeser(selisih.roundToInt())
            }
            MotionEvent.ACTION_UP -> {
                view.isPressed = false
                if (!sedangDigeser) view.performClick()
                sedangDigeser = false
            }
            MotionEvent.ACTION_CANCEL -> {
                view.isPressed = false
                sedangDigeser = false
            }
        }
        return true
    }

    companion object {
        private const val SKALA_DITEKAN = 0.88f
        private const val DURASI_ANIMASI_MS = 100L
    }
}
