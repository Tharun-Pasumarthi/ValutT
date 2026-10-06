package com.vaultt.core.security

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SecurityManagerTest {

    private lateinit var securityManager: SecurityManager

    @Before
    fun setup() {
        securityManager = SecurityManager()
    }

    @Test
    fun `initial state is locked`() {
        assertTrue(securityManager.isLocked.value)
    }

    @Test
    fun `unlock changes state to unlocked`() = runTest {
        securityManager.unlock()
        assertFalse(securityManager.isLocked.value)
    }

    @Test
    fun `lock changes state to locked`() = runTest {
        securityManager.unlock()
        securityManager.lock()
        assertTrue(securityManager.isLocked.value)
    }
}
