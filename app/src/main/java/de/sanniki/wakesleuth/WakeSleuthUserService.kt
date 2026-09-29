package de.sanniki.wakesleuth

import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class WakeSleuthUserService : IWakeSleuthShell.Stub() {
    companion object {
        const val EXIT_CODE_ERROR_PREFIX = "wakelogs_shell_exit_code:"

        internal const val COMMAND_TIMEOUT_MILLIS = 60_000L
    }

    override fun runCommand(command: String): String = ShellRunner.run(arrayOf("sh", "-c", command))
}

/** Process execution without a pipe deadlock and with a hard timeout. */
internal object ShellRunner {
    fun run(
        args: Array<String>,
        timeoutMillis: Long = WakeSleuthUserService.COMMAND_TIMEOUT_MILLIS,
    ): String {
        val process = ProcessBuilder(*args).start()

        try {
            // Both streams are drained at the same time; reading one after
            // the other blocks once the other pipe buffer is full.
            val stdout = StringBuilder()
            val stderr = StringBuilder()

            val outReader = thread(isDaemon = true) {
                runCatching { stdout.append(process.inputStream.bufferedReader().readText()) }
            }

            val errReader = thread(isDaemon = true) {
                runCatching { stderr.append(process.errorStream.bufferedReader().readText()) }
            }

            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()

                throw IllegalStateException("Command timed out after $timeoutMillis ms")
            }

            outReader.join(timeoutMillis)
            errReader.join(timeoutMillis)

            val exitCode = process.exitValue()

            if (exitCode != 0) {
                throw IllegalStateException(
                    stderr.toString().ifBlank {
                        WakeSleuthUserService.EXIT_CODE_ERROR_PREFIX + exitCode
                    },
                )
            }

            return stdout.toString()
        } finally {
            process.destroyForcibly()
        }
    }
}
