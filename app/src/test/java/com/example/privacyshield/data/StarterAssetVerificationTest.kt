package com.example.privacyshield.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

class StarterAssetVerificationTest {

    @Test
    fun verifiesStarterHostsBinParsesSuccessfully() {
        val binFile = File("src/main/assets/starter_hosts.bin")
        assertTrue("starter_hosts.bin must exist", binFile.exists())

        var count = 0
        GZIPInputStream(FileInputStream(binFile)).use { gz ->
            BufferedReader(InputStreamReader(gz, Charsets.UTF_8)).useLines { lines ->
                for (line in lines) {
                    val domain = line.trim()
                    if (domain.isNotEmpty()) {
                        count++
                    }
                }
            }
        }

        assertTrue("Parsed domains should be > 200,000, actual: $count", count > 200000)
    }
}
