package com.omarsalama58.snapdoc.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.omarsalama58.snapdoc.imaging.Filter
import java.io.File

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("snapdoc", Context.MODE_PRIVATE)

    private val _pdfQuality = mutableIntStateOf(sp.getInt("pdfQuality", 85))
    var pdfQuality: Int
        get() = _pdfQuality.intValue
        set(v) {
            _pdfQuality.intValue = v
            sp.edit().putInt("pdfQuality", v).apply()
        }

    private val _a4 = mutableStateOf(sp.getBoolean("a4", true))
    var a4: Boolean
        get() = _a4.value
        set(v) {
            _a4.value = v
            sp.edit().putBoolean("a4", v).apply()
        }

    /** Index into the Filter enum applied automatically to new scans. */
    private val _defaultFilter = mutableIntStateOf(sp.getInt("defaultFilter3", Filter.LIGHTEN.ordinal))
    var defaultFilter: Int
        get() = _defaultFilter.intValue
        set(v) {
            _defaultFilter.intValue = v
            sp.edit().putInt("defaultFilter3", v).apply()
        }
}

/** Saved signatures (transparent PNGs) used by the editor. */
object Signatures {
    private fun dir(ctx: Context) = File(ctx.filesDir, "signatures").also { it.mkdirs() }

    fun list(ctx: Context): List<File> =
        (dir(ctx).listFiles { f -> f.extension == "png" } ?: emptyArray()).sortedByDescending { it.lastModified() }

    fun save(ctx: Context, bmp: android.graphics.Bitmap): File {
        val f = File(dir(ctx), "sig_${System.currentTimeMillis()}.png")
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }
}
