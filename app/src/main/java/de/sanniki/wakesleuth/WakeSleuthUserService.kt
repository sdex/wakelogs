package de.sanniki.wakesleuth

import java.io.BufferedReader
import java.io.InputStreamReader

class WakeSleuthUserService : IWakeSleuthShell.Stub() {

    override fun runCommand(command: String): String {
        val process = Runtime.getRuntime().exec(
            arrayOf(
                "sh",
                "-c",
                command
            )
        )

        val stdout = BufferedReader(
            InputStreamReader(process.inputStream)
        ).use { reader ->
            reader.readText()
        }

        val stderr = BufferedReader(
            InputStreamReader(process.errorStream)
        ).use { reader ->
            reader.readText()
        }

        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw IllegalStateException(
                stderr.ifBlank {
                    "Shell-Befehl fehlgeschlagen: $exitCode"
                }
            )
        }

        return stdout
    }
}
