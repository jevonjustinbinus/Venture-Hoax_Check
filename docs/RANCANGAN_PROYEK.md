# Rancangan Proyek: Aplikasi Cek Hoaks dengan Tombol Mengambang

> Nama produk masih sementara. Dokumen ini menjadi acuan untuk seluruh tahap pengerjaan. Jika ada keputusan yang berubah, perbarui dokumen ini terlebih dahulu.

## 1. Latar belakang

Proyek ini dibuat untuk mata kuliah Venture Creation dengan tema: masyarakat sering menemukan informasi menarik di media sosial, tetapi kesulitan mengetahui apakah informasi tersebut benar. Pendekatannya menggabungkan technopreneur dan edupreneur: teknologi membantu pengguna mengecek informasi, sekaligus mengajari mereka mengenali ciri-ciri hoaks sendiri.

Produk akan dipamerkan dalam showcase sekitar 7-8 minggu dari awal pengerjaan dan dikerjakan oleh satu developer. Dosen menyetujui penggunaan model open source seperti Qwen dan menyarankan bentuk aplikasi HP dengan tombol mengambang.

## 2. Prinsip produk

1. **Pemandu, bukan hakim.** Sistem menunjukkan ciri-ciri yang mencurigakan beserta buktinya. Sistem tidak pernah menyatakan sebuah informasi "benar" hanya berdasarkan pendapat AI.
2. **Bukti di atas tebakan.** Kesimpulan paling kuat hanya diberikan jika klaim cocok dengan artikel cek fakta yang sudah terbit.
3. **Belajar sambil memakai.** Pengguna diajak menilai terlebih dahulu, lalu penilaiannya dibandingkan dengan temuan sistem.
4. **Satu ketukan dari tempat keraguan muncul.** Pengguna tidak perlu berpindah aplikasi untuk mengecek.
5. **Privasi.** Hanya area yang dipilih pengguna yang dikirim, dan gambar tidak disimpan.

## 3. Pengguna dan model bisnis

Pengguna mencakup semua kalangan, dari pelajar, mahasiswa, sampai orang tua. Karena itu UI harus jelas, tombol berukuran besar, teks mudah dibaca, dan bahasa yang dipakai adalah Bahasa Indonesia semi-formal.

Model bisnis versi awal: gratis untuk pengguna individu, dengan segmen pembayar pertama berupa institusi pendidikan (sekolah dan kampus) yang membayar modul literasi digital dan dashboard perkembangan kemampuan siswa.

Platform MVP hanya Android. iOS tidak mengizinkan tombol di atas aplikasi lain maupun screenshot di latar belakang, sehingga masuk roadmap dalam bentuk share sheet.

## 4. Alur pengguna (MVP lengkap)

1. Pengguna membuka aplikasi, memberikan izin yang dibutuhkan, lalu mengaktifkan tombol mengambang.
2. Saat membaca konten di aplikasi lain dan merasa ragu, pengguna mengetuk tombol mengambang.
3. Tombol disembunyikan sesaat dan layar diambil.
4. Layar pilih area muncul: tampilan layar dibekukan dengan lapisan gelap, pengguna menggeser dan mengatur ukuran kotak seleksi, lalu menekan "Cek Sekarang" atau "Batal".
5. Hanya potongan area yang dikirim ke server. Screenshot penuh langsung dibuang dari memori.
6. Selama server menganalisis, kartu kecil menampilkan pertanyaan sekali ketuk: "Menurutmu informasi ini: Percaya / Ragu / Tidak percaya?" Waktu tunggu analisis dimanfaatkan untuk bagian edukasi ini.
7. Kartu hasil singkat muncul di atas layar: tingkat indikasi dan ciri utama yang ditemukan.
8. Tombol "Pelajari lebih lanjut" membuka aplikasi dan menampilkan rincian setiap ciri beserta bukti dan penjelasannya, artikel cek fakta yang cocok, serta perbandingan dengan penilaian pengguna.
9. Di dalam aplikasi juga tersedia **mode Latih**: simulasi feed berisi hoaks yang sudah dibantah secara resmi, lengkap dengan skor dan penjelasan.

## 5. Arsitektur sistem

```
[HP Android]                                    [Laptop developer, GPU NVIDIA 6 GB]
Tombol mengambang (overlay)                      FastAPI
  -> ambil layar (MediaProjection)                 -> Qwen-VL via Ollama: baca teks, ekstrak klaim,
  -> pilih area (overlay crop)                        deteksi ciri dari daftar tetap (output JSON)
  -> kirim potongan gambar (HTTP) ---------------> -> pencocokan klaim dengan database cek fakta
                                                      (embedding + pgvector)
                                                   -> validasi ciri: aturan + classifier IndoBERT
  <- kartu hasil (overlay) <---------------------- -> logika tingkat indikasi (kode sendiri) -> JSON
```

