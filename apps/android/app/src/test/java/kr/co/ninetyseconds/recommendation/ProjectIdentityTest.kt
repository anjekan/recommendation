package kr.co.ninetyseconds.recommendation

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectIdentityTest {
    @Test
    fun packageNamespace_isStable() {
        assertEquals(
            "kr.co.ninetyseconds.recommendation",
            ProjectIdentityTest::class.java.packageName,
        )
    }
}
