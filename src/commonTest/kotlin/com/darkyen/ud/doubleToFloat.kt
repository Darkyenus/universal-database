package com.darkyen.ud

/** Convert [Double] to [Float]. Same as [Double.toFloat] but actually works in JS backend.
 * See https://youtrack.jetbrains.com/issue/KT-24975/Enforce-range-of-Float-type-in-JS
 * and https://youtrack.jetbrains.com/issue/KT-35422/Fix-IntUIntDouble.toFloat-in-K-JS */
expect fun doubleToFloat(v: Double): Float