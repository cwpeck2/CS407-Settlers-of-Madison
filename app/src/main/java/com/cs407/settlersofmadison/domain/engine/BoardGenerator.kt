package com.cs407.settlersofmadison.domain.engine

import com.cs407.settlersofmadison.domain.model.HexCoord
import com.cs407.settlersofmadison.domain.model.Resource
import com.cs407.settlersofmadison.domain.model.Tile

/**
 * Helpers for constructing board layouts (tiny board, resource patterns, etc.).
 */

/**
 * Simple 7-hex "tiny board"
 *
 *   .  1  2  3
 *    4  0  5
 *   .  6  7  .
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

    val resources = listOf(
        Resource.WHEAT,
        Resource.WOOD,
        Resource.BRICK,
        Resource.SHEEP,
        Resource.ORE,
        Resource.WOOD,
        Resource.SHEEP
    )

    val numbers = listOf(6, 8, 5, 9, 10, 4, 11)

    return coords.indices.map { i ->
        Tile(
            coord = coords[i],
            resource = resources[i],
            number = numbers[i]
        )
    }
}