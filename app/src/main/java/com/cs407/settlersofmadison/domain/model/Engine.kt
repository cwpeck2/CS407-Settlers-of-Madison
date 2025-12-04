package com.cs407.settlersofmadison.domain.model

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Basic Catan-style resources for now.
// You can rename/re-theme these later (e.g., MADISON_CHEESE, etc.)
enum class Resource {
    WOOD,
    BRICK,
    SHEEP,
    WHEAT,
    ORE
}

// Axial hex coordinate (pointy-top)
data class HexCoord(val q: Int, val r: Int)

// A single hex tile on the board
data class Tile(
    val coord: HexCoord,
    val resource: Resource,
    val number: Int
)

// Intersection (vertex) and edge keys, in canonical form.
data class VertexKey(val q: Int, val r: Int, val corner: Int) // corner in 0..5
data class EdgeKey(val q: Int, val r: Int, val edge: Int)     // edge in 0..5

// Axial neighbor directions (pointy-top layout)
private val DIRS = arrayOf(
    HexCoord(+1, 0),  // 0
    HexCoord(+1,-1),  // 1
    HexCoord(0,-1),   // 2
    HexCoord(-1, 0),  // 3
    HexCoord(-1,+1),  // 4
    HexCoord(0,+1)    // 5
)

fun neighbor(c: HexCoord, dir: Int): HexCoord =
    HexCoord(c.q + DIRS[dir].q, c.r + DIRS[dir].r)

/**
 * Canonicalize a vertex: the same physical corner shared by neighboring tiles
 * always maps to the same VertexKey.
 */

// --- Helper for vertex canonicalization ---
private fun vertexTriple(c: HexCoord, corner: Int): List<HexCoord> {
    // The three hexes that meet at this vertex:
    //  - the tile itself
    //  - neighbor in direction `corner`
    //  - neighbor in direction `corner - 1` (i.e., (corner + 5) % 6)
    val c0 = c
    val c1 = neighbor(c, corner)
    val c2 = neighbor(c, (corner + 5) % 6)

    // Sort so that (A,B,C) is the same no matter which tile we start from
    return listOf(c0, c1, c2).sortedWith(
        compareBy<HexCoord> { it.q }.thenBy { it.r }
    )
}

/**
 * Canonical vertex id: any physical corner shared by up to 3 hexes
 * always maps to the same (q,r,corner), regardless of which tile/corner
 * we start from.
 */
fun canonicalVertex(c: HexCoord, corner: Int): VertexKey {
    val triple = vertexTriple(c, corner)
    val canonicalCoord = triple[0] // smallest coord (lexicographically)

    // Find which local corner on canonicalCoord yields the same triple
    val canonicalCorner = (0 until 6).first { k ->
        vertexTriple(canonicalCoord, k) == triple
    }

    return VertexKey(canonicalCoord.q, canonicalCoord.r, canonicalCorner)
}

/**
 * Same idea for edges.
 */
fun canonicalEdge(c: HexCoord, edge: Int): EdgeKey =
    if (edge in 0..2) {
        EdgeKey(c.q, c.r, edge)
    } else {
        val n = neighbor(c, edge)
        EdgeKey(n.q, n.r, (edge + 3) % 6)
    }

// Pixel helpers for rendering (not strictly needed by HexBoard, but handy)
data class Pixel(val x: Float, val y: Float)

fun axialToPixel(c: HexCoord, S: Float): Pixel {
    val x = S * (1.5f * c.q)
    val y = S * (sqrt(3f) * (c.r + c.q / 2f))
    return Pixel(x, y)
}

fun hexCorners(cx: Float, cy: Float, S: Float): List<Offset> =
    (0 until 6).map { i ->
        val ang = (60.0 * i) * (PI / 180.0)
        Offset(
            (cx + S * cos(ang)).toFloat(),
            (cy + S * sin(ang)).toFloat()
        )
    }

/**
 * Tiny 7-hex "demo" board:
 *
 *    [4] [0] [5]
 *      [6]
 */
fun tinyBoard(): List<Tile> {
    val coords = listOf(
        HexCoord(0, 0),
        HexCoord(1, 0),
        HexCoord(1, -1),
        HexCoord(0, -1),
        HexCoord(-1, 0),
        HexCoord(-1, 1),
        HexCoord(0, 1)
    )

    // Very rough resource layout, just to have something to demo.
    val resources = listOf(
        Resource.WHEAT,
        Resource.WOOD,
        Resource.BRICK,
        Resource.SHEEP,
        Resource.ORE,
        Resource.WOOD,
        Resource.SHEEP
    )

    // Example number tokens
    val numbers = listOf(6, 8, 5, 9, 10, 4, 11)

    return coords.indices.map { i ->
        Tile(coords[i], resources[i], numbers[i])
    }
}
