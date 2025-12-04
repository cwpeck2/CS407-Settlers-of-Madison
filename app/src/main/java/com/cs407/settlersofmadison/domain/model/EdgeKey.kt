package com.cs407.settlersofmadison.domain.model


/**
 * Uniquely identifies an edge on the hex grid.
 *
 * edge is 0..5 around the hex.
 */
data class EdgeKey(
    val q: Int,
    val r: Int,
    val edge: Int // 0..5
)