Koneksi HP ke laptop: saat pengembangan, keduanya berada di jaringan Wi-Fi yang sama. Saat showcase, gunakan hotspot sendiri atau tunnel (ngrok atau Cloudflare Tunnel), jangan bergantung pada Wi-Fi kampus.

## 6. Komponen aplikasi Android

| Komponen | Tanggung jawab | Tahap |
|---|---|---|
| `MainActivity` (Compose) | Status izin, mengaktifkan dan mematikan tombol; nantinya rincian hasil dan mode Latih | 1, 7 |
| `FloatingButtonService` (foreground service) | Memasang dan melepas tombol overlay; nantinya menyimpan sesi MediaProjection | 1, 2 |
| Pengambil layar | MediaProjection, VirtualDisplay, ImageReader untuk menghasilkan Bitmap layar penuh | 2 |
| Overlay pilih area | View layar penuh berisi gambar beku, lapisan gelap, kotak seleksi, tombol Batal dan Cek Sekarang | 3 |
| Klien API | Mengirim potongan gambar ke server (multipart) dan menerima JSON | 4 |
| Kartu status dan hasil | Overlay kartu kecil: status analisis, pertanyaan sikap, hasil singkat | 4, 6 |

## 7. Komponen server (mulai tahap 4)

- **Framework:** Python FastAPI, satu endpoint utama untuk menerima gambar.
- **Model vision:** Qwen-VL ukuran kecil yang dikuantisasi (misalnya Qwen2.5-VL 3B atau Qwen3-VL 4B), dijalankan dengan Ollama. Pilih yang muat di VRAM 6 GB dan cek ketersediaannya saat tahap 5. Qwen hanya bertugas membaca teks, merangkum klaim, dan menandai ciri dari daftar tetap. Qwen tidak menentukan kesimpulan akhir.
- **Pencocokan cek fakta:** embedding `multilingual-e5-base` (wajib memakai awalan `query: ` dan `passage: `), disimpan di PostgreSQL dengan pgvector. Cadangan: `multilingual-e5-small` jika lambat, `bge-m3` jika kurang akurat.
- **Validator ciri:** aturan (regex dan daftar kata kunci) ditambah classifier multi-label hasil fine-tuning sendiri (IndoBERTweet untuk bahasa media sosial atau IndoBERT base). Bila perlu, diekspor ke ONNX int8.
- **OCR pembanding (opsional):** PaddleOCR untuk memeriksa teks yang dibaca Qwen.
- **Penjelasan ciri:** teks template yang ditulis sendiri untuk setiap ciri, bukan dibuat oleh LLM.
- **Privasi:** gambar diproses di memori dan tidak disimpan maupun dicatat di log.

### Draf format respons

Format final ditetapkan di tahap 5.

```json
{
  "tingkat_indikasi": "perlu_hati_hati",
  "teks_terbaca": "VIRAL! Air keran sebabkan penyakit misterius... SEBARKAN!",
  "klaim_utama": "Air keran menyebabkan penyakit misterius dalam semalam",
  "ciri_terdeteksi": [
    {
      "id": "ajakan_menyebarkan",
      "bukti": "SEBARKAN!",
      "sumber_deteksi": ["aturan", "qwen"],
      "keyakinan": "tinggi"
    }
  ],
  "artikel_cek_fakta": [
    {
      "judul": "Judul artikel cek fakta",
      "sumber": "turnbackhoax.id",
      "url": "https://...",
      "skor_kemiripan": 0.82
    }
  ],
  "catatan": "Hasil ini bukan jaminan benar atau salah."
}
```

## 8. Daftar ciri hoaks (draf)

Daftar ini dipakai bersama oleh sistem dan pertanyaan kepada pengguna, sehingga hasilnya bisa dibandingkan. Finalisasi beserta teks penjelasannya dilakukan di tahap 5.

| id | Ciri | Deteksi utama |
|---|---|---|
| `ajakan_menyebarkan` | Ajakan menyebarkan ("sebarkan", "viralkan") | Aturan |
| `desakan_waktu` | Desakan waktu ("sebelum dihapus", "segera") | Aturan + Qwen |
| `kapital_tanda_seru` | Huruf kapital dan tanda seru berlebihan | Aturan |
| `link_mencurigakan` | Tautan pemendek atau tautan tidak jelas | Aturan |
| `sumber_tidak_jelas` | Tidak menyebut sumber yang bisa dicek | Qwen + aturan |
| `judul_clickbait` | Judul clickbait | Classifier (CLICK-ID) + Qwen |
| `bahasa_provokatif` | Bahasa provokatif, memancing emosi atau panik | Classifier + Qwen |
| `pernah_dibantah` | Klaim serupa sudah dibantah media cek fakta | Pencocokan embedding |

