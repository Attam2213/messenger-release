package com.example.messenger.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.*
import platform.CoreCrypto.*
import platform.Security.*
import platform.darwin.*
import kotlinx.cinterop.*

class IosCryptoManager : CryptoManager {

    private val publicKeyTag = "com.example.messenger.keys.public"
    private val privateKeyTag = "com.example.messenger.keys.private"
    
    private fun deriveKeyPBKDF2(password: String): ByteArray {
        val passwordData = password.encodeToByteArray()
        val saltData = "MessengerFixedSalt".encodeToByteArray() // In production, use random salt and store with data
        val derivedKey = ByteArray(32)
        val rounds = 10000u
        
        passwordData.usePinned { pwd ->
            saltData.usePinned { slt ->
                derivedKey.usePinned { dk ->
                    CCKeyDerivationPBKDF(
                        kCCPBKDF2,
                        pwd.addressOf(0),
                        passwordData.size.toULong(),
                        slt.addressOf(0),
                        saltData.size.toULong(),
                        kCCPRFHmacAlgSHA256,
                        rounds,
                        dk.addressOf(0),
                        derivedKey.size.toULong()
                    )
                }
            }
        }
        return derivedKey
    }

    override fun getMyPublicKeyString(): String {
        val publicKey = loadKey(publicKeyTag) ?: return ""
        val data = SecKeyCopyExternalRepresentation(publicKey, null) ?: return ""
        val nsData = CFBridgingRelease(data) as NSData
        return nsData.base64EncodedStringWithOptions(0u)
    }

    override fun getMyPrivateKeyString(): String {
        val privateKey = loadKey(privateKeyTag) ?: return ""
        val data = SecKeyCopyExternalRepresentation(privateKey, null) ?: return ""
        val nsData = CFBridgingRelease(data) as NSData
        return nsData.base64EncodedStringWithOptions(0u)
    }

    override suspend fun sign(data: String): String = withContext(Dispatchers.Default) {
        val privateKey = loadKey(privateKeyTag) ?: throw IllegalStateException("No private key found")
        val dataToSign = data.toNSData()
        
        val algorithm = kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256
        
        if (!SecKeyIsAlgorithmSupported(privateKey, kSecKeyOperationTypeSign, algorithm)) {
             throw IllegalStateException("Algorithm not supported")
        }

        val signature = SecKeyCreateSignature(privateKey, algorithm, dataToSign as CFDataRef, null)
            ?: throw IllegalStateException("Signing failed")
            
        val nsData = CFBridgingRelease(signature) as NSData
        return@withContext nsData.base64EncodedStringWithOptions(0u)
    }

    override suspend fun verify(data: String, signatureBase64: String, publicKeyString: String): Boolean = withContext(Dispatchers.Default) {
        val keyData = NSData.create(base64EncodedString = publicKeyString, options = 0u) ?: return@withContext false
        
        val attributes = mapOf<CFStringRef?, Any>(
            kSecAttrKeyType to kSecAttrKeyTypeRSA,
            kSecAttrKeyClass to kSecAttrKeyClassPublic,
            kSecAttrKeySizeInBits to NSNumber.numberWithInt(2048)
        )
        
        val params = attributes.toCFDictionary()
        val publicKey = SecKeyCreateWithData(keyData as CFDataRef, params, null) 
        if (params != null) CFRelease(params)
        
        if (publicKey == null) return@withContext false

        val algorithm = kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256
        val dataToVerify = data.toNSData()
        val signatureData = NSData.create(base64EncodedString = signatureBase64, options = 0u) ?: return@withContext false

        return@withContext SecKeyVerifySignature(publicKey, algorithm, dataToVerify as CFDataRef, signatureData as CFDataRef, null)
    }

    override fun hasIdentity(): Boolean {
        val key = loadKey(privateKeyTag)
        return key != null
    }

    override fun reloadKeys() {
    }

    override suspend fun createIdentity() = withContext(Dispatchers.Default) {
        if (hasIdentity()) return@withContext

        val privateKeyAttrs = mapOf<CFStringRef?, Any>(
            kSecAttrIsPermanent to NSNumber.numberWithBool(true),
            kSecAttrApplicationTag to privateKeyTag.toNSData()
        )
        
        val publicKeyAttrs = mapOf<CFStringRef?, Any>(
            kSecAttrIsPermanent to NSNumber.numberWithBool(true),
            kSecAttrApplicationTag to publicKeyTag.toNSData()
        )

        val attributes = mapOf<CFStringRef?, Any>(
            kSecAttrKeyType to kSecAttrKeyTypeRSA,
            kSecAttrKeySizeInBits to NSNumber.numberWithInt(2048),
            kSecPrivateKeyAttrs to privateKeyAttrs.toNSDictionary(),
            kSecPublicKeyAttrs to publicKeyAttrs.toNSDictionary()
        )

        val params = attributes.toCFDictionary()
        val privateKey = SecKeyCreateRandomKey(params, null)
        if (params != null) CFRelease(params)
        
        if (privateKey == null) {
             throw IllegalStateException("Failed to generate key pair")
        }
    }

