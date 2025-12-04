package com.cs407.settlersofmadison.domain.model

/**
 * A single hex tile on the board with a resource type and number token.
 */
data class Tile(
    val coord: HexCoord,
    val resource: Resource,
    val number: Int
)