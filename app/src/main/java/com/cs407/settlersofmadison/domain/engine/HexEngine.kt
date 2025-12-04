package com.cs407.settlersofmadison.domain.engine

import com.cs407.settlersofmadison.domain.model.EdgeKey
import com.cs407.settlersofmadison.domain.model.HexCoord
import com.cs407.settlersofmadison.domain.model.VertexKey

/**
 * Core hex-grid logic: neighbor lookup and canonical vertex/edge keys.
 */

// Axial direction vectors (pointy-top hexes), in order 0..5.
private val DIRS = arrayOf(
    HexCoord(+1, 0),
    HexCoord(+1, -1),
    HexCoord(0, -1),
    HexCoord(-1, 0),
    HexCoord(-1, +1),
    HexCoord(0, +1)
)

/**
 * Returns the neighbor hex of [c] in direction [dir] (0..5).
 */
fun neighbor(c: HexCoord, dir: Int): HexCoord {
    val d = DIRS[dir]
    return HexCoord(c.q + d.q, c.r + d.r)
}

/**
 * Convert a local (coord, corner) pair into a canonical VertexKey so
 * the same intersection is always represented by a single key.
 */
fun canonicalVertex(c: HexCoord, corner: Int): VertexKey =
    if (corner in 0..2) {
        VertexKey(c.q, c.r, corner)
    } else {
        val n = neighbor(c, corner)
        VertexKey(n.q, n.r, (corner + 3) % 6)
    }

/**
 * Convert a local (coord, edge) pair into a canonical EdgeKey so
 * the same edge is always represented by a single key.
 */
fun canonicalEdge(c: HexCoord, edge: Int): EdgeKey =
    if (edge in 0..2) {
        EdgeKey(c.q, c.r, edge)
    } else {
        val n = neighbor(c, edge)
        EdgeKey(n.q, n.r, (edge + 3) % 6)
    }