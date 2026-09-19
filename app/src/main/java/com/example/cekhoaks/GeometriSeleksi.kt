package com.example.cekhoaks

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/** Persegi panjang dalam koordinat view (piksel, pecahan diperbolehkan). */
data class Kotak(val kiri: Float, val atas: Float, val kanan: Float, val bawah: Float) {
    val lebar: Float get() = kanan - kiri
    val tinggi: Float get() = bawah - atas

    fun berisi(x: Float, y: Float): Boolean = x in kiri..kanan && y in atas..bawah
}

/** Area potongan dalam piksel Bitmap, siap dipakai untuk Bitmap.createBitmap. */
data class KotakPiksel(val x: Int, val y: Int, val lebar: Int, val tinggi: Int)

/** Bagian kotak seleksi yang dipegang untuk mengubah ukuran. */
enum class Pegangan(val kiri: Boolean, val atas: Boolean, val kanan: Boolean, val bawah: Boolean) {
    KIRI_ATAS(true, true, false, false),
    KANAN_ATAS(false, true, true, false),
    KIRI_BAWAH(true, false, false, true),
    KANAN_BAWAH(false, false, true, true),
    ATAS(false, true, false, false),
    BAWAH(false, false, false, true),
    KIRI(true, false, false, false),
    KANAN(false, false, true, false),
}

/**
 * Perhitungan kotak seleksi pada layar pilih area. Semua fungsi di sini murni (tidak memakai
 * kelas Android), sehingga bisa diuji dengan unit test biasa di GeometriSeleksiTest.
 */
object GeometriSeleksi {

    // Toleransi pembulatan: 540.0001 tetap dianggap 540, bukan dibulatkan ke 541.
    private const val TOLERANSI = 0.001f

    /**
     * Posisi Bitmap saat digambar di view: skala seragam sebesar mungkin tanpa terpotong,
     * diletakkan di tengah. Jika ukuran Bitmap sama dengan view, hasilnya persis seluas view.
     */
    fun areaGambar(lebarView: Int, tinggiView: Int, lebarBitmap: Int, tinggiBitmap: Int): Kotak {
        val skala = min(lebarView.toFloat() / lebarBitmap, tinggiView.toFloat() / tinggiBitmap)
        val lebar = lebarBitmap * skala
        val tinggi = tinggiBitmap * skala
        val kiri = (lebarView - lebar) / 2f
        val atas = (tinggiView - tinggi) / 2f
        return Kotak(kiri, atas, kiri + lebar, atas + tinggi)
    }

    /**
     * Mengubah kotak seleksi (koordinat view) menjadi area piksel di Bitmap.
     * Tepi kiri dan atas dibulatkan ke bawah, tepi kanan dan bawah ke atas, agar isi yang
     * terlihat di dalam kotak tidak ada yang terpotong. Hasilnya selalu di dalam Bitmap dan
     * minimal 1 x 1 piksel.
     */
    fun petakanKeBitmap(seleksi: Kotak, area: Kotak, lebarBitmap: Int, tinggiBitmap: Int): KotakPiksel {
        val skala = area.lebar / lebarBitmap
        val kiri = floor((seleksi.kiri - area.kiri) / skala + TOLERANSI).toInt().coerceIn(0, lebarBitmap - 1)
        val atas = floor((seleksi.atas - area.atas) / skala + TOLERANSI).toInt().coerceIn(0, tinggiBitmap - 1)
        val kanan = ceil((seleksi.kanan - area.kiri) / skala - TOLERANSI).toInt().coerceIn(kiri + 1, lebarBitmap)
        val bawah = ceil((seleksi.bawah - area.atas) / skala - TOLERANSI).toInt().coerceIn(atas + 1, tinggiBitmap)
        return KotakPiksel(kiri, atas, kanan - kiri, bawah - atas)
    }

    /** Kotak awal di tengah: 80% lebar dan 40% tinggi area, tetapi tidak lebih kecil dari minimum. */
    fun kotakAwal(batas: Kotak, ukuranMin: Float): Kotak {
        val lebar = (batas.lebar * 0.8f).coerceIn(min(ukuranMin, batas.lebar), batas.lebar)
        val tinggi = (batas.tinggi * 0.4f).coerceIn(min(ukuranMin, batas.tinggi), batas.tinggi)
        val kiri = batas.kiri + (batas.lebar - lebar) / 2f
        val atas = batas.atas + (batas.tinggi - tinggi) / 2f
        return Kotak(kiri, atas, kiri + lebar, atas + tinggi)
    }

    /** Memindahkan kotak sejauh (dx, dy) tanpa mengubah ukurannya dan tanpa keluar dari batas. */
    fun geser(awal: Kotak, dx: Float, dy: Float, batas: Kotak): Kotak {
        val kiri = (awal.kiri + dx).coerceIn(batas.kiri, maxOf(batas.kiri, batas.kanan - awal.lebar))
        val atas = (awal.atas + dy).coerceIn(batas.atas, maxOf(batas.atas, batas.bawah - awal.tinggi))
        return Kotak(kiri, atas, kiri + awal.lebar, atas + awal.tinggi)
    }

