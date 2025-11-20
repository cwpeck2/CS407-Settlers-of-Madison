package com.cs407.settlersofmadison.domain.model

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class Resource { TIMBER, STONE, FOOD, CULTURE, TECH }

data class HexCoord(val q: Int, val r: Int)
data class Tile(val coord: HexCoord, val resource: Resource, val number: Int)

data class VertexKey(val q: Int, val r: Int, val corner: Int) // 0..5
data class EdgeKey(val q: Int, val r: Int, val edge: Int)     // 0..5

private val DIRS = arrayOf(
    HexCoord(+1, 0), HexCoord(+1,-1), HexCoord(0,-1),
    HexCoord(-1, 0), HexCoord(-1,+1), HexCoord(0,+1)
)

fun neighbor(c: HexCoord, dir: Int) = HexCoord(c.q + DIRS[dir].q, c.r + DIRS[dir].r)

fun canonicalVertex(c: HexCoord, corner: Int): VertexKey =
    if (corner in 0..2) VertexKey(c.q, c.r, corner)
    else {
        val n = neighbor(c, corner)
        VertexKey(n.q, n.r, (corner + 3) % 6)
    }

fun canonicalEdge(c: HexCoord, edge: Int): EdgeKey =
    if (edge in 0..2) EdgeKey(c.q, c.r, edge)
    else {
        val n = neighbor(c, edge)
        EdgeKey(n.q, n.r, (edge + 3) % 6)
    }

data class Pixel(val x: Float, val y: Float)
fun axialToPixel(c: HexCoord, S: Float): Pixel {
    val x = S * (1.5f * c.q)
    val y = S * (sqrt(3f) * (c.r + c.q / 2f))
    return Pixel(x, y)
}

fun hexCorners(cx: Float, cy: Float, S: Float): List<Offset> =
    (0 until 6).map { i ->
        val ang = (60.0 * i) * (PI / 180.0)
        Offset((cx + S * cos(ang)).toFloat(), (cy + S * sin(ang)).toFloat())
    }

fun tinyBoard(): List<Tile> {
    val coords = listOf(
        HexCoord(0,0),
        HexCoord(1,0), HexCoord(1,-1), HexCoord(0,-1),
        HexCoord(-1,0), HexCoord(-1,1), HexCoord(0,1)
    )
    val resources = listOf(
        Resource.CULTURE, Resource.TIMBER, Resource.STONE,
        Resource.FOOD, Resource.TECH, Resource.TIMBER, Resource.FOOD
    )
    val numbers = listOf(6, 8, 5, 9, 10, 4, 11)
    return coords.indices.map { i -> Tile(coords[i], resources[i], numbers[i]) }
}
