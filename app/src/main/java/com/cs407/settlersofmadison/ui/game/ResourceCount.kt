package com.cs407.settlersofmadison.ui.game

import com.cs407.settlersofmadison.domain.model.Resource

/**
 * Simple UI wrapper for showing a resource card with a count.
 */
data class ResourceCount(
    val type: Resource,
    val amount: Int
)