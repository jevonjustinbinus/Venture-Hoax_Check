package com.example.cekhoaks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeometriSeleksiTest {

    private val delta = 0.001f

    private fun assertKotak(harap: Kotak, hasil: Kotak) {
        assertEquals("kiri", harap.kiri, hasil.kiri, delta)
        assertEquals("atas", harap.atas, hasil.atas, delta)
        assertEquals("kanan", harap.kanan, hasil.kanan, delta)
        assertEquals("bawah", harap.bawah, hasil.bawah, delta)
    }

    // --- areaGambar ---

    @Test
    fun areaGambar_ukuranSama_menutupiSeluruhView() {
        assertKotak(Kotak(0f, 0f, 1080f, 2400f), GeometriSeleksi.areaGambar(1080, 2400, 1080, 2400))
    }

    @Test
    fun areaGambar_bitmapSetengahResolusi_diperbesarDuaKali() {
        assertKotak(Kotak(0f, 0f, 1080f, 2400f), GeometriSeleksi.areaGambar(1080, 2400, 540, 1200))
    }

    @Test
    fun areaGambar_rasioBerbeda_diletakkanDiTengahDenganOffset() {
        // Bitmap 1000x2000 di view 1080x2400: skala min(1.08, 1.2) = 1.08 -> 1080x2160, sisa 240 dibagi dua.
        assertKotak(Kotak(0f, 120f, 1080f, 2280f), GeometriSeleksi.areaGambar(1080, 2400, 1000, 2000))
    }

    // --- petakanKeBitmap ---

    @Test
    fun petakan_satuBandingSatu_koordinatSama() {
        val area = GeometriSeleksi.areaGambar(1080, 2400, 1080, 2400)
        val hasil = GeometriSeleksi.petakanKeBitmap(Kotak(100f, 200f, 500f, 800f), area, 1080, 2400)
        assertEquals(KotakPiksel(100, 200, 400, 600), hasil)
    }

    @Test
    fun petakan_pojokKiriAtasDanKananBawah_tidakBergeser() {
        val area = GeometriSeleksi.areaGambar(1080, 2400, 1080, 2400)
        assertEquals(KotakPiksel(0, 0, 300, 300), GeometriSeleksi.petakanKeBitmap(Kotak(0f, 0f, 300f, 300f), area, 1080, 2400))
        assertEquals(
            KotakPiksel(780, 2100, 300, 300),
            GeometriSeleksi.petakanKeBitmap(Kotak(780f, 2100f, 1080f, 2400f), area, 1080, 2400),
        )
    }

    @Test
    fun petakan_bitmapSetengahResolusi_dibagiDua() {
        val area = GeometriSeleksi.areaGambar(1080, 2400, 540, 1200)
        val hasil = GeometriSeleksi.petakanKeBitmap(Kotak(100f, 200f, 500f, 800f), area, 540, 1200)
        assertEquals(KotakPiksel(50, 100, 200, 300), hasil)
    }

    @Test
    fun petakan_denganOffset_offsetDikurangiLaluDibagiSkala() {
        val area = GeometriSeleksi.areaGambar(1080, 2400, 1000, 2000) // skala 1.08, atas 120
        val hasil = GeometriSeleksi.petakanKeBitmap(Kotak(108f, 228f, 540f, 1200f), area, 1000, 2000)
        assertEquals(KotakPiksel(100, 100, 400, 900), hasil)
    }

    @Test
    fun petakan_tepiPecahan_dibulatkanKeLuar() {
        val area = GeometriSeleksi.areaGambar(1080, 2400, 1080, 2400)
        val hasil = GeometriSeleksi.petakanKeBitmap(Kotak(10.6f, 20.2f, 110.2f, 120.7f), area, 1080, 2400)
        assertEquals(KotakPiksel(10, 20, 101, 101), hasil)
    }

    @Test
    fun petakan_galatFloatKecil_tidakMenambahSatuPiksel() {
        val area = GeometriSeleksi.areaGambar(1080, 2400, 1080, 2400)
        val hasil = GeometriSeleksi.petakanKeBitmap(Kotak(99.9999f, 200.0001f, 500.0001f, 799.9999f), area, 1080, 2400)
        assertEquals(KotakPiksel(100, 200, 400, 600), hasil)
    }

    @Test
    fun petakan_seleksiDiLuarGambar_dibatasiKeTepiBitmap() {
        val area = GeometriSeleksi.areaGambar(1080, 2400, 1000, 2000) // gambar dari y=120 sampai 2280
        val hasil = GeometriSeleksi.petakanKeBitmap(Kotak(-50f, 0f, 2000f, 2400f), area, 1000, 2000)
        assertEquals(KotakPiksel(0, 0, 1000, 2000), hasil)
    }

    @Test
    fun petakan_seluruhArea_menghasilkanBitmapUtuh() {
        val area = GeometriSeleksi.areaGambar(1080, 2400, 1080, 2400)
        assertEquals(KotakPiksel(0, 0, 1080, 2400), GeometriSeleksi.petakanKeBitmap(area, area, 1080, 2400))
    }

    @Test
    fun petakan_seleksiKosong_tetapMinimalSatuPiksel() {
        val area = GeometriSeleksi.areaGambar(1080, 2400, 1080, 2400)
        val hasil = GeometriSeleksi.petakanKeBitmap(Kotak(1080f, 2400f, 1080f, 2400f), area, 1080, 2400)
        assertEquals(KotakPiksel(1079, 2399, 1, 1), hasil)
    }

    // --- kotakAwal ---

    @Test
    fun kotakAwal_80PersenLebar40PersenTinggi_diTengah() {
        val hasil = GeometriSeleksi.kotakAwal(Kotak(0f, 0f, 1000f, 2000f), ukuranMin = 100f)
        assertKotak(Kotak(100f, 600f, 900f, 1400f), hasil)
    }

    @Test
    fun kotakAwal_areaKecil_tidakLebihKecilDariMinimum() {
        val hasil = GeometriSeleksi.kotakAwal(Kotak(0f, 0f, 200f, 200f), ukuranMin = 150f)
        assertKotak(Kotak(20f, 25f, 180f, 175f), hasil)
    }

    // --- geser ---

    private val batas = Kotak(0f, 0f, 1000f, 2000f)
    private val kotak = Kotak(100f, 100f, 400f, 500f)

    @Test
    fun geser_bebas_ukuranTetap() {
        assertKotak(Kotak(150f, 80f, 450f, 480f), GeometriSeleksi.geser(kotak, 50f, -20f, batas))
    }

    @Test
    fun geser_melewatiTepi_berhentiDiBatas() {
        assertKotak(Kotak(0f, 0f, 300f, 400f), GeometriSeleksi.geser(kotak, -500f, -500f, batas))
        assertKotak(Kotak(700f, 1600f, 1000f, 2000f), GeometriSeleksi.geser(kotak, 5000f, 5000f, batas))
    }

    // --- ubahUkuran ---

    @Test
    fun ubahUkuran_sudutKananBawah_hanyaTepiKananDanBawahBergerak() {
        val hasil = GeometriSeleksi.ubahUkuran(kotak, Pegangan.KANAN_BAWAH, 100f, 200f, batas, 96f)
        assertKotak(Kotak(100f, 100f, 500f, 700f), hasil)
    }

    @Test
    fun ubahUkuran_sudutKiriAtas_melewatiBatas_berhentiDiTepiLayar() {
        val hasil = GeometriSeleksi.ubahUkuran(kotak, Pegangan.KIRI_ATAS, -500f, -500f, batas, 96f)
        assertKotak(Kotak(0f, 0f, 400f, 500f), hasil)
    }

    @Test
    fun ubahUkuran_sudutKananAtasDanKiriBawah() {
        assertKotak(
            Kotak(100f, 50f, 450f, 500f),
            GeometriSeleksi.ubahUkuran(kotak, Pegangan.KANAN_ATAS, 50f, -50f, batas, 96f),
        )
        assertKotak(
            Kotak(50f, 100f, 400f, 550f),
            GeometriSeleksi.ubahUkuran(kotak, Pegangan.KIRI_BAWAH, -50f, 50f, batas, 96f),
        )
    }

    @Test
    fun ubahUkuran_sisi_hanyaSatuTepiBergerak() {
        assertKotak(Kotak(100f, 50f, 400f, 500f), GeometriSeleksi.ubahUkuran(kotak, Pegangan.ATAS, 999f, -50f, batas, 96f))
        assertKotak(Kotak(100f, 100f, 400f, 550f), GeometriSeleksi.ubahUkuran(kotak, Pegangan.BAWAH, 999f, 50f, batas, 96f))
        assertKotak(Kotak(50f, 100f, 400f, 500f), GeometriSeleksi.ubahUkuran(kotak, Pegangan.KIRI, -50f, 999f, batas, 96f))
        assertKotak(Kotak(100f, 100f, 450f, 500f), GeometriSeleksi.ubahUkuran(kotak, Pegangan.KANAN, 50f, 999f, batas, 96f))
    }

    @Test
    fun ubahUkuran_diperkecilBerlebihan_berhentiDiUkuranMinimum() {
        val hasil = GeometriSeleksi.ubahUkuran(kotak, Pegangan.KANAN_BAWAH, -1000f, -1000f, batas, 96f)
        assertKotak(Kotak(100f, 100f, 196f, 196f), hasil)
        val hasilKiri = GeometriSeleksi.ubahUkuran(kotak, Pegangan.KIRI_ATAS, 1000f, 1000f, batas, 96f)
        assertKotak(Kotak(304f, 404f, 400f, 500f), hasilKiri)
    }

    // --- kotakBaru ---

    @Test
    fun kotakBaru_diseretKeKiriAtas_tetapUrutanBenar() {
        val hasil = GeometriSeleksi.kotakBaru(500f, 600f, 200f, 100f, batas, 96f)
        assertKotak(Kotak(200f, 100f, 500f, 600f), hasil)
    }

    @Test
    fun kotakBaru_terlaluKecil_diperbesarKeArahJari() {
        assertKotak(Kotak(500f, 600f, 596f, 696f), GeometriSeleksi.kotakBaru(500f, 600f, 510f, 610f, batas, 96f))
        assertKotak(Kotak(404f, 504f, 500f, 600f), GeometriSeleksi.kotakBaru(500f, 600f, 490f, 590f, batas, 96f))
    }

    @Test
    fun kotakBaru_diTepiLayar_tetapDiDalamBatas() {
        val hasil = GeometriSeleksi.kotakBaru(990f, 1990f, 995f, 1995f, batas, 96f)
        assertKotak(Kotak(904f, 1904f, 1000f, 2000f), hasil)
        val keluar = GeometriSeleksi.kotakBaru(500f, 500f, -300f, 3000f, batas, 96f)
        assertKotak(Kotak(0f, 500f, 500f, 2000f), keluar)
    }

    // --- peganganDiTitik ---

    @Test
    fun pegangan_keempatSudut() {
        assertEquals(Pegangan.KIRI_ATAS, GeometriSeleksi.peganganDiTitik(kotak, 110f, 90f, 24f))
        assertEquals(Pegangan.KANAN_ATAS, GeometriSeleksi.peganganDiTitik(kotak, 420f, 110f, 24f))
        assertEquals(Pegangan.KIRI_BAWAH, GeometriSeleksi.peganganDiTitik(kotak, 80f, 520f, 24f))
        assertEquals(Pegangan.KANAN_BAWAH, GeometriSeleksi.peganganDiTitik(kotak, 400f, 500f, 24f))
    }

    @Test
    fun pegangan_keempatSisi() {
        assertEquals(Pegangan.ATAS, GeometriSeleksi.peganganDiTitik(kotak, 250f, 90f, 24f))
        assertEquals(Pegangan.BAWAH, GeometriSeleksi.peganganDiTitik(kotak, 250f, 515f, 24f))
        assertEquals(Pegangan.KIRI, GeometriSeleksi.peganganDiTitik(kotak, 115f, 300f, 24f))
        assertEquals(Pegangan.KANAN, GeometriSeleksi.peganganDiTitik(kotak, 385f, 300f, 24f))
    }

    @Test
    fun pegangan_tengahKotakAtauJauhDiLuar_null() {
        assertNull(GeometriSeleksi.peganganDiTitik(kotak, 250f, 300f, 24f))
        assertNull(GeometriSeleksi.peganganDiTitik(kotak, 800f, 1500f, 24f))
        // Sejajar sisi atas tetapi jauh di luar rentang horizontal kotak.
        assertNull(GeometriSeleksi.peganganDiTitik(kotak, 800f, 100f, 24f))
    }
}
