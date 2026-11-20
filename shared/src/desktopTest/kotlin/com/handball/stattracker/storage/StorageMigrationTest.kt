package com.handball.stattracker.storage

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StorageMigrationTest {
    private val root: File = Files.createTempDirectory("migration-test").toFile()
    private val old = File(root, "cache").apply { mkdirs() }
    private val new = File(root, "files")

    @AfterTest
    fun cleanup() { root.deleteRecursively() }

    @Test
    fun movesAllFilesAndRemovesTheOldOnes() {
        File(old, "snapshot.json").writeText("{\"a\":1}")
        File(old, "kv-owner.txt").writeText("user-1")

        assertEquals(2, StorageMigration.moveFiles(old, new))

        assertEquals("{\"a\":1}", File(new, "snapshot.json").readText())
        assertEquals("user-1", File(new, "kv-owner.txt").readText())
        assertTrue(old.listFiles()!!.isEmpty(), "Der alte Cache-Ordner muss leer sein")
    }

    @Test
    fun existingFilesInTheTargetAreNeverOverwritten() {
        File(old, "snapshot.json").writeText("ALT")
        new.mkdirs()
        File(new, "snapshot.json").writeText("NEU")

        assertEquals(0, StorageMigration.moveFiles(old, new))

        assertEquals("NEU", File(new, "snapshot.json").readText())
        assertFalse(File(old, "snapshot.json").exists(), "Die veraltete Kopie wird entfernt")
    }

    @Test
    fun missingOrEmptySourceIsHarmless() {
        assertEquals(0, StorageMigration.moveFiles(File(root, "gibt-es-nicht"), new))
        assertEquals(0, StorageMigration.moveFiles(old, new))
        assertFalse(new.exists(), "Ohne Dateien wird kein Zielordner angelegt")
    }

    @Test
    fun sameDirectoryIsLeftAlone() {
        File(old, "snapshot.json").writeText("X")
        assertEquals(0, StorageMigration.moveFiles(old, old))
        assertEquals("X", File(old, "snapshot.json").readText())
    }

    @Test
    fun migrationIsIdempotent() {
        File(old, "snapshot.json").writeText("X")
        assertEquals(1, StorageMigration.moveFiles(old, new))
        assertEquals(0, StorageMigration.moveFiles(old, new))
        assertEquals("X", File(new, "snapshot.json").readText())
    }
}
