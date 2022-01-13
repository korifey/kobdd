package org.kobdd

import org.junit.jupiter.api.Test
import java.io.File

class TestKosat {
    @Test
    fun testCnfExamples() {
        val cnfFiles = File("src/test/resources/cnf-examples")
        for (file in cnfFiles.walkTopDown()) {
            if (file.extension != "cnf") continue
            if (!file.absolutePath.contains("unsolved")) continue
            file.inputStream().use { stream ->
                processCnfRequests(readCnfRequests(stream))
            }
        }
    }
}