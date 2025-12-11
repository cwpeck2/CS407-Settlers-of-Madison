package com.cs407.settlersofmadison.domain.model

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt






enum class Resource {
    CONCRETE,
    STUDENT,
    BUCKY,
    CHAIR,
    CHEESE_CURD,

    LAKE
}






data class HexCoord(val q: Int, val r: Int)


data class Tile(
    val coord: HexCoord,
    val resource: Resource,
    val number: Int,
    val landmark: Landmark? = null
)


data class VertexKey(val q: Int, val r: Int, val corner: Int)
data class EdgeKey(val q: Int, val r: Int, val edge: Int)






private val DIRS = arrayOf(
    HexCoord(+1, 0),
    HexCoord(+1, -1),
    HexCoord(0, -1),
    HexCoord(-1, 0),
    HexCoord(-1, +1),
    HexCoord(0, +1)
)


fun neighbor(c: HexCoord, dir: Int): HexCoord =
    HexCoord(c.q + DIRS[dir].q, c.r + DIRS[dir].r)






private fun vertexTriple(c: HexCoord, corner: Int): List<HexCoord> {
    val c0 = c
    val c1 = neighbor(c, corner)
    val c2 = neighbor(c, (corner + 5) % 6)


    return listOf(c0, c1, c2).sortedWith(
        compareBy<HexCoord> { it.q }.thenBy { it.r }
    )
}


fun canonicalVertex(c: HexCoord, corner: Int): VertexKey {
    val triple = vertexTriple(c, corner)
    val canonicalCoord = triple[0]


    val canonicalCorner = (0 until 6).first { k ->
        vertexTriple(canonicalCoord, k) == triple
    }

    return VertexKey(canonicalCoord.q, canonicalCoord.r, canonicalCorner)
}


fun canonicalEdge(c: HexCoord, edge: Int): EdgeKey =
    if (edge in 0..2) {
        EdgeKey(c.q, c.r, edge)
    } else {
        val n = neighbor(c, edge)
        EdgeKey(n.q, n.r, (edge + 3) % 6)
    }






data class Pixel(val x: Float, val y: Float)


fun axialToPixel(c: HexCoord, size: Float): Pixel {
    val x = size * (sqrt(3f) * c.q + (sqrt(3f) / 2f) * c.r)
    val y = size * ((3f / 2f) * c.r)
    return Pixel(x, y)
}


fun hexCorners(cx: Float, cy: Float, S: Float): List<Offset> =
    (0 until 6).map { i ->
        val ang = (30.0 - 60.0 * i) * (PI / 180.0)
        Offset(
            (cx + S * cos(ang)).toFloat(),
            (cy + S * sin(ang)).toFloat()
        )
    }






fun standardCatanBoardRandom(): List<Tile> {

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



    val baseResources = mutableListOf(
        Resource.CONCRETE, Resource.CONCRETE, Resource.CONCRETE, Resource.CONCRETE,
        Resource.STUDENT, Resource.STUDENT, Resource.STUDENT,
        Resource.BUCKY, Resource.BUCKY, Resource.BUCKY, Resource.BUCKY,
        Resource.CHAIR, Resource.CHAIR, Resource.CHAIR, Resource.CHAIR,
        Resource.CHEESE_CURD, Resource.CHEESE_CURD, Resource.CHEESE_CURD, Resource.CHEESE_CURD
    )



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
        5
    )

    baseResources.shuffle()
    baseNumbers.shuffle()


    return coords.indices.map { i ->
        Tile(
            coord = coords[i],
            resource = baseResources[i],
            number = baseNumbers[i]
        )
    }
}


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
