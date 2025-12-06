package com.cs407.settlersofmadison.domain.model

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ---------------------------------------------------------------------
// Resources
// ---------------------------------------------------------------------

// Basic Catan-style resources for now.
enum class Resource {
    CONCRETE,
    STUDENT,
    BUCKY,
    CHAIR,
    CHEESE_CURD,

    LAKE
}

// ---------------------------------------------------------------------
// Core hex types (pointy-top axial)
// ---------------------------------------------------------------------

/**
 * Axial hex coordinate (q, r) for a **pointy-top** layout as in
 * Red Blob Games.
 */
data class HexCoord(val q: Int, val r: Int)

/**
 * A single hex tile on the board.
 */
data class Tile(
    val coord: HexCoord,
    val resource: Resource,
    val number: Int
)

/**
 * Intersection (vertex) and edge keys, in canonical form.
 *
 *  - VertexKey.corner ∈ [0, 5]
 *  - EdgeKey.edge ∈ [0, 5]
 *
 * These are *canonicalized* so that a physical corner/edge shared by
 * neighboring tiles always uses the same (q, r, corner/edge) no matter
 * which tile you started from.
 */
data class VertexKey(val q: Int, val r: Int, val corner: Int)
data class EdgeKey(val q: Int, val r: Int, val edge: Int)

// ---------------------------------------------------------------------
// Axial neighbor directions (pointy-top) – Red Blob style
// ---------------------------------------------------------------------

/**
 * Direction vectors for pointy-top axial coordinates.
 *
 * Direction indices (0..5) correspond to the 6 primary hex neighbors.
 * This order is chosen to be consistent with using corner indices
 * between direction i and (i+1) when computing corners.
 */
private val DIRS = arrayOf(
    HexCoord(+1, 0),   // 0
    HexCoord(+1, -1),  // 1
    HexCoord(0, -1),   // 2
    HexCoord(-1, 0),   // 3
    HexCoord(-1, +1),  // 4
    HexCoord(0, +1)    // 5
)

/**
 * Neighbor in given direction (0..5).
 */
fun neighbor(c: HexCoord, dir: Int): HexCoord =
    HexCoord(c.q + DIRS[dir].q, c.r + DIRS[dir].r)

// ---------------------------------------------------------------------
// Canonical vertex + edge keys (this is where the hex logic really lives)
// ---------------------------------------------------------------------

/**
 * Helper for vertex canonicalization.
 *
 * For a given tile (c) and corner index, returns the **three** hex
 * coordinates that meet at that vertex:
 *   - the tile itself (c)
 *   - neighbor in direction `corner`
 *   - neighbor in direction `corner - 1` (i.e. (corner + 5) % 6)
 *
 * We then sort these so that the same physical vertex always yields
 * the same triple, regardless of which tile/corner we started from.
 */
private fun vertexTriple(c: HexCoord, corner: Int): List<HexCoord> {
    val c0 = c
    val c1 = neighbor(c, corner)
    val c2 = neighbor(c, (corner + 5) % 6)

    // Sort lexicographically so (A,B,C) is in a canonical order
    return listOf(c0, c1, c2).sortedWith(
        compareBy<HexCoord> { it.q }.thenBy { it.r }
    )
}

/**
 * Canonical vertex id: any physical corner shared by up to 3 hexes
 * always maps to the same (q, r, corner), regardless of which tile /
 * corner we started from.
 */
fun canonicalVertex(c: HexCoord, corner: Int): VertexKey {
    val triple = vertexTriple(c, corner)
    val canonicalCoord = triple[0] // smallest (q,r)

    // Find which local corner on canonicalCoord yields the same triple
    val canonicalCorner = (0 until 6).first { k ->
        vertexTriple(canonicalCoord, k) == triple
    }

    return VertexKey(canonicalCoord.q, canonicalCoord.r, canonicalCorner)
}

/**
 * Canonical edge id: any physical edge shared by two hexes always maps
 * to the same (q, r, edge).
 *
 * Convention:
 *  - For local edges 0,1,2, we treat the current hex as canonical.
 *  - For local edges 3,4,5, we move to the neighbor and use the
 *    "opposite" edge index (edge + 3) % 6.
 */
