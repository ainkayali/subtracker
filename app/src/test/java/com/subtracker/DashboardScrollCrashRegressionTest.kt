package com.subtracker

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class DashboardScrollCrashRegressionTest {
    @Test
    fun `dashboard lazy rows do not use Material3 SwipeToDismissBox`() {
        val source = dashboardSource().readText()

        assertFalse(
            "Dashboard LazyColumn should not wrap rows in SwipeToDismissBox; it regressed into a scroll crash on device.",
            source.contains("SwipeToDismissBox(")
        )
    }

    private fun dashboardSource(): File =
        sequenceOf(
            File("src/main/java/com/subtracker/ui/DashboardScreen.kt"),
            File("app/src/main/java/com/subtracker/ui/DashboardScreen.kt")
        ).first { it.exists() }
}
