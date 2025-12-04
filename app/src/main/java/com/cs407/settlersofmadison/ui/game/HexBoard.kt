package com.cs407.settlersofmadison.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.cs407.settlersofmadison.domain.model.HexCoord
import com.cs407.settlersofmadison.domain.model.Resource
import com.cs407.settlersofmadison.domain.model.Tile
import com.cs407.settlersofmadison.domain.model.VertexKey
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun HexBoard(
    roomState: RoomState,
    currentPlayerId: String,
    robberMode: Boolean,
    robberCoord: HexCoord?,
    onVertexTap: (VertexKey) -> Unit,
    onTileTap: ((HexCoord) -> Unit)?
) {
    val tiles = roomState.tiles
    // Graph from tiles in state so it's always in sync
    val graph = remember(tiles) { buildBoardGraph(tiles) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val density = LocalDensity.current
        val boardWidthPx = with(density) { maxWidth.toPx() }
        val boardHeightPx = with(density) { maxHeight.toPx() }

        // Size so a radius-2 (19-hex) board fits nicely
        val hexSize = min(boardWidthPx, boardHeightPx) / 5f

        // 1) Centers of each tile in screen space
        val tileCenters = remember(tiles, hexSize, boardWidthPx, boardHeightPx) {
            computeTileCenters(tiles, hexSize, boardWidthPx, boardHeightPx)
        }

        // 2) Vertex positions = base tile center + corner offset
        val vertexPositions = remember(graph, tileCenters, hexSize) {
            computeVertexPositions(graph, tileCenters, hexSize)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(graph, vertexPositions, tileCenters, robberMode, onTileTap) {
                    detectTapGestures { tapOffset ->
                        // If robber is being moved: tap chooses a TILE
                        if (robberMode && onTileTap != null) {
                            val tileCoord = findNearestTile(
                                tap = tapOffset,
                                tileCenters = tileCenters,
                                maxDistancePx = hexSize * 0.9f
                            )
                            if (tileCoord != null) {
                                android.util.Log.d(
                                    "HEXDEBUG",
                                    "Robber tap at (${tapOffset.x}, ${tapOffset.y}) → tile $tileCoord"
                                )
                                onTileTap(tileCoord)
                            }
                            return@detectTapGestures
                        }

                        // Normal mode: tap chooses a VERTEX
                        val vKey = findNearestVertex(
                            tap = tapOffset,
                            vertexPositions = vertexPositions,
                            maxDistancePx = hexSize * 0.5f
                        )
                        if (vKey != null) {
                            val vertex = graph.vertices[vKey]
                            val tilesDesc = vertex?.tileCoords
                                ?.mapNotNull { coord -> graph.tilesByCoord[coord] }
                                ?.joinToString { t -> "${t.number}-${t.resource.name}" }
                                ?: "no tiles"

                            android.util.Log.d(
                                "HEXDEBUG",
                                "Tap at (${tapOffset.x}, ${tapOffset.y}) → $vKey touches: $tilesDesc"
                            )

                            onVertexTap(vKey)
                        }
                    }
                }
        ) {
            val tileStrokeWidth = hexSize * 0.06f

            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = hexSize * 0.4f
                isFakeBoldText = true
                isAntiAlias = true
            }

            // --- draw hex tiles ---
            tiles.forEach { tile ->
                val center = tileCenters[tile.coord] ?: return@forEach

                val path = Path().apply {
                    for (corner in 0 until 6) {
                        val off = hexCornerOffsetBoard(hexSize * 0.95f, corner)
                        val x = center.x + off.x
                        val y = center.y + off.y
                        if (corner == 0) {
                            moveTo(x, y)
                        } else {
                            lineTo(x, y)
                        }
                    }
                    close()
                }

                // Fill by resource
                drawPath(
                    path = path,
                    color = tile.resource.toTileColor()
                )

                // Outline
                drawPath(
                    path = path,
                    color = Color.Black,
                    style = Stroke(width = tileStrokeWidth)
                )

                // Robber highlight on this tile?
                if (robberCoord != null && robberCoord == tile.coord) {
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.35f),
                        radius = hexSize * 0.45f,
                        center = center,
                        style = Stroke(width = hexSize * 0.08f)
                    )
                }

                // Number in center
                drawContext.canvas.nativeCanvas.drawText(
                    tile.number.toString(),
                    center.x,
                    center.y + textPaint.textSize / 3f, // baseline tweak
                    textPaint
                )
            }

            // --- draw player settlements ---
            roomState.players.values.forEach { player ->
                val color = if (player.id == currentPlayerId) {
                    Color.Red
                } else {
                    Color.Gray
                }

                player.settlements.forEach { vKey ->
                    val pos = vertexPositions[vKey]
                    if (pos != null) {
                        drawCircle(
                            color = color,
                            radius = hexSize * 0.18f,
                            center = pos
                        )
                    }
                }
            }

            // --- faint dots at every vertex for debugging / aiming ---
            vertexPositions.values.forEach { pos ->
                drawCircle(
                    color = Color.Black.copy(alpha = 0.2f),
                    radius = hexSize * 0.08f,
                    center = pos
                )
            }
        }
    }
}

