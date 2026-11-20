package com.handball.stattracker.storage

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Verschlüsselt/entschlüsselt kleine Geheimnisse (Login-Token). */
internal interface SecretProtector {
    /** Kennung, die vor dem Chiffrat gespeichert wird, damit beim Lesen der richtige Schutz gewählt wird. */
    val tag: String

    /** null = Schutz auf diesem System nicht möglich (der Aufrufer nimmt den nächsten). */
    fun protect(plain: ByteArray): ByteArray?

    /** null = nicht entschlüsselbar (anderes Konto, beschädigt, Schlüssel fehlt). */
    fun unprotect(data: ByteArray): ByteArray?
}

/**
 * AES-256-GCM mit einem zufälligen Schlüssel in einer Datei, die nur der aktuelle Benutzer lesen darf.
 * Plattformunabhängig, aber der Schlüssel liegt neben den Daten: schützt vor versehentlichem Kopieren
 * (Backup, Synchronisation, Weitergabe der Datei), nicht vor Schadsoftware desselben Benutzers.
 */
internal class AesFileKeyProtector(private val keyFile: File) : SecretProtector {
    override val tag = "aes1"
    private val random = SecureRandom()

    private fun key(create: Boolean): SecretKeySpec? {
        if (!keyFile.exists()) {
            if (!create) return null
            val bytes = ByteArray(32).also { random.nextBytes(it) }
            keyFile.parentFile?.mkdirs()
            keyFile.writeBytes(bytes)
            restrictToOwner(keyFile)
        }
        val bytes = keyFile.readBytes()
        return if (bytes.size == 32) SecretKeySpec(bytes, "AES") else null
    }

    override fun protect(plain: ByteArray): ByteArray? = runCatching {
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(create = true), GCMParameterSpec(128, iv))
        iv + cipher.doFinal(plain)
    }.getOrNull()

    override fun unprotect(data: ByteArray): ByteArray? = runCatching {
        val k = key(create = false) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, data.copyOfRange(0, 12)))
        cipher.doFinal(data, 12, data.size - 12)
    }.getOrNull()

    private fun restrictToOwner(file: File) {
        runCatching {
            Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------"))
        }.onFailure {
            // Windows: kein POSIX, ersatzweise nur für den Besitzer lesbar/schreibbar
            file.setReadable(false, false); file.setReadable(true, true)
            file.setWritable(false, false); file.setWritable(true, true)
        }
    }
}

/**
 * Windows DPAPI (`ProtectedData`, Bereich CurrentUser) über PowerShell. Der Schlüssel gehört zum
 * Windows-Benutzerkonto; die Datei lässt sich auf einem anderen Konto oder Rechner nicht entschlüsseln.
 * Schlägt der Aufruf fehl (kein PowerShell, Richtlinie, Zeitüberschreitung), liefert [protect] null.
 */
internal class WindowsDpapiProtector : SecretProtector {
    override val tag = "dpapi"

    override fun protect(plain: ByteArray): ByteArray? = run("Protect", plain)
    override fun unprotect(data: ByteArray): ByteArray? = run("Unprotect", data)

    private fun run(operation: String, input: ByteArray): ByteArray? = runCatching {
        if (!System.getProperty("os.name", "").contains("Windows", ignoreCase = true)) return null
        // Bewusst ohne Anführungszeichen, damit die Windows-Argumentübergabe nichts verändert.
        val script = "Add-Type -AssemblyName System.Security; " +
            "\$b=[Convert]::FromBase64String([Console]::In.ReadToEnd().Trim()); " +
            "\$r=[System.Security.Cryptography.ProtectedData]::$operation(\$b,\$null," +
            "[System.Security.Cryptography.DataProtectionScope]::CurrentUser); " +
            "[Convert]::ToBase64String(\$r)"
        val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        process.outputStream.use { it.write(Base64.getEncoder().encode(input)) }
        val output = process.inputStream.readBytes()
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        if (process.exitValue() != 0) return null
        Base64.getDecoder().decode(String(output, Charsets.UTF_8).trim())
    }.getOrNull()
}

/**
 * Speichert Geheimnisse als Datei `secret-<key>.bin` im Format `<tag>:<base64>`.
 * Geschrieben wird mit dem ersten funktionierenden Schutz aus [protectors]; gelesen mit dem
 * Schutz, dessen Kennung in der Datei steht.
 */
internal class DesktopSecretStore(
    private val dir: File,
    private val protectors: List<SecretProtector>
) {
    private fun file(key: String) = File(dir, "secret-" + key.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".bin")

    fun read(key: String): String? = runCatching {
        val f = file(key)
        if (!f.exists()) return null
        val text = f.readText(Charsets.UTF_8).trim()
        val tag = text.substringBefore(':')
        val protector = protectors.find { it.tag == tag } ?: return null
        val plain = protector.unprotect(Base64.getDecoder().decode(text.substringAfter(':'))) ?: return null
        String(plain, Charsets.UTF_8)
    }.getOrNull()

    /** @return false, wenn kein Schutz verfügbar war und deshalb **nichts** gespeichert wurde. */
    fun write(key: String, value: String?): Boolean {
        val f = file(key)
        if (value == null) {
            runCatching { f.delete() }
            return true
        }
        for (protector in protectors) {
            val sealed = protector.protect(value.toByteArray(Charsets.UTF_8)) ?: continue
            return runCatching {
                dir.mkdirs()
                f.writeText(protector.tag + ":" + Base64.getEncoder().encodeToString(sealed), Charsets.UTF_8)
                true
            }.getOrDefault(false)
        }
        return false
    }

    companion object {
        fun default(dir: File) = DesktopSecretStore(
            dir,
            listOf(WindowsDpapiProtector(), AesFileKeyProtector(File(dir, "secret.key")))
        )
    }
}
