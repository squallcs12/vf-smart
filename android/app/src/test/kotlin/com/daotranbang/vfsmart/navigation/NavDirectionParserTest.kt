package com.daotranbang.vfsmart.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests NavDirectionParser.parse() against real-world Google Maps notification strings.
 *
 * Notification fields observed across Maps versions:
 *   TICKER : full instruction, e.g. "Turn left onto Lê Lợi in 300 m"
 *   TITLE  : distance ("300 m") OR maneuver ("Turn left") depending on version
 *   TEXT   : street name ("Lê Lợi") OR secondary instruction
 *
 * allText fed to the parser = "$ticker $title $subText $bodyText"
 */
class NavDirectionParserTest {

    private data class Case(
        val description: String,        // what Google Maps shows
        val notificationText: String,   // allText as constructed in the service
        val expected: NavigationState.Direction
    )

    private val cases = listOf(
        // ── Basic turns ────────────────────────────────────────────────────
        Case("Turn left",
            "Turn left onto Nguyễn Huệ in 300 m Turn left Nguyễn Huệ",
            NavigationState.Direction.LEFT),

        Case("Turn right",
            "Turn right onto Lê Lợi in 500 m Turn right Lê Lợi",
            NavigationState.Direction.RIGHT),

        Case("Continue straight / Head",
            "Head north on Đinh Tiên Hoàng 200 m Đinh Tiên Hoàng",
            NavigationState.Direction.STRAIGHT),

        // ── Slight turns ───────────────────────────────────────────────────
        Case("Turn slight left",
            "Turn slight left onto Hai Bà Trưng in 400 m Turn slight left Hai Bà Trưng",
            NavigationState.Direction.SLIGHT_LEFT),

        Case("Turn slight right",
            "Turn slight right onto Trần Phú in 250 m Turn slight right Trần Phú",
            NavigationState.Direction.SLIGHT_RIGHT),

        // ── Sharp turns ────────────────────────────────────────────────────
        Case("Turn sharp left",
            "Turn sharp left 150 m Turn sharp left",
            NavigationState.Direction.SHARP_LEFT),

        Case("Turn sharp right",
            "Turn sharp right 200 m Turn sharp right",
            NavigationState.Direction.SHARP_RIGHT),

        // ── U-turn ─────────────────────────────────────────────────────────
        Case("Make a U-turn",
            "Make a U-turn 100 m U-turn",
            NavigationState.Direction.U_TURN),

        Case("U-turn (short form)",
            "Uturn when possible 80 m",
            NavigationState.Direction.U_TURN),

        // ── Keep ───────────────────────────────────────────────────────────
        Case("Keep left",
            "Keep left 600 m Keep left",
            NavigationState.Direction.KEEP_LEFT),

        Case("Keep right",
            "Keep right onto ramp 400 m Keep right",
            NavigationState.Direction.KEEP_RIGHT),

        // ── Fork ───────────────────────────────────────────────────────────
        Case("Take the left fork",
            "Take the left fork 800 m left fork",
            NavigationState.Direction.FORK_LEFT),

        Case("Take the right fork",
            "Take the right fork 350 m right fork",
            NavigationState.Direction.FORK_RIGHT),

        // ── Ramp ───────────────────────────────────────────────────────────
        Case("Take the ramp on the left",
            "Take the ramp on the left 1.2 km ramp left",
            NavigationState.Direction.FORK_LEFT),

        Case("Take the ramp on the right",
            "Take the ramp on the right 900 m ramp right",
            NavigationState.Direction.FORK_RIGHT),

        // ── Merge ──────────────────────────────────────────────────────────
        Case("Merge onto highway",
            "Merge onto Quốc lộ 1 in 500 m Merge Quốc lộ 1",
            NavigationState.Direction.MERGE),

        // ── Roundabout ─────────────────────────────────────────────────────
        Case("At the roundabout",
            "At the roundabout take the 2nd exit 300 m roundabout",
            NavigationState.Direction.ROUNDABOUT),

        Case("Rotary",
            "At the rotary take the 1st exit 200 m rotary",
            NavigationState.Direction.ROUNDABOUT),

        Case("Exit the roundabout",
            "Exit the roundabout 100 m exit roundabout",
            NavigationState.Direction.EXIT_ROUNDABOUT),

        // ── Ferry ──────────────────────────────────────────────────────────
        Case("Take the ferry",
            "Take the ferry 2.1 km ferry",
            NavigationState.Direction.FERRY),

        // ── Destination ────────────────────────────────────────────────────
        Case("You have arrived",
            "You have arrived at your destination destination",
            NavigationState.Direction.DESTINATION),

        Case("Destination on left",
            "Your destination is on the left arrived",
            NavigationState.Direction.DESTINATION),
    )

    @Test
    fun testAllDirections() {
        val passed = mutableListOf<Case>()
        val failed = mutableListOf<Pair<Case, NavigationState.Direction>>()

        for (case in cases) {
            val result = NavDirectionParser.parse(case.notificationText)
            if (result == case.expected) passed.add(case)
            else failed.add(case to result)
        }

        // Print table
        println("\n╔══════════════════════════════════════╦══════════════════════╦══════════════════════╦════════╗")
        println(  "║ Notification sample                  ║ Expected             ║ Got                  ║ Result ║")
        println(  "╠══════════════════════════════════════╬══════════════════════╬══════════════════════╬════════╣")
        for (case in cases) {
            val got = NavDirectionParser.parse(case.notificationText)
            val pass = got == case.expected
            println("║ %-36s ║ %-20s ║ %-20s ║ %-6s ║".format(
                case.description.take(36),
                case.expected.name.take(20),
                got.name.take(20),
                if (pass) "✓ PASS" else "✗ FAIL"
            ))
        }
        println("╚══════════════════════════════════════╩══════════════════════╩══════════════════════╩════════╝")
        println("\nPassed: ${passed.size}/${cases.size}")
        if (failed.isNotEmpty()) {
            println("Failed:")
            failed.forEach { (case, got) ->
                println("  - ${case.description}: expected ${case.expected}, got $got")
                println("    Input: \"${case.notificationText}\"")
            }
        }

        assertEquals("All cases should pass", 0, failed.size)
    }
}
