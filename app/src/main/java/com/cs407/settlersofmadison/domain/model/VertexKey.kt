package com.cs407.settlersofmadison.domain.model

/**
 * Uniquely identifies a vertex (intersection) on the hex grid.
 *
 * corner is 0..5 around the hex.
 */
data class VertexKey(
    val q: Int,
    val r: Int,
    val corner: Int // 0..5
)