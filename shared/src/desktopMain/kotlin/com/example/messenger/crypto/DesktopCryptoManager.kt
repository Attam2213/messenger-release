package com.example.messenger.crypto

import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DesktopCryptoManager : CryptoManager {
    // For Desktop we can store keys in user home directory
    private val keyDir = File(System.getProperty("user.home"), "AppData/Local/Messenger/keys")
    private val PRIVATE_KEY_FILE = File(keyDir, "identity_private.key")
    private val PUBLIC_KEY_FILE = File(keyDir, "identity_public.key")
    
    private var myKeyPair: KeyPair? = null

    init {
        if (!keyDir.exists()) {
            keyDir.mkdirs()
        }
        loadKeys()
    }

    private fun loadKeys() {
        if (PRIVATE_KEY_FILE.exists() && PUBLIC_KEY_FILE.exists()) {
            try {
                val privBytes = PRIVATE_KEY_FILE.readBytes()
                val pubBytes = PUBLIC_KEY_FILE.readBytes()

                val keyFactory = KeyFactory.getInstance("RSA")
                val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))

                myKeyPair = KeyPair(publicKey, privateKey)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun getMyPublicKeyString(): String {
        val pub = myKeyPair?.public ?: return ""
        // Java Base64 encoder (RFC 4648)
        return Base64.getEncoder().encodeToString(pub.encoded)
    }

    override fun getMyPrivateKeyString(): String {
        val priv = myKeyPair?.private ?: return ""
        return Base64.getEncoder().encodeToString(priv.encoded)
    }

    override suspend fun sign(data: String): String = withContext(Dispatchers.Default) {
        val privateKey = myKeyPair?.private ?: throw Exception("No private key loaded")
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(data.toByteArray())
        val signatureBytes = signature.sign()
        Base64.getEncoder().encodeToString(signatureBytes)
    }

    override suspend fun verify(data: String, signatureBase64: String, publicKeyString: String): Boolean = withContext(Dispatchers.Default) {
        try {
            val publicKeyBytes = Base64.getDecoder().decode(publicKeyString)
            val keySpec = X509EncodedKeySpec(publicKeyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(keySpec)

            val signature = Signature.getInstance("SHA256withRSA")
            signature.initVerify(publicKey)
            signature.update(data.toByteArray())
            val signatureBytes = Base64.getDecoder().decode(signatureBase64)
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun hasIdentity(): Boolean {
        return myKeyPair != null
    }

    override fun reloadKeys() {
        loadKeys()
    }

    override suspend fun createIdentity() = withContext(Dispatchers.IO) {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        
        PRIVATE_KEY_FILE.writeBytes(kp.private.encoded)
        PUBLIC_KEY_FILE.writeBytes(kp.public.encoded)
        
        myKeyPair = kp
    }

    override suspend fun clearIdentity() = withContext(Dispatchers.IO) {
        PRIVATE_KEY_FILE.delete()
        PUBLIC_KEY_FILE.delete()
        myKeyPair = null
    }

    override fun getDatabasePassphrase(): String {
        val priv = myKeyPair?.private
        if (priv != null) {
            val md = MessageDigest.getInstance("SHA-256")
            val hashBytes = md.digest(priv.encoded)
            return Base64.getEncoder().encodeToString(hashBytes)
        }
        return "default_insecure_passphrase"
    }

    override suspend fun decrypt(encryptedData: String): String = withContext(Dispatchers.Default) {
        val privateKey = myKeyPair?.private ?: throw Exception("No private key")
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        val encryptedBytes = Base64.getDecoder().decode(encryptedData)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        String(decryptedBytes, Charsets.UTF_8)
    }

    override suspend fun encrypt(data: String, publicKey: String): String = withContext(Dispatchers.Default) {
        val publicKeyBytes = Base64.getDecoder().decode(publicKey)
        val keySpec = X509EncodedKeySpec(publicKeyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val key = keyFactory.generatePublic(keySpec)
        
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encryptedBytes = cipher.doFinal(data.toByteArray())
        Base64.getEncoder().encodeToString(encryptedBytes)
    }

    override suspend fun decryptAes(data: ByteArray, key: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        cipher.doFinal(data)
    }

    override suspend fun encryptAes(data: ByteArray, key: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        cipher.doFinal(data)
    }
    
    override fun generateAesKey(): ByteArray {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        return keyGen.generateKey().encoded
    }

    override fun getMyPublicKeyHash(): String {
        return getHashFromPublicKeyString(getMyPublicKeyString())
    }

    override fun getHashFromPublicKeyString(key: String): String {
        if (key.isEmpty()) return ""
        try {
            val pubBytes = Base64.getDecoder().decode(key)
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(pubBytes)
            return hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            return ""
        }
    }
    
    override fun createIdentityInMemory(): MemoryIdentity {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        return MemoryIdentity(
            publicKeyBase64 = Base64.getEncoder().encodeToString(kp.public.encoded),
            privateKeyBase64 = Base64.getEncoder().encodeToString(kp.private.encoded)
        )
    }

    override suspend fun importIdentity(privateKeyBase64: String, publicKeyBase64: String) = withContext(Dispatchers.IO) {
        val privBytes = Base64.getDecoder().decode(privateKeyBase64)
        val pubBytes = Base64.getDecoder().decode(publicKeyBase64)
        
        // Validate keys
        val keyFactory = KeyFactory.getInstance("RSA")
        val privKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))
        val pubKey = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))
        
        PRIVATE_KEY_FILE.writeBytes(privBytes)
        PUBLIC_KEY_FILE.writeBytes(pubBytes)
        myKeyPair = KeyPair(pubKey, privKey)
    }

    override suspend fun importIdentity(privateKeyBase64: String) = withContext(Dispatchers.IO) {
        val privBytes = Base64.getDecoder().decode(privateKeyBase64)
        val keyFactory = KeyFactory.getInstance("RSA")
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))
        
        if (privateKey is RSAPrivateCrtKey) {
            val spec = RSAPublicKeySpec(privateKey.modulus, privateKey.publicExponent)
            val publicKey = keyFactory.generatePublic(spec)
            val pubBytes = publicKey.encoded
            
            PRIVATE_KEY_FILE.writeBytes(privBytes)
            PUBLIC_KEY_FILE.writeBytes(pubBytes)
            myKeyPair = KeyPair(publicKey, privateKey)
        } else {
            throw Exception("Cannot derive public key from this private key")
        }
    }

    override suspend fun encryptWithPassword(data: String, password: String): String = withContext(Dispatchers.Default) {
        val key = deriveKeyPBKDF2(password)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(data.toByteArray())
        
        // Combine IV + Encrypted Data
        val combined = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
        
        Base64.getEncoder().encodeToString(combined)
    }

    override suspend fun decryptWithPassword(encryptedData: String, password: String): String = withContext(Dispatchers.Default) {
        val combined = Base64.getDecoder().decode(encryptedData)
        val key = deriveKeyPBKDF2(password)
        
        // Extract IV (GCM usually 12 bytes)
        val ivSize = 12 
        if (combined.size < ivSize) throw IllegalArgumentException("Invalid encrypted data")
        
        val iv = ByteArray(ivSize)
        val encryptedBytes = ByteArray(combined.size - ivSize)
        
        System.arraycopy(combined, 0, iv, 0, ivSize)
        System.arraycopy(combined, ivSize, encryptedBytes, 0, encryptedBytes.size)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        
        String(decryptedBytes, Charsets.UTF_8)
    }

    private fun deriveKeyPBKDF2(password: String): ByteArray {
        val salt = "MessengerFixedSalt".toByteArray() // Fixed salt for now to match other platforms
        val iterations = 10000
        val keyLength = 256
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLength)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return skf.generateSecret(spec).encoded
    }
}
