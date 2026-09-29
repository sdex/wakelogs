package de.sanniki.wakesleuth

import java.io.BufferedReader
import java.io.InputStreamReader

class WakeSleuthUserService : IWakeSleuthShell.Stub() {

    companion object {
        const val EXIT_CODE_ERROR_PREFIX =
            "wakelogs_shell_exit_code:"
    }

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
                    EXIT_CODE_ERROR_PREFIX + exitCode
                }
            )
        }

        return stdout
    }
}
