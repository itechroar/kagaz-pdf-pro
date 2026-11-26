package com.kagazsoftware.pdf

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Pads this view by the system bars, cutout and keyboard so edge-to-edge content is never covered. */
fun View.applySystemInsetsPadding() {
    val l = paddingLeft; val t = paddingTop; val r = paddingRight; val b = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val i = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
        )
        v.setPadding(l + i.left, t + i.top, r + i.right, b + i.bottom)
        WindowInsetsCompat.CONSUMED
    }
}
