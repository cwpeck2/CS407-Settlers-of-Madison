package com.cs407.settlersofmadison.game.state

import com.cs407.settlersofmadison.network.ConnState
import com.cs407.settlersofmadison.network.P2PService
import kotlinx.coroutines.flow.Flow

/**
 * P2PService into the GameTransport interface.
 */
class P2PGameTransport(
    private val p2p: P2PService
) : GameTransport {

    override val incoming: Flow<String> = p2p.incoming

    override fun send(message: String) {
        p2p.send(message)
    }
}