    /**
     * Mengubah ukuran kotak dengan menarik [pegangan] sejauh (dx, dy) dari posisi awal gestur.
     * Tepi yang ditarik berhenti di batas layar dan tidak bisa membuat kotak lebih kecil dari
     * [ukuranMin]. Tepi lain tidak bergerak.
     */
    fun ubahUkuran(awal: Kotak, pegangan: Pegangan, dx: Float, dy: Float, batas: Kotak, ukuranMin: Float): Kotak {
        val minLebar = min(ukuranMin, batas.lebar)
        val minTinggi = min(ukuranMin, batas.tinggi)
        var kiri = awal.kiri
        var atas = awal.atas
        var kanan = awal.kanan
        var bawah = awal.bawah
        if (pegangan.kiri) kiri = (awal.kiri + dx).coerceIn(batas.kiri, maxOf(batas.kiri, awal.kanan - minLebar))
        if (pegangan.kanan) kanan = (awal.kanan + dx).coerceIn(minOf(batas.kanan, awal.kiri + minLebar), batas.kanan)
        if (pegangan.atas) atas = (awal.atas + dy).coerceIn(batas.atas, maxOf(batas.atas, awal.bawah - minTinggi))
        if (pegangan.bawah) bawah = (awal.bawah + dy).coerceIn(minOf(batas.bawah, awal.atas + minTinggi), batas.bawah)
        return Kotak(kiri, atas, kanan, bawah)
    }

    /**
     * Kotak baru yang digambar dari titik awal jari ke titik sekarang. Jika lebih kecil dari
     * minimum, kotak diperbesar ke arah gerakan jari, lalu digeser agar tetap di dalam batas.
     */
    fun kotakBaru(xAwal: Float, yAwal: Float, xSekarang: Float, ySekarang: Float, batas: Kotak, ukuranMin: Float): Kotak {
        val (kiri, kanan) = rentang(xAwal, xSekarang, batas.kiri, batas.kanan, ukuranMin)
        val (atas, bawah) = rentang(yAwal, ySekarang, batas.atas, batas.bawah, ukuranMin)
        return Kotak(kiri, atas, kanan, bawah)
    }

    /** Versi satu dimensi dari [kotakBaru]. */
    private fun rentang(awal: Float, sekarang: Float, batasMin: Float, batasMaks: Float, ukuranMin: Float): Pair<Float, Float> {
        val a = awal.coerceIn(batasMin, batasMaks)
        val b = sekarang.coerceIn(batasMin, batasMaks)
        val panjangMin = min(ukuranMin, batasMaks - batasMin)
        if (abs(b - a) >= panjangMin) return Pair(minOf(a, b), maxOf(a, b))

        var mulai = if (b >= a) a else a - panjangMin
        mulai = mulai.coerceIn(batasMin, batasMaks - panjangMin)
        return Pair(mulai, mulai + panjangMin)
    }

    /**
     * Pegangan yang berada di bawah jari, atau null jika jari tidak menyentuh pegangan mana pun.
     * Sudut diperiksa lebih dulu daripada sisi. [radiusSentuh] adalah setengah lebar area sentuh.
     */
    fun peganganDiTitik(kotak: Kotak, x: Float, y: Float, radiusSentuh: Float): Pegangan? {
        val dekatKiri = abs(x - kotak.kiri) <= radiusSentuh
        val dekatKanan = abs(x - kotak.kanan) <= radiusSentuh
        val dekatAtas = abs(y - kotak.atas) <= radiusSentuh
        val dekatBawah = abs(y - kotak.bawah) <= radiusSentuh

        // Jika kotak kecil sehingga dua tepi sama-sama dekat, yang dipilih adalah tepi terdekat.
        val pilihKiri = dekatKiri && (!dekatKanan || abs(x - kotak.kiri) <= abs(x - kotak.kanan))
        val pilihKanan = dekatKanan && !pilihKiri
        val pilihAtas = dekatAtas && (!dekatBawah || abs(y - kotak.atas) <= abs(y - kotak.bawah))
        val pilihBawah = dekatBawah && !pilihAtas

        val dalamRentangX = x in kotak.kiri..kotak.kanan
        val dalamRentangY = y in kotak.atas..kotak.bawah

        return when {
            pilihKiri && pilihAtas -> Pegangan.KIRI_ATAS
            pilihKanan && pilihAtas -> Pegangan.KANAN_ATAS
            pilihKiri && pilihBawah -> Pegangan.KIRI_BAWAH
            pilihKanan && pilihBawah -> Pegangan.KANAN_BAWAH
            pilihAtas && dalamRentangX -> Pegangan.ATAS
            pilihBawah && dalamRentangX -> Pegangan.BAWAH
            pilihKiri && dalamRentangY -> Pegangan.KIRI
            pilihKanan && dalamRentangY -> Pegangan.KANAN
            else -> null
        }
    }
}
