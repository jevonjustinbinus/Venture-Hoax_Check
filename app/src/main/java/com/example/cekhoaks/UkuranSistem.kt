package com.example.cekhoaks

import android.annotation.SuppressLint
import android.content.Context

/**
 * Ukuran milik sistem Android, misalnya "status_bar_height" atau "navigation_bar_height".
 * Dipakai sebagai cadangan di tempat yang belum punya API resmi (Android 10 ke bawah di luar
 * Activity) atau saat sistem tidak mengirim insets ke jendela overlay. Hasilnya 0 jika tidak ada.
 */
@SuppressLint("DiscouragedApi", "InternalInsetResource")
internal fun Context.dimenSistem(nama: String): Int {
    val id = resources.getIdentifier(nama, "dimen", "android")
    return if (id > 0) resources.getDimensionPixelSize(id) else 0
}