## 9. Tingkat indikasi (draf logika)

1. Ada artikel cek fakta dengan kemiripan di atas ambang, dan artikel tersebut menyatakan klaimnya salah atau hoaks: **Indikasi kuat hoaks**.
2. Tidak ada artikel yang cocok, tetapi ditemukan beberapa ciri berkeyakinan tinggi: **Perlu hati-hati**.
3. Selain itu: **Tidak ditemukan indikasi hoaks**, disertai pesan bahwa ini bukan jaminan kebenaran dan saran untuk mengecek sumbernya.

Keyakinan ciri dianggap tinggi jika terdeteksi oleh aturan, atau oleh minimal dua sumber deteksi. Nilai ambang dan jumlah ciri ditentukan dengan data uji di tahap 5. Perbandingan antara penilaian pengguna dan temuan sistem cukup memakai logika perbandingan himpunan, tanpa LLM.

## 10. Data dan lisensi (untuk tahap 5)

| Data | Kegunaan | Catatan lisensi |
|---|---|---|
| API Yudistira TurnBackHoax (Mafindo) | Database artikel cek fakta | Cek status API dan minta izin kepada Mafindo |
| CLICK-ID (15.000 judul berlabel) | Latih deteksi clickbait | CC BY 4.0, boleh komersial dengan atribusi |
| Dataset Ibrohim & Budi 2019 (tweet ujaran kebencian) | Latih deteksi bahasa provokatif | CC BY-NC-SA 4.0, hanya untuk prototipe; versi komersial perlu izin atau data sendiri |
| 300-500 narasi hoaks berlabel sendiri | Data uji realistis, tambahan data latih, konten mode Latih | Diambil dari artikel cek fakta yang sudah terbit |

## 11. Spesifikasi visual (dari prototipe)

- **Warna:** utama `#0F766E`, aksen `#F97316`, teks utama `#0F172A`, teks sekunder `#64748B`.
- **Tombol mengambang:** lingkaran 56dp berwarna utama dengan opacity sekitar 93%, ikon perisai centang putih 26dp, bayangan halus, menempel di tepi kanan dengan jarak 14dp, posisi awal sekitar 40% tinggi layar.
- **Layar pilih area:** lapisan gelap `#080F1A` dengan opacity sekitar 68%, kotak seleksi bergaris putih 3dp dengan sudut membulat 8dp, pegangan di empat sudut (titik putih bergaris warna utama, area sentuh 44dp), kotak instruksi gelap di atas tombol, tombol "Batal" (sekunder) dan "Cek Sekarang" (warna aksen) setinggi 52dp, ukuran minimum kotak 96dp.
- **Kartu status:** latar putih, sudut membulat 20dp, thumbnail potongan 72dp, indikator loading, teks "Sedang menganalisis...", tombol "Tutup".
- **Font:** Plus Jakarta Sans; untuk sementara boleh memakai sans-serif bawaan sistem.
- **Prototipe interaktif:** https://claude.ai/artifact/WQgUGxKrXNEvJrbqK3w7kj (hanya bisa dibuka oleh pemilik akun; jika tidak bisa diakses, gunakan spesifikasi di atas).

## 12. Proyek referensi open source

| Repositori | Lisensi | Dipakai untuk | Tahap | Catatan |
|---|---|---|---|---|
| https://github.com/ervareza/screen-translator | MIT | Overlay `TYPE_APPLICATION_OVERLAY`, notifikasi foreground service dengan aksi berhenti, MediaProjection, perbaikan Android 14/15 | 1-2 | Paling mirip konsepnya, tetapi pemicu tangkap layarnya lewat layanan aksesibilitas (`InactivityAccessibilityService`); bagian itu jangan diikuti |
| https://github.com/SavinduK/SnapCrop | MIT menurut README (file `LICENSE` tidak disertakan); diperlakukan sebagai MIT | Pola pasang/lepas overlay dan lapisan crop transparan | 1, 3 | README mencantumkan izin tampil di atas aplikasi lain (`SYSTEM_ALERT_WINDOW`), tetapi berdasarkan kodenya overlay dipasang dengan `TYPE_ACCESSIBILITY_OVERLAY` dari layanan aksesibilitas, dan pemicunya juga lewat layanan aksesibilitas. Bagian itu jangan diikuti |
| https://github.com/EdwardSierra/ScreenshotApp | GPLv3 | Alur ambil layar penuh lalu crop, penyimpanan izin screen capture | 2-3 | Pemicunya Quick Settings tile. Jangan salin kode kecuali aplikasi dirilis sebagai GPLv3 (diputuskan di awal tahap 2) |
| https://github.com/cvzi/ScreenshotTile | GPLv3 | Referensi matang: screenshot area tertentu, MediaProjection di berbagai versi Android | 2-3 | Jangan salin kode kecuali aplikasi dirilis sebagai GPLv3. Tombol mengambangnya bergantung pada layanan aksesibilitas (`TYPE_ACCESSIBILITY_OVERLAY`); jangan ikuti pendekatan itu |
| https://github.com/mtsahakis/MediaProjectionDemo | "Do whatever you want License." (tidak standar) | Dasar MediaProjection API | 2 | Kode lama, hanya untuk memahami konsep |

