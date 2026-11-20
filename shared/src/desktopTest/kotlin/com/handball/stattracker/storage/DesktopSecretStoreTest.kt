package com.handball.stattracker.storage

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopSecretStoreTest {
    private val dir: File = Files.createTempDirectory("secret-store-test").toFile()

    @AfterTest
    fun cleanup() { dir.deleteRecursively() }

    private fun store(d: File = dir) = DesktopSecretStore(d, listOf(AesFileKeyProtector(File(d, "secret.key"))))

    private val token = """{"accessToken":"eyJhbGciOiJIUzI1NiJ9.geheim","refreshToken":"r-12345"}"""

    @Test
    fun roundTripReturnsTheOriginalValue() {
        val s = store()
        assertTrue(s.write("session", token))
        assertEquals(token, s.read("session"))
    }

    @Test
    fun fileOnDiskDoesNotContainThePlaintext() {
        store().write("session", token)
        val onDisk = dir.listFiles()!!.filter { it.name.startsWith("secret-") }.single().readText()
        assertFalse("geheim" in onDisk, "Token darf nicht im Klartext auf der Platte liegen")
        assertFalse("r-12345" in onDisk)
        assertTrue(onDisk.startsWith("aes1:"))
    }

    @Test
    fun valueSurvivesANewStoreInstanceWithTheSameKeyFile() {
        store().write("session", token)
        assertEquals(token, store().read("session"))
    }

    @Test
    fun anotherKeyFileCannotDecrypt() {
        store().write("session", token)
        File(dir, "secret.key").delete()
        // neuer, zufälliger Schlüssel: das alte Chiffrat ist nicht mehr lesbar
        assertNull(DesktopSecretStore(dir, listOf(AesFileKeyProtector(File(dir, "secret.key")))).also { it.write("other", "x") }.read("session"))
    }

    @Test
    fun tamperedFileIsRejectedInsteadOfReturningGarbage() {
        store().write("session", token)
        val file = dir.listFiles()!!.first { it.name.startsWith("secret-") }
        val text = file.readText()
        file.writeText(text.dropLast(6) + "AAAAAA")
        assertNull(store().read("session"))
    }

    @Test
    fun writingNullDeletesTheEntry() {
        val s = store()
        s.write("session", token)
        assertTrue(s.write("session", null))
        assertNull(s.read("session"))
        assertTrue(dir.listFiles()!!.none { it.name.startsWith("secret-") })
    }

    @Test
    fun missingEntryReadsAsNull() {
        assertNull(store().read("gibt-es-nicht"))
    }

    @Test
    fun fallsBackToNextProtectorWhenPreferredIsUnavailable() {
        val unavailable = object : SecretProtector {
            override val tag = "dpapi"
            override fun protect(plain: ByteArray): ByteArray? = null
            override fun unprotect(data: ByteArray): ByteArray? = null
        }
        val s = DesktopSecretStore(dir, listOf(unavailable, AesFileKeyProtector(File(dir, "secret.key"))))
        assertTrue(s.write("session", token))
        assertEquals(token, s.read("session"))
    }

    @Test
    fun nothingIsStoredWhenNoProtectorWorks() {
        val unavailable = object : SecretProtector {
            override val tag = "none"
            override fun protect(plain: ByteArray): ByteArray? = null
            override fun unprotect(data: ByteArray): ByteArray? = null
        }
        val s = DesktopSecretStore(dir, listOf(unavailable))
        assertFalse(s.write("session", token))
        assertTrue(dir.listFiles()!!.isEmpty(), "Es darf keine Klartext-Ersatzdatei entstehen")
    }

    @Test
    fun keyFileIsRestrictedToTheOwnerOnPosixSystems() {
        store().write("session", token)
        val perms = runCatching { Files.getPosixFilePermissions(File(dir, "secret.key").toPath()) }.getOrNull() ?: return
        assertEquals(setOf(java.nio.file.attribute.PosixFilePermission.OWNER_READ, java.nio.file.attribute.PosixFilePermission.OWNER_WRITE), perms)
    }
}