fun canonicalEdge(c: HexCoord, edge: Int): EdgeKey =
    if (edge in 0..2) {
        EdgeKey(c.q, c.r, edge)
    } else {
        val n = neighbor(c, edge)
        EdgeKey(n.q, n.r, (edge + 3) % 6)
    }

// ---------------------------------------------------------------------
// Optional pixel helpers (not strictly needed by HexBoard but handy)
// ---------------------------------------------------------------------

/**
 * Simple pixel coordinate helper used by older drawing code; for
 * current Compose board we instead use hexToPixelBoard in HexBoard.kt.
 */
data class Pixel(val x: Float, val y: Float)

/**
 * Axial -> pixel for **pointy-top** layout (same basis as HexBoard).
 *
 * Basis vectors:
 *   q: (sqrt(3), 0)
 *   r: (sqrt(3)/2, 3/2)
 */
fun axialToPixel(c: HexCoord, size: Float): Pixel {
    val x = size * (sqrt(3f) * c.q + (sqrt(3f) / 2f) * c.r)
    val y = size * ((3f / 2f) * c.r)
    return Pixel(x, y)
}

/**
 * Corner positions around a hex centered at (cx, cy) with radius S.
 */
fun hexCorners(cx: Float, cy: Float, S: Float): List<Offset> =
    (0 until 6).map { i ->
        val ang = (30.0 - 60.0 * i) * (PI / 180.0)
        Offset(
            (cx + S * cos(ang)).toFloat(),
            (cy + S * sin(ang)).toFloat()
        )
    }

// ---------------------------------------------------------------------
// Board layouts
// ---------------------------------------------------------------------

/**
 * Standard radius-2 Catan-style board (19 hexes) with randomized
 * resources + numbers. This is what GameViewModel currently uses.
 */
fun standardCatanBoardRandom(): List<Tile> {
    // 1) Generate axial coords for a radius-2 hexagon (Red Blob pattern)
    val radius = 2
    val coords = mutableListOf<HexCoord>()
    for (q in -radius..radius) {
        val r1 = maxOf(-radius, -q - radius)
        val r2 = minOf(radius, -q + radius)
        for (r in r1..r2) {
            coords += HexCoord(q, r)
        }
    }

    require(coords.size == 19) { "Radius-2 hexagon should have 19 tiles, got ${coords.size}" }

    // 2) Resource distribution – not strictly standard Catan, but
    //    gives a nice mix (4 of each + 3 extra).
    val baseResources = mutableListOf(
        Resource.CONCRETE, Resource.CONCRETE, Resource.CONCRETE, Resource.CONCRETE,
        Resource.STUDENT, Resource.STUDENT, Resource.STUDENT,
        Resource.BUCKY, Resource.BUCKY, Resource.BUCKY, Resource.BUCKY,
        Resource.CHAIR, Resource.CHAIR, Resource.CHAIR, Resource.CHAIR,
        Resource.CHEESE_CURD, Resource.CHEESE_CURD, Resource.CHEESE_CURD, Resource.CHEESE_CURD
    )

    // 3) Number tokens – again, roughly Catan-like but not exact.
    //    (18 non-7 numbers + one extra to make 19)
    val baseNumbers = mutableListOf(
        2,
        3, 3,
        4, 4,
        5, 5,
        6, 6,
        8, 8,
        9, 9,
        10, 10,
        11, 11,
        12,
        5 // extra filler
    )

    baseResources.shuffle()
    baseNumbers.shuffle()

    // 4) Zip them into tiles
    return coords.indices.map { i ->
        Tile(
            coord = coords[i],
            resource = baseResources[i],
            number = baseNumbers[i]
        )
    }
}

/**
 * Tiny 7-hex "demo" board:
 *
 *    [4] [0] [5]
 *      [6]
 *    [2] [1] [3]
 *
 * Mostly useful for debugging hex math and vertex/edge sharing.
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
        Resource.CHAIR,
        Resource.CONCRETE,
        Resource.STUDENT,
        Resource.BUCKY,
        Resource.CHEESE_CURD,
        Resource.CONCRETE,
        Resource.BUCKY
    )

    val numbers = listOf(6, 8, 5, 9, 10, 4, 11)

    return coords.indices.map { i ->
        Tile(coords[i], resources[i], numbers[i])
    }
}