Proyek ini memasang tombol mengambang dengan `SYSTEM_ALERT_WINDOW` + `TYPE_APPLICATION_OVERLAY` dari foreground service biasa, cara yang tidak dipakai oleh referensi mana pun. Rincian file dan kode yang diambil ada di `docs/REFERENSI.md`. Kebijakan penggunaan kode referensi dijelaskan di `CLAUDE.md`.

## 13. Tahapan pengerjaan

| Tahap | Cakupan | Selesai jika | Perkiraan |
|---|---|---|---|
| 1 | Tombol mengambang overlay: izin tampil di atas aplikasi lain, foreground service, tombol tampil di aplikasi lain, ketukan tanpa aksi | Tombol tampil stabil di atas aplikasi lain dan bisa diaktifkan serta dimatikan | Minggu 1 |
| 2 | Tombol bisa digeser vertikal di tepi kanan; ketukan mengambil layar dengan MediaProjection; tombol disembunyikan saat pengambilan | Bitmap layar penuh tersedia di memori | Minggu 2 |
| 3 | Overlay pilih area sesuai prototipe | Bitmap potongan sesuai kotak seleksi; screenshot penuh dibuang | Minggu 2-3 |
| 4 | Server FastAPI dummy dan kartu status | HP mengirim potongan gambar dan menerima respons dummy dari laptop | Minggu 3 |
| 5 | Pipeline analisis di server | Respons JSON sesuai format final, diuji dengan data uji | Minggu 3-5 |
| 6 | Pertanyaan sikap saat menunggu dan kartu hasil singkat | Alur dari ketukan sampai hasil berjalan utuh | Minggu 5-6 |
| 7 | Aplikasi utama: rincian hasil, perbandingan penilaian, mode Latih | Mode Latih berisi minimal 10 konten | Minggu 6-7 |
| 8 | Uji di beberapa HP, koneksi showcase, materi demo | Demo lancar tanpa bergantung pada internet kampus | Minggu 7-8 |

Pengumpulan data dan fine-tuning classifier dikerjakan paralel mulai minggu 2 di luar proyek Android.

### Catatan teknis untuk tahap berikutnya

- **Tahap 2 (Android 14 ke atas):** persetujuan screen capture harus diminta untuk setiap sesi; intent hasil persetujuan tidak boleh dipakai ulang; `MediaProjection.Callback` wajib didaftarkan sebelum `createVirtualDisplay`; `createVirtualDisplay` tidak boleh dipanggil lebih dari sekali pada objek MediaProjection yang sama; foreground service bertipe `mediaProjection` dimulai setelah persetujuan diperoleh dan sebelum `getMediaProjection`. Di dialog persetujuan, pengguna bisa memilih "satu aplikasi" atau "seluruh layar", jadi arahkan untuk memilih seluruh layar.
- **Tahap 3:** overlay pilih area adalah jendela layar penuh, jadi harus menangani sentuhan sendiri dan dilepas dengan benar saat Batal maupun Cek Sekarang.
- **Umum:** sistem atau aplikasi tertentu (misalnya halaman pengaturan izin dan aplikasi perbankan) dapat menyembunyikan overlay. Ini perilaku normal.

## 14. Di luar cakupan MVP (roadmap)

- iOS melalui share sheet.
- Video: transkripsi audio dengan Whisper, lalu dianalisis sebagai teks.
- Pencocokan gambar dengan database hoaks memakai CLIP.
- Panduan reverse image search yang lebih lengkap.
- Dashboard institusi untuk sekolah dan kampus.
- Deteksi gambar editan atau buatan AI tidak dijanjikan, karena detektor yang ada belum cukup andal; topik ini cukup diangkat sebagai materi edukasi di mode Latih.
