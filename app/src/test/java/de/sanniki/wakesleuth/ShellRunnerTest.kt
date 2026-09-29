package de.sanniki.wakesleuth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ShellRunnerTest {
    private fun sh(script: String) = arrayOf("sh", "-c", script)

    @Test
    fun `returns stdout of a successful command`() {
        assertEquals("hello\n", ShellRunner.run(sh("echo hello")))
    }

    @Test
    fun `large stderr does not deadlock the command`() {
        // More than any pipe buffer on stderr before stdout is written.
        val output = ShellRunner.run(sh("head -c 300000 /dev/zero | tr '\\0' 'e' >&2; echo done"), timeoutMillis = 20_000)

        assertEquals("done\n", output)
    }

    @Test
    fun `failure reports stderr`() {
        try {
            ShellRunner.run(sh("echo boom >&2; exit 3"))
            fail("expected failure")
        } catch (expected: IllegalStateException) {
            assertEquals("boom\n", expected.message)
        }
    }

    @Test
    fun `failure without stderr reports the exit code`() {
        try {
            ShellRunner.run(sh("exit 7"))
            fail("expected failure")
        } catch (expected: IllegalStateException) {
            assertEquals(WakeSleuthUserService.EXIT_CODE_ERROR_PREFIX + 7, expected.message)
        }
    }

    @Test
    fun `hanging command is killed after the timeout`() {
        val started = System.nanoTime()

        try {
            ShellRunner.run(sh("sleep 30"), timeoutMillis = 300)
            fail("expected timeout")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("timed out"))
        }

        assertTrue((System.nanoTime() - started) / 1_000_000 < 10_000)
    }
}
