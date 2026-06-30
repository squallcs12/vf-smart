package com.daotranbang.vfsmart.data.network

/**
 * Transport-level connection state to the car (the ws://<ip>/ws status stream).
 * Kept to two states so UI `when` blocks stay exhaustive.
 */
sealed class ConnectionState {
    object Connected : ConnectionState()
    object Disconnected : ConnectionState()
}