    override suspend fun clearIdentity() = withContext(Dispatchers.Default) {
        deleteKey(privateKeyTag)
        deleteKey(publicKeyTag)
    }

    override suspend fun decrypt(encryptedData: String): String = withContext(Dispatchers.Default) {
        val privateKey = loadKey(privateKeyTag) ?: throw IllegalStateException("No private key")
        val data = NSData.create(base64EncodedString = encryptedData, options = 0u) ?: throw IllegalArgumentException("Invalid Base64")
        
        val algorithm = kSecKeyAlgorithmRSAEncryptionOAEPSHA256
        
        val decryptedData = SecKeyCreateDecryptedData(privateKey, algorithm, data as CFDataRef, null)
            ?: throw IllegalStateException("Decryption failed")
            
        val nsData = CFBridgingRelease(decryptedData) as NSData
        return@withContext NSString.create(data = nsData, encoding = NSUTF8StringEncoding).toString()
    }

    override suspend fun encrypt(data: String, publicKey: String): String = withContext(Dispatchers.Default) {
        val keyData = NSData.create(base64EncodedString = publicKey, options = 0u) ?: throw IllegalArgumentException("Invalid Base64 Key")
        
        val attributes = mapOf<CFStringRef?, Any>(
            kSecAttrKeyType to kSecAttrKeyTypeRSA,
            kSecAttrKeyClass to kSecAttrKeyClassPublic
        )
        
        val params = attributes.toCFDictionary()
        val key = SecKeyCreateWithData(keyData as CFDataRef, params, null) 
        if (params != null) CFRelease(params)
        
        if (key == null) throw IllegalStateException("Failed to create key from data")
            
        val algorithm = kSecKeyAlgorithmRSAEncryptionOAEPSHA256
        val dataToEncrypt = data.toNSData()
        
        val encryptedData = SecKeyCreateEncryptedData(key, algorithm, dataToEncrypt as CFDataRef, null)
            ?: throw IllegalStateException("Encryption failed")
            
        val nsData = CFBridgingRelease(encryptedData) as NSData
        return@withContext nsData.base64EncodedStringWithOptions(0u)
    }

    override suspend fun decryptAes(data: ByteArray, key: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        val keyData = key.toNSData()
        val attributes = mapOf<CFStringRef?, Any>(
            kSecAttrKeyType to kSecAttrKeyTypeAES,
            kSecAttrKeyClass to kSecAttrKeyClassSymmetric,
            kSecAttrKeySizeInBits to NSNumber.numberWithInt(256)
        )
        
        val params = attributes.toCFDictionary()
        val secKey = SecKeyCreateWithData(keyData as CFDataRef, params, null)
        if (params != null) CFRelease(params)
        
        if (secKey == null) throw IllegalStateException("Failed to create AES key")

        val algorithm = kSecKeyAlgorithmAESGCM
        val nsData = data.toNSData()
        
        val decrypted = SecKeyCreateDecryptedData(secKey, algorithm, nsData as CFDataRef, null)
            ?: throw IllegalStateException("AES Decryption failed")
            
        val resultNsData = CFBridgingRelease(decrypted) as NSData
        return@withContext resultNsData.toByteArray()
    }

    override suspend fun encryptAes(data: ByteArray, key: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        val keyData = key.toNSData()
        val attributes = mapOf<CFStringRef?, Any>(
            kSecAttrKeyType to kSecAttrKeyTypeAES,
            kSecAttrKeyClass to kSecAttrKeyClassSymmetric,
            kSecAttrKeySizeInBits to NSNumber.numberWithInt(256)
        )
        
        val params = attributes.toCFDictionary()
        val secKey = SecKeyCreateWithData(keyData as CFDataRef, params, null)
        if (params != null) CFRelease(params)
        
        if (secKey == null) throw IllegalStateException("Failed to create AES key")

        val algorithm = kSecKeyAlgorithmAESGCM
        val nsData = data.toNSData()
        
        val encrypted = SecKeyCreateEncryptedData(secKey, algorithm, nsData as CFDataRef, null)
            ?: throw IllegalStateException("AES Encryption failed")
            
        val resultNsData = CFBridgingRelease(encrypted) as NSData
        return@withContext resultNsData.toByteArray()
    }

