package com.vinfast.vf3smart.navigation

data class NavigationState(
    val isActive: Boolean = false,
    val maneuver: String = "",      // e.g. "TURN LEFT", "CONTINUE"
    val distance: String = "",      // e.g. "300 m", "1.2 km"
    val direction: Direction = Direction.STRAIGHT
) {
    enum class Direction {
        LEFT, RIGHT, STRAIGHT, U_TURN, ROUNDABOUT
    }
}
