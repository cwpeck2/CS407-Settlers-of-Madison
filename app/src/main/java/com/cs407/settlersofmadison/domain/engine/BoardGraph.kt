package com.cs407.settlersofmadison.domain.engine

import com.cs407.settlersofmadison.domain.model.EdgeKey
import com.cs407.settlersofmadison.domain.model.HexCoord
import com.cs407.settlersofmadison.domain.model.Tile
import com.cs407.settlersofmadison.domain.model.VertexKey

data class Vertex(
    val key: VertexKey,
    val tileCoords: Set<HexCoord>   // which hexes touch this intersection
)

data class Edge(
    val key: EdgeKey,
    val tileCoords: Set<HexCoord>,  // hexes along this edge (1 or 2)
    val vertices: Pair<VertexKey, VertexKey>   // its endpoints
)

data class BoardGraph(
    val tiles: List<Tile>,
    val vertices: Map<VertexKey, Vertex>,
    val edges: Map<EdgeKey, Edge>,
    val tilesByCoord: Map<HexCoord, Tile> = tiles.associateBy { it.coord }
)

/**
 * Build a graph from a list of tiles: shared vertices and edges across tiles.
 */
fun buildBoardGraph(tiles: List<Tile>): BoardGraph {
    val vertexToTiles = mutableMapOf<VertexKey, MutableSet<HexCoord>>()
    val edgeToTiles   = mutableMapOf<EdgeKey, MutableSet<HexCoord>>()

    // 1) Walk every tile, register its 6 corners and 6 edges.
    for (tile in tiles) {
        val c = tile.coord

        // corners
        for (corner in 0 until 6) {
            val vKey = canonicalVertex(c, corner)
            vertexToTiles.getOrPut(vKey) { linkedSetOf() }.add(c)
        }

        // edges
        for (edgeIndex in 0 until 6) {
            val eKey = canonicalEdge(c, edgeIndex)
            edgeToTiles.getOrPut(eKey) { linkedSetOf() }.add(c)
        }
    }

    // 2) Turn those maps into Vertex / Edge objects
    val vertices = vertexToTiles.mapValues { (vKey, coords) ->
        Vertex(key = vKey, tileCoords = coords)
    }

    val edges = edgeToTiles.mapValues { (eKey, coords) ->
        val baseHex = HexCoord(eKey.q, eKey.r)

        // For an edge k on a given canonical hex, its endpoints are corners k and (k+1)%6
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