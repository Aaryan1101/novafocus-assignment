package com.novafocus.alphabetlauncher

import kotlin.math.exp
import kotlin.math.roundToInt

const val AlphabetSize = 26

fun safeAlphabetSpacing(height: Float, markerExtent: Float, preferredSpacing: Float = Float.POSITIVE_INFINITY): Float =
    minOf(
        ((height - markerExtent * 2f) / 27f).coerceAtLeast(0f),
        preferredSpacing.coerceAtLeast(0f)
    )

fun groupKey(label: String): Char? = label.firstOrNull()?.uppercaseChar()
    ?.takeIf { it in 'A'..'Z' }

fun sortLabels(labels: List<String>): List<String> = labels.sortedWith(String.CASE_INSENSITIVE_ORDER)

fun letterIndex(y: Float, firstCenter: Float, spacing: Float): Int {
    if (spacing <= 0f) return 0
    return ((y - firstCenter) / spacing).roundToInt().coerceIn(0, AlphabetSize - 1)
}

fun sidebarIndex(y: Float, firstLetterCenter: Float, spacing: Float): Int {
    if (spacing <= 0f) return 0
    return ((y - firstLetterCenter) / spacing).roundToInt().coerceIn(-1, AlphabetSize - 1)
}

fun letterCenter(index: Int, firstCenter: Float, spacing: Float): Float =
    firstCenter + index.coerceIn(0, AlphabetSize - 1) * spacing

fun curveDisplacement(letterCenter: Float, fingerY: Float, spacing: Float, maxPull: Float): Float {
    if (spacing <= 0f || maxPull <= 0f) return 0f
    val distance = (letterCenter - fingerY) / (spacing * 2.2f)
    return -maxPull * exp(-(distance * distance) / 2f)
}
