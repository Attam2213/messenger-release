package com.example.messenger.crypto

data class MemoryIdentity(
    val publicKeyBase64: String,
    val privateKeyBase64: String
)

interface CryptoManager {
    fun getMyPublicKeyString(): String
    fun getMyPrivateKeyString(): String
    suspend fun sign(data: String): String
    suspend fun verify(data: String, signatureBase64: String, publicKeyString: String): Boolean
    
    fun hasIdentity(): Boolean
    fun reloadKeys()
    suspend fun createIdentity()
    suspend fun clearIdentity()

    // RSA Encryption/Decryption
    suspend fun decrypt(encryptedData: String): String
    suspend fun encrypt(data: String, publicKey: String): String

    // AES Encryption/Decryption
    suspend fun decryptAes(data: ByteArray, key: ByteArray): ByteArray
    suspend fun encryptAes(data: ByteArray, key: ByteArray): ByteArray
    fun generateAesKey(): ByteArray
    
    // Hashing
    fun getMyPublicKeyHash(): String
    fun getHashFromPublicKeyString(key: String): String
    
    // Identity Management
    fun createIdentityInMemory(): MemoryIdentity
    suspend fun importIdentity(privateKeyBase64: String, publicKeyBase64: String)
    suspend fun importIdentity(privateKeyBase64: String)
    
    // PBE
    suspend fun encryptWithPassword(data: String, password: String): String
    suspend fun decryptWithPassword(encryptedData: String, password: String): String
    
    fun getDatabasePassphrase(): String
}