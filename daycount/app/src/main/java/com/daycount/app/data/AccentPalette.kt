package com.daycount.app.data

/**
 * Shared accent palette. Index 0 is the neutral ink accent, the rest are muted
 * hues that suit the minimal numeric look.
 */
object AccentPalette {
    val colors: List<Int> = listOf(
        0xFF1B1B18.toInt(),
        0xFF4F5BD5.toInt(),
        0xFFE4572E.toInt(),
        0xFF2A9D8F.toInt(),
        0xFFD9A32E.toInt(),
        0xFFD45079.toInt()
    )

    val size: Int get() = colors.size

    fun color(index: Int, isDark: Boolean): Int {
        val safeIndex = ((index % size) + size) % size
        if (safeIndex == 0 && isDark) return 0xFFF3F3EE.toInt()
        return colors[safeIndex]
    }
}