/* ---------- Geometry helpers ---------- */

private fun hexToPixelBoard(coord: HexCoord, hexSize: Float): Offset {
    // pointy-top axial layout (q, r)
    val x = hexSize * (sqrt(3f) * coord.q + sqrt(3f) / 2f * coord.r)
    val y = hexSize * (3f / 2f * coord.r)
    return Offset(x, y)
}

private fun hexCornerOffsetBoard(hexSize: Float, cornerIndex: Int): Offset {
    // same orientation as your board: pointy-top, corner 0 at top-right-ish
    val angleDeg = 60f * cornerIndex - 30f
    val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()
    val cx = hexSize * cos(angleRad)
    val cy = hexSize * sin(angleRad)
    return Offset(cx, cy)
}

/**
 * Vertex positions: **exact hex corners** from their canonical tile+corner.
 */
private fun computeVertexPositions(
    graph: BoardGraph,
    tileCenters: Map<HexCoord, Offset>,
    hexSize: Float
): Map<VertexKey, Offset> {
    val result = mutableMapOf<VertexKey, Offset>()

    for ((key, vertex) in graph.vertices) {
        // Base tile for this vertex key
        val baseCoord = HexCoord(key.q, key.r)
        val center = tileCenters[baseCoord]

        if (center != null) {
            val off = hexCornerOffsetBoard(hexSize * 0.95f, key.corner)
            result[key] = center + off
        } else {
            // Fallback: average of centers of tiles that touch this vertex
            val centers = vertex.tileCoords.mapNotNull { tileCenters[it] }
            if (centers.isNotEmpty()) {
                var sx = 0f
                var sy = 0f
                for (c in centers) {
                    sx += c.x
                    sy += c.y
                }
                val n = centers.size.toFloat()
                result[key] = Offset(sx / n, sy / n)
            }
        }
    }

    return result
}

/**
 * Pick the closest vertex to a tap, within a radius.
 */
private fun findNearestVertex(
    tap: Offset,
    vertexPositions: Map<VertexKey, Offset>,
    maxDistancePx: Float
): VertexKey? {
    var bestKey: VertexKey? = null
    var bestDist2 = maxDistancePx * maxDistancePx

    for ((key, pos) in vertexPositions) {
        val dx = tap.x - pos.x
        val dy = tap.y - pos.y
        val d2 = dx * dx + dy * dy
        if (d2 <= bestDist2) {
            bestDist2 = d2
            bestKey = key
        }
    }
    return bestKey
}

/**
 * Pick the closest tile center to a tap, within a radius (for robber moves).
 */
private fun findNearestTile(
    tap: Offset,
    tileCenters: Map<HexCoord, Offset>,
    maxDistancePx: Float
): HexCoord? {
    var bestCoord: HexCoord? = null
    var bestDist2 = maxDistancePx * maxDistancePx

    for ((coord, pos) in tileCenters) {
        val dx = tap.x - pos.x
        val dy = tap.y - pos.y
        val d2 = dx * dx + dy * dy
        if (d2 <= bestDist2) {
            bestDist2 = d2
            bestCoord = coord
        }
    }
    return bestCoord
}

/* ---------- Resource → color ---------- */

private fun Resource.toTileColor(): Color = when (this) {
    Resource.WOOD  -> Color(0xFF4CAF50)   // green-ish
    Resource.BRICK -> Color(0xFFFF7043)   // orange/red
    Resource.SHEEP -> Color(0xFF9CCC65)   // light green
    Resource.WHEAT -> Color(0xFFFFF176)   // yellow
    Resource.ORE   -> Color(0xFF90A4AE)   // gray-blue
    // If/when you add Resource.WATER, you can make it blue here.
    else           -> Color(0xFFBDBDBD)   // fallback (desert / special)
}

/* ---------- Board layout math ---------- */

/**
 * Compute center for each tile. Origin = center of the canvas.
 */
private fun computeTileCenters(
    tiles: List<Tile>,
    hexSize: Float,
    boardWidthPx: Float,
    boardHeightPx: Float
): Map<HexCoord, Offset> {
    val origin = Offset(boardWidthPx / 2f, boardHeightPx / 2f)

    return tiles.associate { tile ->
        val local = hexToPixelBoard(tile.coord, hexSize)
        tile.coord to Offset(origin.x + local.x, origin.y + local.y)
    }
}
