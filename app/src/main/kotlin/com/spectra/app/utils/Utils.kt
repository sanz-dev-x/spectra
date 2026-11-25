/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.utils

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.edit
import com.spectra.app.R

fun getArtistSeparator(context: Context): String = " ${context.getString(R.string.and)} "

fun <T> List<T>.joinToArtistString(
    conjunction: String,
    transform: (T) -> String,
): String = when (size) {
    0 -> ""
    1 -> transform(this[0])
    2 -> "${transform(this[0])}$conjunction${transform(this[1])}"
    else -> dropLast(1).joinToString(", ") { transform(it) } + "$conjunction${transform(last())}"
}

fun reportException(throwable: Throwable) {
    throwable.printStackTrace()
}
