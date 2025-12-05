package com.cs407.settlersofmadison.ui.game

import kotlin.math.abs

data class Hex(val q: Int, val r: Int) {
    val s get() = -q - r
    fun neighbors(): List<Hex> = listOf(
        Hex(+1, 0), Hex(+1, -1), Hex(0, -1),
        Hex(-1, 0), Hex(-1, +1), Hex(0, +1)
    ).map { d -> Hex(q + d.q, r + d.r) }
    fun distanceTo(other: Hex): Int {
        val dq = q - other.q
        val dr = r - other.r
        val ds = s - other.s
        return (abs(dq) + abs(dr) + abs(ds)) / 2
    }
}