    override fun generateAesKey(): ByteArray {
        val bytes = ByteArray(32)
        val status = SecRandomCopyBytes(kSecRandomDefault, 32u, bytes.refTo(0))
        if (status != errSecSuccess) {
            throw IllegalStateException("Random generation failed")
        }
        return bytes
    }

    override fun getMyPublicKeyHash(): String {
        return getMyPublicKeyString().hashCode().toString()
    }

    override fun getHashFromPublicKeyString(key: String): String {
        return key.hashCode().toString()
    }

    override fun createIdentityInMemory(): MemoryIdentity {
        return MemoryIdentity("mem_pub", "mem_priv")
    }

    override suspend fun importIdentity(privateKeyBase64: String, publicKeyBase64: String) = withContext(Dispatchers.Default) {
        clearIdentity()
        
        val privateKeyData = NSData.create(base64EncodedString = privateKeyBase64, options = 0u) 
            ?: throw IllegalArgumentException("Invalid Private Key Base64")
        val publicKeyData = NSData.create(base64EncodedString = publicKeyBase64, options = 0u) 
            ?: throw IllegalArgumentException("Invalid Public Key Base64")

        val privateKeyAttrs = mapOf<CFStringRef?, Any>(
            kSecClass to kSecClassKey,
            kSecAttrKeyType to kSecAttrKeyTypeRSA,
            kSecAttrKeyClass to kSecAttrKeyClassPrivate,
            kSecAttrApplicationTag to privateKeyTag.toNSData(),
            kSecValueData to privateKeyData,
            kSecAttrIsPermanent to NSNumber.numberWithBool(true),
            kSecAttrKeySizeInBits to NSNumber.numberWithInt(2048)
        )
        
        val publicKeyAttrs = mapOf<CFStringRef?, Any>(
            kSecClass to kSecClassKey,
            kSecAttrKeyType to kSecAttrKeyTypeRSA,
            kSecAttrKeyClass to kSecAttrKeyClassPublic,
            kSecAttrApplicationTag to publicKeyTag.toNSData(),
            kSecValueData to publicKeyData,
            kSecAttrIsPermanent to NSNumber.numberWithBool(true),
            kSecAttrKeySizeInBits to NSNumber.numberWithInt(2048)
        )

        val privateParams = privateKeyAttrs.toCFDictionary()
        val publicParams = publicKeyAttrs.toCFDictionary()
        
        val statusPrivate = SecItemAdd(privateParams, null)
        val statusPublic = SecItemAdd(publicParams, null)
        
        if (privateParams != null) CFRelease(privateParams)
        if (publicParams != null) CFRelease(publicParams)
        
        if (statusPrivate != errSecSuccess || statusPublic != errSecSuccess) {
            throw IllegalStateException("Failed to import identity: Priv=$statusPrivate, Pub=$statusPublic")
        }
    }

    override suspend fun importIdentity(privateKeyBase64: String) {
        // Not implemented for single key import in this version
    }

