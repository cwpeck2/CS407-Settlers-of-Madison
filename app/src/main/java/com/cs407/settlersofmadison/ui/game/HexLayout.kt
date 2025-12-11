package com.cs407.settlersofmadison.ui.game

import androidx.compose.ui.geometry.Offset
import kotlin.math.*

object HexLayout {
    const val SIZE = 90f
    private val SQRT3 = sqrt(3f)
    private data class FractionalHex(val q: Float, val r: Float, val s: Float)


    fun hexToPixel(hex: Hex): Offset {
        val x = (SQRT3 * hex.q + SQRT3 / 2f * hex.r) * SIZE
        val y = (3f / 2f * hex.r) * SIZE
        return Offset(x, y)
    }

    fun pixelToHex(p: Offset): Hex {

        val x = p.x / SIZE
        val y = p.y / SIZE




        val qf = (SQRT3 / 3f * x - 1f / 3f * y)
        val rf = (2f / 3f * y)
        val sf = -qf - rf

        return hexRound(FractionalHex(qf, rf, sf))
    }

    private fun hexRound(h: FractionalHex): Hex {
        var q = h.q.roundToInt()
        var r = h.r.roundToInt()
        var s = h.s.roundToInt()

        val qDiff = abs(q - h.q)
        val rDiff = abs(r - h.r)
        val sDiff = abs(s - h.s)

        if (qDiff > rDiff && qDiff > sDiff) {
            q = -r - s
        } else if (rDiff > sDiff) {
            r = -q - s
        } else {
            s = -q - r
        }

        return Hex(q, r)
    }
}
