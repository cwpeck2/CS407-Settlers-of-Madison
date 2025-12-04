package com.cs407.settlersofmadison.domain.engine

import androidx.compose.ui.geometry.Offset
import com.cs407.settlersofmadison.domain.model.HexCoord
import com.cs407.settlersofmadison.domain.model.Pixel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geometry helpers for projecting hex coordinates to screen space.
 */

/**
 * Convert axial hex coordinate [c] to pixel coordinate, using hex "size" S.
 *
 * Uses a pointy-top hex layout.
 */
fun axialToPixel(c: HexCoord, S: Float): Pixel {
    val x = S * (1.5f * c.q)
    val y = S * (sqrt(3f) * (c.r + c.q / 2f))
    return Pixel(x, y)
}

/**
 * Returns the 6 corner points of a hex centered at ([cx], [cy]) with size [S].
 */
fun hexCorners(cx: Float, cy: Float, S: Float): List<Offset> =
    (0 until 6).map { i ->
        val ang = (60.0 * i) * (PI / 180.0)
        Offset(
            (cx + S * cos(ang)).toFloat(),
            (cy + S * sin(ang)).toFloat()
        )
    }