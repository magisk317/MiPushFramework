package io.github.magisk317.mipush.service

import android.content.Context
import io.github.magisk317.mipush.testing.DiskStringPreferences
import io.github.magisk317.mipush.testing.diskPreferencesContext
import io.github.magisk317.xposed.logging.XLog
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class RegistrationRevocationJournalTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `unfinished revocation survives new journal context and is isolated by user and package`() {
        val first = diskPreferencesContext(directory)
        assertTrue(RegistrationRevocationJournal.begin(first, "target.app", 0, "primary-id"))
        assertTrue(RegistrationRevocationJournal.begin(first, "target.app", 999, "clone-id"))
        assertTrue(RegistrationRevocationJournal.begin(first, "other.app", 0, "other-id"))

        val reopened = diskPreferencesContext(directory)
        assertEquals("primary-id", RegistrationRevocationJournal.pendingAppId(reopened, "target.app", 0))
        assertEquals("clone-id", RegistrationRevocationJournal.pendingAppId(reopened, "target.app", 999))
        assertTrue(RegistrationRevocationJournal.complete(reopened, "target.app", 0))

        val completed = diskPreferencesContext(directory)
        assertFalse(RegistrationRevocationJournal.hasPending(completed, "target.app", 0))
        assertEquals("clone-id", RegistrationRevocationJournal.pendingAppId(completed, "target.app", 999))
        assertEquals("other-id", RegistrationRevocationJournal.pendingAppId(completed, "other.app", 0))
    }

    @Test
    fun `failed completion preserves the same-process fence and the durable retry`() {
        val path = directory.resolve("journal.properties")
        val prefs = DiskStringPreferences(path)
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns prefs
        assertTrue(RegistrationRevocationJournal.begin(context, "target.app", 0, "app-id"))
        prefs.failWrites = true
        val logLevel = XLog.getLogLevel()
        XLog.setLogLevel(Int.MAX_VALUE)
        try {
            assertFalse(RegistrationRevocationJournal.complete(context, "target.app", 0))
        } finally { XLog.setLogLevel(logLevel) }
        assertTrue(RegistrationRevocationJournal.hasPending(context, "target.app", 0))
        assertEquals("app-id", DiskStringPreferences(path).getString("0:target.app", null))
        prefs.failWrites = false
        assertTrue(RegistrationRevocationJournal.complete(context, "target.app", 0))
        assertFalse(DiskStringPreferences(path).contains("0:target.app"))
    }

    @Test
    fun `failed begin is reported before any durable revocation exists`() {
        val path = directory.resolve("failed.properties")
        val prefs = DiskStringPreferences(path).apply { failWrites = true }
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns prefs

        assertFalse(RegistrationRevocationJournal.begin(context, "target.app", 0, "app-id"))
        assertFalse(DiskStringPreferences(path).contains("0:target.app"))
    }
}