    override fun getDatabasePassphrase(): String {
        return "ios_passphrase"
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun encryptWithPassword(data: String, password: String): String = withContext(Dispatchers.Default) {
        val key = deriveKeyPBKDF2(password)
        val ivSize = 12
        val tagSize = 16
        val iv = ByteArray(ivSize)
        arc4random_buf(iv.refTo(0), ivSize.toULong())
        
        val dataBytes = data.encodeToByteArray()
        val encryptedBytes = ByteArray(dataBytes.size)
        val tag = ByteArray(tagSize)
        
        memScoped {
            val keyPtr = key.refTo(0).getPointer(this)
            val ivPtr = iv.refTo(0).getPointer(this)
            val dataInPtr = dataBytes.refTo(0).getPointer(this)
            val dataOutPtr = encryptedBytes.refTo(0).getPointer(this)
            val tagPtr = tag.refTo(0).getPointer(this)
            val tagLenVar = alloc<ULongVar>()
            tagLenVar.value = tagSize.toULong()

            val status = CCCryptorGCM(
                kCCEncrypt,
                kCCAlgorithmAES,
                keyPtr, key.size.toULong(),
                ivPtr, iv.size.toULong(),
                null, 0u,
                dataInPtr, dataBytes.size.toULong(),
                dataOutPtr,
                tagPtr, tagLenVar.ptr
            )
            
            if (status != kCCSuccess) {
                throw Exception("Encryption failed with status: $status")
            }
        }
        
        val combined = ByteArray(ivSize + encryptedBytes.size + tagSize)
        iv.copyInto(combined, 0)
        encryptedBytes.copyInto(combined, ivSize)
        tag.copyInto(combined, ivSize + encryptedBytes.size)
        
        val nsData = combined.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), combined.size.toULong())
        }
        nsData.base64EncodedStringWithOptions(0u)
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun decryptWithPassword(encryptedData: String, password: String): String = withContext(Dispatchers.Default) {
        val nsData = NSData.create(base64EncodedString = encryptedData, options = 0u) 
            ?: throw IllegalArgumentException("Invalid Base64")
        
        val length = nsData.length.toInt()
        val combinedBytes = ByteArray(length)
        if (length > 0) {
            combinedBytes.usePinned { pinned ->
                nsData.getBytes(pinned.addressOf(0), length.toULong())
            }
        }

        val ivSize = 12
        val tagSize = 16
        
        if (combinedBytes.size < ivSize + tagSize) throw IllegalArgumentException("Invalid encrypted data")
        
        val iv = combinedBytes.copyOfRange(0, ivSize)
        val ciphertextAndTag = combinedBytes.copyOfRange(ivSize, combinedBytes.size)
        val ciphertextSize = ciphertextAndTag.size - tagSize
        val ciphertext = ciphertextAndTag.copyOfRange(0, ciphertextSize)
        val tag = ciphertextAndTag.copyOfRange(ciphertextSize, ciphertextAndTag.size)
        
        val key = deriveKeyPBKDF2(password)
        val decryptedBytes = ByteArray(ciphertextSize)
        
        memScoped {
            val keyPtr = key.refTo(0).getPointer(this)
            val ivPtr = iv.refTo(0).getPointer(this)
            val dataInPtr = ciphertext.refTo(0).getPointer(this)
            val dataOutPtr = decryptedBytes.refTo(0).getPointer(this)
            val tagPtr = tag.refTo(0).getPointer(this)
            val tagLenVar = alloc<ULongVar>()
            tagLenVar.value = tagSize.toULong()

            val status = CCCryptorGCM(
                kCCDecrypt,
                kCCAlgorithmAES,
                keyPtr, key.size.toULong(),
                ivPtr, iv.size.toULong(),
                null, 0u,
                dataInPtr, ciphertext.size.toULong(),
                dataOutPtr,
                tagPtr, tagLenVar.ptr
            )
            
            if (status != kCCSuccess) {
                 throw Exception("Decryption failed with status: $status")
            }
        }
        
        decryptedBytes.decodeToString()
    }

    // --- Helpers ---

    private fun loadKey(tag: String): SecKeyRef? {
        // kSecReturnRef expects kCFBooleanTrue
        val query = mapOf<CFStringRef?, Any>(
            kSecClass to kSecClassKey,
            kSecAttrApplicationTag to tag.toNSData(),
            kSecAttrKeyType to kSecAttrKeyTypeRSA,
            kSecReturnRef to kCFBooleanTrue as Any
        )
        
        val params = query.toCFDictionary()
        
        return memScoped {
            val resultPtr = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(params, resultPtr.ptr)
            if (params != null) CFRelease(params)
            
            if (status == errSecSuccess) {
                resultPtr.value as SecKeyRef
            } else {
                null
            }
        }
    }

    private fun deleteKey(tag: String) {
        val query = mapOf<CFStringRef?, Any>(
            kSecClass to kSecClassKey,
            kSecAttrApplicationTag to tag.toNSData()
        )
        val params = query.toCFDictionary()
        SecItemDelete(params)
        if (params != null) CFRelease(params)
    }

    private fun Map<CFStringRef?, Any>.toCFDictionary(): CFDictionaryRef? {
        val dict = NSMutableDictionary()
        this.forEach { (k, v) ->
            if (k != null) {
                dict.setObject(v, forKey = k as NSCopyingProtocol)
            }
        }
        return CFBridgingRetain(dict) as CFDictionaryRef?
    }
    
    private fun Map<CFStringRef?, Any>.toNSDictionary(): NSDictionary {
        val dict = NSMutableDictionary()
        this.forEach { (k, v) ->
            if (k != null) {
                dict.setObject(v, forKey = k as NSCopyingProtocol)
            }
        }
        return dict
    }

    private fun String.toNSData(): NSData {
        return NSString.create(string = this).dataUsingEncoding(NSUTF8StringEncoding) 
            ?: NSData.data()
    }

    private fun ByteArray.toNSData(): NSData = memScoped {
        if (isEmpty()) return@memScoped NSData.data()
        return NSData.dataWithBytes(this@toNSData.refTo(0), this@toNSData.size.toULong())
    }

    private fun NSData.toByteArray(): ByteArray {
        val length = this.length.toInt()
        if (length == 0) return ByteArray(0)
        val bytes = ByteArray(length)
        val ptr = this.bytes
        if (ptr != null) {
             val bytePtr = ptr.reinterpret<ByteVar>()
             for (i in 0 until length) {
                 bytes[i] = bytePtr[i]
             }
        }
        return bytes
    }
}
