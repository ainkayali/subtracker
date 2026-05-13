package com.subtracker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class StableRollbackRegressionTest {
    @Test
    fun appSourceMatchesStableV132RollbackBaseline() {
        val expectedHashes = mapOf(
            "src/main/java/com/subtracker/App.kt" to
                "fe7dfd5228ce03a40ffe9e395b3b989818a310cf4b56e838dbfb23d48c6ab05b",
            "src/main/java/com/subtracker/Db.kt" to
                "30422e0cba189d3ee3edf97b399b6baff4e82b84aa541aee176e106855e2b87b",
            "src/main/java/com/subtracker/MainActivity.kt" to
                "6d23304fdf7b6ad1d9d23dcbe3a3f2c34fe20b1e8d811aa99af8527e23a3cd82",
            "src/main/java/com/subtracker/SubViewModel.kt" to
                "8878c1662d3f2b09cf8605ed527d57df6bbd9e41535ce78a7c7a61df43488526",
            "src/main/java/com/subtracker/detect/DetectionReconciler.kt" to
                "056c2af461cc6178947c140030a8309e45b0b5a3eec4c7dd955ca9bdc802f412",
            "src/main/java/com/subtracker/ui/BackfillScreen.kt" to
                "04639ee0d64595cf8aca7786d237cc744fea57bbed19527e744786521f2be721",
            "src/main/java/com/subtracker/ui/DashboardScreen.kt" to
                "f9b810c6fb27ef295cf716f0a4660d66bc667782ebc78f6615839804898b9105",
        )

        expectedHashes.forEach { (relativePath, expectedHash) ->
            val sourcePath = Path.of(relativePath)
            val actualHash = sha256(Files.readAllBytes(sourcePath))
            assertEquals("$relativePath should match the v1.3.2 stable rollback baseline", expectedHash, actualHash)
        }
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
