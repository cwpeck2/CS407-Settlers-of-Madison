package com.cs407.settlersofmadison.ui.game

/**
 * User profile data class with validation and helper methods.
 * Stores player information for display in game and lobby.
 */
data class UserProfile(
    val playerName: String = "Player",
    val playerId: String = "player_1",
    val gamesPlayed: Int = 0,
    val gamesWon: Int = 0
) {
    // Get win rate as percentage
    fun getWinRate(): Int {
        return if (gamesPlayed > 0) (gamesWon * 100) / gamesPlayed else 0
    }

    // Check if name is valid (not empty, reasonable length)
    fun isValidName(): Boolean {
        return playerName.isNotBlank() && playerName.length in 2..20
    }

    // Get display text for stats
    fun getStatsText(): String {
        return "Games: $gamesPlayed | Wins: $gamesWon | Win Rate: ${getWinRate()}%"
    }

    // Create copy with updated stats after a game
    fun withGameResult(won: Boolean): UserProfile {
        return copy(
            gamesPlayed = gamesPlayed + 1,
            gamesWon = if (won) gamesWon + 1 else gamesWon
        )
    }
}