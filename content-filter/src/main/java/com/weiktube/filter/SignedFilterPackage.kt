package com.weiktube.filter

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Verifies the exact downloaded bytes before JSON parsing or use by either engine. */
object SignedFilterPackage {
    fun publicKey(encoded: String): PublicKey = KeyFactory.getInstance("RSA").generatePublic(
        X509EncodedKeySpec(Base64.getDecoder().decode(encoded.trim())))

    fun verify(json: ByteArray, signatureBase64: String, key: PublicKey, minimumVersion: Int = 1): FilterSnapshot {
        require(json.size in 1..FilterSnapshot.MAX_BYTES && signatureBase64.length <= 4096) { "FILTER_SIZE" }
        val signature = Base64.getDecoder().decode(signatureBase64.trim())
        val verified = runCatching {
            Signature.getInstance("SHA256withRSA").run {
                initVerify(key); update(json); verify(signature)
            }
        }.getOrDefault(false)
        require(verified) { "FILTER_SIGNATURE" }
        return FilterSnapshot.parse(json).also { require(it.version >= minimumVersion) { "FILTER_ROLLBACK" } }
    }
}
