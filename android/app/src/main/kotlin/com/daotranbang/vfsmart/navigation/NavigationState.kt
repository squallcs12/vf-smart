package com.daotranbang.vfsmart.navigation

data class NavigationState(
    val isActive: Boolean = false,
    val maneuver: String = "",      // e.g. "TURN LEFT", "CONTINUE"
    val distance: String = "",      // e.g. "300 m", "1.2 km"
    val direction: Direction = Direction.STRAIGHT
) {
    enum class Direction {
        STRAIGHT,
        SLIGHT_LEFT, LEFT, SHARP_LEFT,
        SLIGHT_RIGHT, RIGHT, SHARP_RIGHT,
        U_TURN,
        KEEP_LEFT, KEEP_RIGHT,
        FORK_LEFT, FORK_RIGHT,
        RAMP_LEFT, RAMP_RIGHT,
        MERGE,
        ROUNDABOUT, EXIT_ROUNDABOUT,
        FERRY,
        DESTINATION
    }
}
