package com.utilityhub.logic.image

import com.utilityhub.logic.Validation
import kotlin.math.floor
import kotlin.math.roundToInt

data class CropRect(val left: Int, val top: Int, val width: Int, val height: Int)

data class Rgb(val r: Int, val g: Int, val b: Int) {
    fun hex(): String = String.format("#%02X%02X%02X", r, g, b)
    override fun toString() = "${hex()}  rgb($r, $g, $b)"
}

data class ColorShare(val color: Rgb, val percent: Double)

data class SheetLayout(val landscape: Boolean, val columns: Int, val rows: Int) {
    val count: Int get() = columns * rows
}

object ImageGeometry {
    /** Largest crop of [aspectW]:[aspectH] inside [original]; offsets 0..100 slide the crop along the free axis. */
    fun aspectCrop(original: PixelSize, aspectW: Int, aspectH: Int, offsetX: Int, offsetY: Int): Validation<CropRect> {
        if (original.width <= 0 || original.height <= 0) return Validation.Invalid("The image has no valid size.")
        if (aspectW <= 0 || aspectH <= 0) return Validation.Invalid("The aspect ratio is invalid.")
        val ratio = aspectW.toDouble() / aspectH
        val (w, h) = if (original.width.toDouble() / original.height > ratio) {
            (original.height * ratio).roundToInt().coerceIn(1, original.width) to original.height
        } else {
            original.width to (original.width / ratio).roundToInt().coerceIn(1, original.height)
        }
        val left = ((original.width - w) * offsetX.coerceIn(0, 100) / 100.0).roundToInt()
        val top = ((original.height - h) * offsetY.coerceIn(0, 100) / 100.0).roundToInt()
        return Validation.Valid(CropRect(left, top, w, h))
    }

    fun mmToPixels(mm: Double, dpi: Int): Int = (mm / 25.4 * dpi).roundToInt()

    /** How many photos fit on a sheet; tries both paper orientations and returns the better one. */
    fun bestSheet(paperWmm: Double, paperHmm: Double, photoWmm: Double, photoHmm: Double, gapMm: Double, marginMm: Double): Validation<SheetLayout> {
        if (photoWmm <= 0 || photoHmm <= 0) return Validation.Invalid("The photo size is invalid.")
        fun fit(w: Double, h: Double, landscape: Boolean): SheetLayout {
            val cols = floor((w - 2 * marginMm + gapMm) / (photoWmm + gapMm)).toInt().coerceAtLeast(0)
            val rows = floor((h - 2 * marginMm + gapMm) / (photoHmm + gapMm)).toInt().coerceAtLeast(0)
            return SheetLayout(landscape, cols, rows)
        }
        val portrait = fit(paperWmm, paperHmm, false)
        val landscape = fit(paperHmm, paperWmm, true)
        val best = if (landscape.count > portrait.count) landscape else portrait
        return if (best.count == 0) Validation.Invalid("The photo does not fit on this paper size.") else Validation.Valid(best)
    }

    /** Most frequent distinct colours (4 bits per channel buckets, near-duplicates merged). Alpha-transparent pixels are ignored. */
    fun dominantColors(pixels: IntArray, count: Int): List<ColorShare> {
        val buckets = HashMap<Int, IntArray>() // key -> [n, sumR, sumG, sumB]
        var total = 0
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            val e = buckets.getOrPut(key) { IntArray(4) }
            e[0]++; e[1] += r; e[2] += g; e[3] += b
            total++
        }
        if (total == 0) return emptyList()
        val picked = ArrayList<Pair<Rgb, IntArray>>()   // colour + running pixel count
        for ((_, e) in buckets.entries.sortedByDescending { it.value[0] }) {
            val c = Rgb(e[1] / e[0], e[2] / e[0], e[3] / e[0])
            val existing = picked.firstOrNull { distance(it.first, c) < 48 }
            if (existing != null) { existing.second[0] += e[0]; continue }   // near-duplicate: merge its share
            if (picked.size < count) picked += c to intArrayOf(e[0])
        }
        return picked.sortedByDescending { it.second[0] }.map { ColorShare(it.first, it.second[0] * 100.0 / total) }
    }

    private fun distance(a: Rgb, b: Rgb): Double =
        Math.sqrt(((a.r - b.r) * (a.r - b.r) + (a.g - b.g) * (a.g - b.g) + (a.b - b.b) * (a.b - b.b)).toDouble())
}
