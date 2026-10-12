package io.github.halilozel1903.deezer

import java.util.Locale

/** Small formatting helpers for track lengths and fan counts. */
public object DeezerFormat {
    /** [seconds] as `m:ss`, or `h:mm:ss` from one hour. Negative values count as 0. */
    public fun duration(seconds: Int): String {
        val total = seconds.coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%d:%02d".format(Locale.ROOT, m, s)
    }

    /** A compact count: `950`, `12.4K`, `3.1M`. */
    public fun compact(count: Long): String = when {
        count < 1_000 -> count.toString()
        count < 1_000_000 -> scaled(count, 1_000.0, "K")
        count < 1_000_000_000 -> scaled(count, 1_000_000.0, "M")
        else -> scaled(count, 1_000_000_000.0, "B")
    }

    /** `"1.2M fans"`, `"1 fan"`. */
    public fun fans(count: Long): String = if (count == 1L) "1 fan" else "${compact(count)} fans"

    private fun scaled(count: Long, unit: Double, suffix: String): String {
        val value = count / unit
        val text = if (value >= 100) "%.0f".format(Locale.ROOT, value) else "%.1f".format(Locale.ROOT, value)
        return text.removeSuffix(".0") + suffix
    }
}
