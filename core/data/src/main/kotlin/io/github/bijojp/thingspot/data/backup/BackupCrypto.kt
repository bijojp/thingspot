package io.github.bijojp.thingspot.data.backup

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password protection for Backup files (ADR 0001; docs/backup-format.md).
 *
 * Layout: "TSPOTENC" | version (1 byte) | salt (16) | PBKDF2 iterations (int) | nonce prefix (8)
 * then chunks of [length (int) | AES-256-GCM ciphertext+tag]. Each chunk's nonce is the prefix
 * plus its index, and its associated data is the header, its index and a "last chunk" flag,
 * so reordering, truncation and tampering all fail. Chunking keeps memory flat on big backups.
 */
object BackupCrypto {
    private val MAGIC = "TSPOTENC".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val SALT_BYTES = 16
    private const val PREFIX_BYTES = 8
    private const val TAG_BITS = 128
    private const val CHUNK_BYTES = 1 shl 20
    const val ITERATIONS = 600_000

    class WrongPassword : Exception("Wrong password, or the file was changed")

    /** A key made from a password; the salt and iteration count travel with every backup. */
    class PasswordKey(val key: SecretKey, val salt: ByteArray, val iterations: Int)

    fun newPasswordKey(password: CharArray, iterations: Int = ITERATIONS): PasswordKey {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        return PasswordKey(deriveKey(password, salt, iterations), salt, iterations)
    }

    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /** Whether [head] (the first bytes of a file) starts like an encrypted Backup. */
    fun isEncrypted(head: ByteArray): Boolean = head.size >= MAGIC.size && head.copyOf(MAGIC.size).contentEquals(MAGIC)

    fun encrypt(input: InputStream, output: OutputStream, key: PasswordKey) {
        val prefix = ByteArray(PREFIX_BYTES).also(SecureRandom()::nextBytes)
        val header = header(key.salt, key.iterations, prefix)
        val out = DataOutputStream(output.buffered())
        out.write(header)

        val source = input.buffered()
        var current = source.readChunk()
        var index = 0
        while (true) {
            val next = if (current.size < CHUNK_BYTES) null else source.readChunk().takeIf { it.isNotEmpty() }
            val sealed = cipher(Cipher.ENCRYPT_MODE, key.key, prefix, index, header, last = next == null).doFinal(current)
            out.writeInt(sealed.size)
            out.write(sealed)
            if (next == null) break
            current = next
            index++
        }
        out.flush()
    }

    /** Decrypts with [password]; throws [WrongPassword] if it's wrong or the file was altered. */
    fun decrypt(input: InputStream, output: OutputStream, password: CharArray) {
        val source = DataInputStream(input.buffered())
        val magic = ByteArray(MAGIC.size).also(source::readFully)
        require(magic.contentEquals(MAGIC)) { "Not an encrypted backup" }
        val version = source.readByte()
        require(version == VERSION) { "Unknown encryption version $version" }
        val salt = ByteArray(SALT_BYTES).also(source::readFully)
        val iterations = source.readInt()
        val prefix = ByteArray(PREFIX_BYTES).also(source::readFully)
        val header = header(salt, iterations, prefix)
        val key = deriveKey(password, salt, iterations)

        var sealed = source.readSealedChunk() ?: throw WrongPassword()
        var index = 0
        while (true) {
            val next = source.readSealedChunk()
            val plain = try {
                cipher(Cipher.DECRYPT_MODE, key, prefix, index, header, last = next == null).doFinal(sealed)
            } catch (_: AEADBadTagException) {
                throw WrongPassword()
            }
            output.write(plain)
            if (next == null) break
            sealed = next
            index++
        }
        output.flush()
    }

    private fun header(salt: ByteArray, iterations: Int, prefix: ByteArray): ByteArray =
        ByteBuffer.allocate(MAGIC.size + 1 + SALT_BYTES + 4 + PREFIX_BYTES)
            .put(MAGIC).put(VERSION).put(salt).putInt(iterations).put(prefix)
            .array()

    private fun cipher(mode: Int, key: SecretKey, prefix: ByteArray, index: Int, header: ByteArray, last: Boolean): Cipher {
        val nonce = ByteBuffer.allocate(PREFIX_BYTES + 4).put(prefix).putInt(index).array()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(ByteBuffer.allocate(header.size + 5).put(header).putInt(index).put(if (last) 1 else 0).array())
        }
    }

    private fun InputStream.readChunk(): ByteArray {
        val buffer = ByteArray(CHUNK_BYTES)
        var filled = 0
        while (filled < CHUNK_BYTES) {
            val read = read(buffer, filled, CHUNK_BYTES - filled)
            if (read < 0) break
            filled += read
        }
        return buffer.copyOf(filled)
    }

    private fun DataInputStream.readSealedChunk(): ByteArray? {
        val length = try {
            readInt()
        } catch (_: EOFException) {
            return null
        }
        require(length in 16..(CHUNK_BYTES + 16)) { "Bad chunk length" }
        return ByteArray(length).also(::readFully)
    }
}
