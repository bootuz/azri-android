package nart.simpleanki

import org.junit.Assert.assertEquals
import org.junit.Test

/** Toolchain smoke test: confirms the JVM unit-test pipeline runs. */
class SmokeTest {
    @Test
    fun arithmetic_holds() {
        assertEquals(4, 2 + 2)
    }
}
