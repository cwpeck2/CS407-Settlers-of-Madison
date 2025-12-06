package com.cs407.settlersofmadison.ui.game

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.cs407.settlersofmadison.R
import com.cs407.settlersofmadison.domain.model.EdgeKey
import com.cs407.settlersofmadison.domain.model.HexCoord
import com.cs407.settlersofmadison.domain.model.Resource
import com.cs407.settlersofmadison.domain.model.Tile
import com.cs407.settlersofmadison.domain.model.VertexKey
import com.cs407.settlersofmadison.domain.model.canonicalVertex   // <-- NEW IMPORT
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
    onTileTap: ((HexCoord) -> Unit)?,
    onEdgeTap: ((EdgeKey) -> Unit)? = null,
    highlightEdges: Set<EdgeKey> = emptySet(),
    highlightVertices: Set<VertexKey> = emptySet()
) {
    val tileTextures: Map<Resource, ImageBitmap> = mapOf(
        Resource.CONCRETE to ImageBitmap.imageResource(id = R.drawable.tile_concrete),
        Resource.STUDENT  to ImageBitmap.imageResource(id = R.drawable.tile_student),
        Resource.BUCKY    to ImageBitmap.imageResource(id = R.drawable.tile_bucky),
        Resource.CHAIR    to ImageBitmap.imageResource(id = R.drawable.tile_chair),
        Resource.CHEESE_CURD to ImageBitmap.imageResource(id = R.drawable.tile_cheese_curds)
    )
    val tiles = roomState.tiles
    val graph = remember(tiles) { buildBoardGraph(tiles) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val density = LocalDensity.current
        val boardWidthPx = with(density) { maxWidth.toPx() }
        val boardHeightPx = with(density) { maxHeight.toPx() }

        // Slightly smaller so everything fits on phones
        val hexSize = min(boardWidthPx, boardHeightPx) / 9.5f

        // 1) tile centers in screen space
        val tileCenters = remember(tiles, hexSize, boardWidthPx, boardHeightPx) {
            computeTileCenters(tiles, hexSize, boardWidthPx, boardHeightPx)
        }

        // 2) vertex positions (FIXED for border vertices)
        val vertexPositions = remember(graph, tileCenters, hexSize) {
            computeVertexPositions(graph, tileCenters, hexSize)
        }

        // 3) edge centers
        val edgeCenters = remember(graph, vertexPositions) {
            computeEdgeCenters(graph, vertexPositions)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(
                    graph,
                    vertexPositions,
                    tileCenters,
                    robberMode,
                    robberCoord,
                    onTileTap,
                    onEdgeTap,
                    edgeCenters,
                    hexSize,
                    highlightEdges,
                    highlightVertices
                ) {
                    detectTapGestures { tapOffset ->
                        // If robber is being moved → choose tile
                        if (robberMode && onTileTap != null) {
                            val tileCoord = findNearestTile(
                                tap = tapOffset,
                                tileCenters = tileCenters,
                                maxDistancePx = hexSize * 0.9f
                            )
                            if (tileCoord != null) {
                                if (robberCoord != null && tileCoord == robberCoord) {
                                    return@detectTapGestures
                                }
                                Log.d(
                                    "HEXDEBUG",
                                    "Robber tap at (${tapOffset.x}, ${tapOffset.y}) → tile $tileCoord"
                                )
                                onTileTap(tileCoord)
                            }
                            return@detectTapGestures
                        }

                        // Normal mode:
                        // 1) Try edge (for roads)
                        if (onEdgeTap != null && highlightEdges.isNotEmpty()) {
                            val eKey = findNearestEdge(
                                tap = tapOffset,
                                graph = graph,
                                vertexPositions = vertexPositions,
                                maxDistancePx = hexSize * 0.45f
                            )
                            if (eKey != null && eKey in highlightEdges) {
                                onEdgeTap(eKey)
                                return@detectTapGestures
                            }
                        }

                        // 2) Vertex (for settlements)
                        val vKey = findNearestVertex(
                            tap = tapOffset,
                            vertexPositions = vertexPositions,
                            maxDistancePx = hexSize * 0.55f
                        )
                        if (vKey != null) {
                            // If we are highlighting vertices (build-settlement mode),
                            // only allow taps on highlighted ones. In setup (no highlights),
                            // allow any vertex.
                            if (highlightVertices.isEmpty() || vKey in highlightVertices) {
                                onVertexTap(vKey)
                            }
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

            // --- Tiles ---
            tiles.forEach { tile ->
                val center = tileCenters[tile.coord] ?: return@forEach

                // Hex polygon for clipping/border
                val path = Path().apply {
                    for (corner in 0 until 6) {
                        val off = hexCornerOffsetBoard(hexSize * 0.95f, corner)
                        val x = center.x + off.x
                        val y = center.y + off.y
                        if (corner == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }

                val texture = tileTextures[tile.resource]

                if (texture != null) {
                    // Slightly larger than the hex so the art fills it, even with padding
                    val radius = hexSize * 0.95f
                    val scale = 1.4f   // tweak this if you want the art bigger/smaller

                    val drawWidth = (radius * 2f * scale).toInt()
                    val drawHeight = (radius * 2f * scale).toInt()

                    val topLeft = IntOffset(
                        (center.x - drawWidth / 2f).toInt(),
                        (center.y - drawHeight / 2f).toInt()
                    )

                    clipPath(path) {
                        drawImage(
                            image = texture,
                            dstSize = IntSize(drawWidth, drawHeight),
                            dstOffset = topLeft
                        )
                    }
                } else {
                    // Fallback: flat color if somehow no texture
                    drawPath(
                        path = path,
                        color = tile.resource.toTileColor()
                    )
                }

                // Black outline around the hex
                drawPath(
                    path = path,
                    color = Color.Black,
                    style = Stroke(width = tileStrokeWidth)
                )

                // Robber ring
                if (robberCoord != null && robberCoord == tile.coord) {
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.35f),
                        radius = hexSize * 0.45f,
                        center = center,
                        style = Stroke(width = hexSize * 0.08f)
                    )
                }

// --- Number token disc on top of the tile ---
                val tokenRadius = hexSize * 0.32f

// light red fill
                drawCircle(
                    color = Color(0xFFFFFFFF),
                    radius = tokenRadius,
                    center = center
                )

// darker red ring around it
                drawCircle(
                    color = Color(0xFFB71C1C),
                    radius = tokenRadius,
                    center = center,
                    style = Stroke(width = hexSize * 0.03f)
                )

// number text on top of the disc
                drawContext.canvas.nativeCanvas.drawText(
                    tile.number.toString(),
                    center.x,
                    center.y + textPaint.textSize / 3f,
                    textPaint
                )
            }


            // Highlight edges (build-road mode)
            highlightEdges.forEach { eKey ->
                val edge = graph.edges[eKey]
                if (edge != null) {
                    val p1 = vertexPositions[edge.vertices.first]
                    val p2 = vertexPositions[edge.vertices.second]
                    if (p1 != null && p2 != null) {
                        drawLine(
                            color = Color.Yellow.copy(alpha = 0.45f),
                            start = p1,
                            end = p2,
                            strokeWidth = hexSize * 0.12f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // Highlight vertices (build-settlement mode)
            highlightVertices.forEach { vKey ->
                val pos = vertexPositions[vKey]
                if (pos != null) {
                    drawCircle(
                        color = Color.Yellow.copy(alpha = 0.5f),
                        radius = hexSize * 0.16f,
                        center = pos,
                        style = Stroke(width = hexSize * 0.05f)
                    )
                }
            }

            // --- Roads + Settlements ---
            roomState.players.values.forEach { player ->
                val color = if (player.id == currentPlayerId) Color.Red else Color.Gray

                // Roads
                player.roads.forEach { eKey ->
                    val edge = graph.edges[eKey]
                    if (edge != null) {
                        val p1 = vertexPositions[edge.vertices.first]
                        val p2 = vertexPositions[edge.vertices.second]
                        if (p1 != null && p2 != null) {
                            drawLine(
                                color = color,
                                start = p1,
                                end = p2,
                                strokeWidth = hexSize * 0.18f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                // Settlements
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

            // Optional: faint dots at vertices to help tapping
            vertexPositions.values.forEach { pos ->
                drawCircle(
                    color = Color.Black.copy(alpha = 0.20f),
                    radius = hexSize * 0.08f,
                    center = pos
                )
            }
        }
    }
}

/* ---------- Geometry helpers ---------- */

private fun hexToPixelBoard(coord: HexCoord, hexSize: Float): Offset {
    // pointy-top axial layout
    val x = hexSize * (sqrt(3f) * coord.q + sqrt(3f) / 2f * coord.r)
    val y = hexSize * (3f / 2f * coord.r)
    return Offset(x, y)
}

private fun hexCornerOffsetBoard(hexSize: Float, cornerIndex: Int): Offset {
    // 0..5 around hex, pointy-top.
    // Must match vertexTriple/canonicalVertex, which use neighbors
    // in directions (corner, corner-1).
    val angleDeg = 30f - 60f * cornerIndex
    val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()
    return Offset(
        x = hexSize * cos(angleRad),
        y = hexSize * sin(angleRad)
    )
}

private fun computeTileCenters(
    tiles: List<Tile>,
    hexSize: Float,
    boardWidthPx: Float,
    boardHeightPx: Float
): Map<HexCoord, Offset> {
    if (tiles.isEmpty()) return emptyMap()

    val rawPositions = mutableMapOf<HexCoord, Offset>()
    var minX = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY

    for (tile in tiles) {
        val p = hexToPixelBoard(tile.coord, hexSize)
        rawPositions[tile.coord] = p
        if (p.x < minX) minX = p.x
        if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y
        if (p.y > maxY) maxY = p.y
    }

    val boardCenterX = (minX + maxX) / 2f
    val boardCenterY = (minY + maxY) / 2f
    val screenCenterX = boardWidthPx / 2f
    val screenCenterY = boardHeightPx / 2f

    val offsetX = screenCenterX - boardCenterX
    val offsetY = screenCenterY - boardCenterY

    val result = mutableMapOf<HexCoord, Offset>()
    for ((coord, p) in rawPositions) {
        result[coord] = Offset(p.x + offsetX, p.y + offsetY)
    }
    return result
}

private fun computeVertexPositions(
    graph: BoardGraph,
    tileCenters: Map<HexCoord, Offset>,
    hexSize: Float
): Map<VertexKey, Offset> {
    val result = mutableMapOf<VertexKey, Offset>()

    for ((key, vertex) in graph.vertices) {
        val baseCoord = HexCoord(key.q, key.r)
        val baseCenter = tileCenters[baseCoord]

        if (baseCenter != null) {
            // Normal case: canonical base hex exists on the board
            val off = hexCornerOffsetBoard(hexSize * 0.95f, key.corner)
            result[key] = baseCenter + off
        } else {
            // Border case: canonical base hex is off-board.
            // Use one of the real tiles touching this vertex, and find
            // which of its local corners maps to this canonical vertex key.
            val candidateCoord = vertex.tileCoords
                .sortedWith(compareBy<HexCoord> { it.q }.thenBy { it.r })
                .firstOrNull()

            var placed = false

            if (candidateCoord != null) {
                val candidateCenter = tileCenters[candidateCoord]
                if (candidateCenter != null) {
                    val cornerIndex = (0 until 6).firstOrNull { k ->
                        canonicalVertex(candidateCoord, k) == key
                    }
                    if (cornerIndex != null) {
                        val off = hexCornerOffsetBoard(hexSize * 0.95f, cornerIndex)
                        result[key] = candidateCenter + off
                        placed = true
                    }
                }
            }

            if (!placed) {
                // Ultimate fallback: average centers of all touching tiles
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
    }

    return result
}

private fun computeEdgeCenters(
    graph: BoardGraph,
    vertexPositions: Map<VertexKey, Offset>
): Map<EdgeKey, Offset> {
    val result = mutableMapOf<EdgeKey, Offset>()

    for ((key, edge) in graph.edges) {
        val (v1, v2) = edge.vertices
        val p1 = vertexPositions[v1]
        val p2 = vertexPositions[v2]
        if (p1 != null && p2 != null) {
            result[key] = Offset(
                (p1.x + p2.x) / 2f,
                (p1.y + p2.y) / 2f
            )
        }
    }
    return result
}

/* ---------- Hit testing ---------- */

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

private fun findNearestEdge(
    tap: Offset,
    graph: BoardGraph,
    vertexPositions: Map<VertexKey, Offset>,
    maxDistancePx: Float
): EdgeKey? {
    var bestKey: EdgeKey? = null
    var bestDist2 = maxDistancePx * maxDistancePx

    for ((key, edge) in graph.edges) {
        val (v1, v2) = edge.vertices
        val p1 = vertexPositions[v1]
        val p2 = vertexPositions[v2]
        if (p1 == null || p2 == null) continue

        val d2 = distanceToSegmentSquared(tap, p1, p2)
        if (d2 <= bestDist2) {
            bestDist2 = d2
            bestKey = key
        }
    }
    return bestKey
}

/**
 * Squared distance from point P to segment AB.
 */
private fun distanceToSegmentSquared(p: Offset, a: Offset, b: Offset): Float {
    val abx = b.x - a.x
    val aby = b.y - a.y
    val apx = p.x - a.x
    val apy = p.y - a.y

    val abLen2 = abx * abx + aby * aby
    if (abLen2 == 0f) {
        // edge collapsed, treat like a point
        return apx * apx + apy * apy
    }

    val t = ((apx * abx) + (apy * aby)) / abLen2
    val clampedT = when {
        t < 0f -> 0f
        t > 1f -> 1f
        else -> t
    }

    val cx = a.x + clampedT * abx
    val cy = a.y + clampedT * aby
    val dx = p.x - cx
    val dy = p.y - cy

    return dx * dx + dy * dy
}

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

/* ---------- Colors per resource ---------- */

private fun Resource.toTileColor(): Color =
    when (this) {
        Resource.CONCRETE  -> Color(0xFF8F8F8F)
        Resource.STUDENT -> Color(0xFFA01CB7)
        Resource.BUCKY -> Color(0xFFF60000)
        Resource.CHAIR -> Color(0xFF0F5215)
        Resource.CHEESE_CURD   -> Color(0xFFFFBE4F)
        Resource.LAKE -> Color(0xFF2196F3)
    }

