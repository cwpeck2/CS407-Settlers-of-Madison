package com.cs407.settlersofmadison.ui.game

import com.cs407.settlersofmadison.domain.model.EdgeKey
import com.cs407.settlersofmadison.domain.model.HexCoord
import com.cs407.settlersofmadison.domain.model.Tile
import com.cs407.settlersofmadison.domain.model.VertexKey
import com.cs407.settlersofmadison.domain.model.canonicalEdge
import com.cs407.settlersofmadison.domain.model.canonicalVertex


data class Vertex(
    val key: VertexKey,
    val tileCoords: Set<HexCoord>
)

data class Edge(
    val key: EdgeKey,
    val tileCoords: Set<HexCoord>,
    val vertices: Pair<VertexKey, VertexKey>
)

data class BoardGraph(
    val tiles: List<Tile>,
    val vertices: Map<VertexKey, Vertex>,
    val edges: Map<EdgeKey, Edge>,
    val tilesByCoord: Map<HexCoord, Tile> = tiles.associateBy { it.coord }
)
fun buildBoardGraph(tiles: List<Tile>): BoardGraph {
    val vertexToTiles = mutableMapOf<VertexKey, MutableSet<HexCoord>>()
    val edgeToTiles   = mutableMapOf<EdgeKey, MutableSet<HexCoord>>()


    for (tile in tiles) {
        val c = tile.coord


        for (corner in 0 until 6) {
            val vKey = canonicalVertex(c, corner)
            vertexToTiles.getOrPut(vKey) { linkedSetOf() }.add(c)
        }


        for (edgeIndex in 0 until 6) {
            val eKey = canonicalEdge(c, edgeIndex)
            edgeToTiles.getOrPut(eKey) { linkedSetOf() }.add(c)
        }
    }


    val vertices = vertexToTiles.mapValues { (vKey, coords) ->
        Vertex(key = vKey, tileCoords = coords)
    }

    val edges = edgeToTiles.mapValues { (eKey, coords) ->
        val baseHex = HexCoord(eKey.q, eKey.r)


        val v1 = canonicalVertex(baseHex, eKey.edge)
        val v2 = canonicalVertex(baseHex, (eKey.edge + 1) % 6)

        Edge(
            key = eKey,
            tileCoords = coords,
            vertices = v1 to v2
        )
    }

    return BoardGraph(
        tiles = tiles,
        vertices = vertices,
        edges = edges
    )
}