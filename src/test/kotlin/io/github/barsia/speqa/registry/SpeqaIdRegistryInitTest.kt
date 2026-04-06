package io.github.barsia.speqa.registry

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SpeqaIdRegistryInitTest : BasePlatformTestCase() {

    fun `test ensureInitialized schedules first scan asynchronously`() {
        val registry = SpeqaIdRegistry(project)
        val scanStarted = CountDownLatch(1)
        val releaseScan = CountDownLatch(1)

        registry.scanTask = {
            scanStarted.countDown()
            releaseScan.await(5, TimeUnit.SECONDS)
        }

        registry.ensureInitialized()

        assertEquals(true, scanStarted.await(5, TimeUnit.SECONDS))
        assertEquals(true, registry.initialized)
        releaseScan.countDown()
    }
}
