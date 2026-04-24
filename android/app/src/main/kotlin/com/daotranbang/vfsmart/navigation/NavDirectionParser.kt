package com.daotranbang.vfsmart.navigation

object NavDirectionParser {

    fun parse(text: String): NavigationState.Direction {
        val t = text.lowercase()
        return when {
            "u-turn" in t || "uturn" in t                       -> NavigationState.Direction.U_TURN
            "destination" in t || "arrived" in t                -> NavigationState.Direction.DESTINATION
            "ferry" in t                                        -> NavigationState.Direction.FERRY
            "roundabout" in t || "rotary" in t -> when {
                "exit" in t                                     -> NavigationState.Direction.EXIT_ROUNDABOUT
                else                                            -> NavigationState.Direction.ROUNDABOUT
            }
            "merge" in t                                        -> NavigationState.Direction.MERGE
            "slight left" in t                                  -> NavigationState.Direction.SLIGHT_LEFT
            "slight right" in t                                 -> NavigationState.Direction.SLIGHT_RIGHT
            "sharp left" in t                                   -> NavigationState.Direction.SHARP_LEFT
            "sharp right" in t                                  -> NavigationState.Direction.SHARP_RIGHT
            "keep left" in t                                    -> NavigationState.Direction.KEEP_LEFT
            "keep right" in t                                   -> NavigationState.Direction.KEEP_RIGHT
            ("fork" in t || "ramp" in t) && "left" in t        -> NavigationState.Direction.FORK_LEFT
            ("fork" in t || "ramp" in t) && "right" in t       -> NavigationState.Direction.FORK_RIGHT
            "left" in t                                         -> NavigationState.Direction.LEFT
            "right" in t                                        -> NavigationState.Direction.RIGHT
            else                                                -> NavigationState.Direction.STRAIGHT
        }
    }

    fun label(direction: NavigationState.Direction): String = when (direction) {
        NavigationState.Direction.STRAIGHT        -> "ĐI THẲNG"
        NavigationState.Direction.SLIGHT_LEFT     -> "CHẾCH TRÁI"
        NavigationState.Direction.LEFT            -> "RẼ TRÁI"
        NavigationState.Direction.SHARP_LEFT      -> "QUẶT TRÁI"
        NavigationState.Direction.SLIGHT_RIGHT    -> "CHẾCH PHẢI"
        NavigationState.Direction.RIGHT           -> "RẼ PHẢI"
        NavigationState.Direction.SHARP_RIGHT     -> "QUẶT PHẢI"
        NavigationState.Direction.U_TURN          -> "QUAY ĐẦU"
        NavigationState.Direction.KEEP_LEFT       -> "GIỮ TRÁI"
        NavigationState.Direction.KEEP_RIGHT      -> "GIỮ PHẢI"
        NavigationState.Direction.FORK_LEFT       -> "PHÂN LỐI TRÁI"
        NavigationState.Direction.FORK_RIGHT      -> "PHÂN LỐI PHẢI"
        NavigationState.Direction.RAMP_LEFT       -> "LÊN ĐƯỜNG TRÁI"
        NavigationState.Direction.RAMP_RIGHT      -> "LÊN ĐƯỜNG PHẢI"
        NavigationState.Direction.MERGE           -> "NHẬP LÀN"
        NavigationState.Direction.ROUNDABOUT      -> "VÒNG XUYẾN"
        NavigationState.Direction.EXIT_ROUNDABOUT -> "RA VÒNG XUYẾN"
        NavigationState.Direction.FERRY           -> "LÊN PHÀ"
        NavigationState.Direction.DESTINATION     -> "ĐIỂM ĐẾN"
    }
}
