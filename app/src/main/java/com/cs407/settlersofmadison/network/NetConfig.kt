package com.cs407.settlersofmadison.network

object NetConfig {
    // Turn this off later if you ever test on real devices
    const val USE_RELAY = true

    // From inside an Android emulator, 10.0.2.2 = your PC's localhost
    const val RELAY_IP = "10.0.2.2"

    // Port your relay server on the PC will listen on
    const val RELAY_PORT = 9000
}