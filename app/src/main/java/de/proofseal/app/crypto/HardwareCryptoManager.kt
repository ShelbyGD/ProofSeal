package de.proofseal.app.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import de.proofseal.app.model.CapturedKeyframe
import de.proofseal.app.model.SensorIntegrityReport
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.ProviderException
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class CryptoSealResult(
    val payloadSha256Hex: String,
    val signatureBase64: String,
    val publicKeyBase64: String,
    val publicKeyFingerprint: String,
    val hardwareSecurityLevel: String,
    val isStrongBoxBacked: Boolean,
    val signedAtUtcIso: String
)

class HardwareCryptoManager(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "de.proofseal.app.master_ecdsa_p256_v1"
        private const val EC_CURVE = "secp256r1"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        private const val PREFS_NAME = "proofseal_crypto_meta"
        private const val PREF_STRONGBOX_ACTIVE = "is_strongbox_backed"

        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun formatFingerprint(publicKeyBytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(publicKeyBytes)
            return digest.take(16).joinToString(":") { "%02X".format(it) }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun ensureKeyPairInitialized(): Boolean {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return isKeyStrongBoxBacked(keyStore)
        }
        return generateHardwareKeyPair()
    }

    @Synchronized
    private fun generateHardwareKeyPair(): Boolean {
        // 1. Versuch: Echtes StrongBox Hardware-SE (dedizierter Sicherheitschip)
        try {
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEYSTORE
            )
            val strongBoxSpec = buildKeyGenSpec(useStrongBox = true)
            kpg.initialize(strongBoxSpec)
            kpg.generateKeyPair()
            prefs.edit().putBoolean(PREF_STRONGBOX_ACTIVE, true).apply()
            return true
        } catch (e: StrongBoxUnavailableException) {
            // Sauberer Fallback auf Standard-TEE
        } catch (e: ProviderException) {
            // Manche OEMs werfen ProviderException / KeyStoreException wenn StrongBox fehlt
        } catch (e: Exception) {
            // Allgemeiner Fallback auf Standard-TEE
        }

        // 2. Fallback: Standard-TEE (Trusted Execution Environment) im AndroidKeyStore
        val kpgFallback = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )
        val teeSpec = buildKeyGenSpec(useStrongBox = false)
        kpgFallback.initialize(teeSpec)
        kpgFallback.generateKeyPair()
        prefs.edit().putBoolean(PREF_STRONGBOX_ACTIVE, false).apply()
        return false
    }

    private fun buildKeyGenSpec(useStrongBox: Boolean): KeyGenParameterSpec {
        return KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setIsStrongBoxBacked(useStrongBox)
            .build()
    }

    private fun isKeyStrongBoxBacked(keyStore: KeyStore): Boolean {
        return try {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
                ?: return prefs.getBoolean(PREF_STRONGBOX_ACTIVE, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val factory = KeyFactory.getInstance(entry.privateKey.algorithm, ANDROID_KEYSTORE)
                val keyInfo = factory.getKeySpec(entry.privateKey, KeyInfo::class.java)
                keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
            } else {
                prefs.getBoolean(PREF_STRONGBOX_ACTIVE, false)
            }
        } catch (e: Exception) {
            prefs.getBoolean(PREF_STRONGBOX_ACTIVE, false)
        }
    }

    fun getHardwareSecurityStatus(): Pair<String, Boolean> {
        val strongBox = ensureKeyPairInitialized()
        val label = if (strongBox) {
            "StrongBox Hardware-SE"
        } else {
            "Standard TEE (Hardware Keystore)"
        }
        return label to strongBox
    }

    fun getPublicKeyFingerprint(): String {
        return try {
            ensureKeyPairInitialized()
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val cert = keyStore.getCertificate(KEY_ALIAS) ?: return "UNVERFÜGBAR"
            formatFingerprint(cert.publicKey.encoded)
        } catch (e: Exception) {
            "KEYSTORE-INIT-ERROR"
        }
    }

    /**
     * Berechnet den SHA-256 Hash über das gesamte Beweispaket und signiert diesen
     * deterministisch mit SHA256withECDSA (secp256r1) über den Android Hardware-Keystore.
     */
    @Synchronized
    fun signPayload(payloadBytes: ByteArray): CryptoSealResult {
        val isStrongBox = ensureKeyPairInitialized()
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        val privateKeyEntry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: run {
                generateHardwareKeyPair()
                keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
            }

        // 1. SHA-256 Hash über das gesamte Beweispaket (Hex-String, lowercase)
        val payloadHashHex = sha256Hex(payloadBytes).lowercase()
        val canonicalHashBytes = payloadHashHex.toByteArray(Charsets.UTF_8)

        // 2. Hardware-gestützte Signatur mit SHA256withECDSA
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(privateKeyEntry.privateKey)
        signature.update(canonicalHashBytes)
        val signatureBytes = signature.sign()

        val publicKeyBytes = privateKeyEntry.certificate.publicKey.encoded
        val signatureBase64 = Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
        val publicKeyBase64 = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)
        val fingerprint = formatFingerprint(publicKeyBytes)

        val securityLevel = if (isStrongBox) {
            "StrongBox Hardware-SE"
        } else {
            "Standard TEE"
        }

        val nowUtc = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))

        return CryptoSealResult(
            payloadSha256Hex = payloadHashHex,
            signatureBase64 = signatureBase64,
            publicKeyBase64 = publicKeyBase64,
            publicKeyFingerprint = fingerprint,
            hardwareSecurityLevel = securityLevel,
            isStrongBoxBacked = isStrongBox,
            signedAtUtcIso = nowUtc
        )
    }

    /**
     * Prüft lokal (offline) anhand des Payload-Hashes, der ECDSA-Signatur (Base64)
     * und des X.509 Public Keys (Base64), ob das Zertifikat mathematisch authentisch
     * und unverändert ist.
     */
    fun verifySeal(
        payloadHash: String,
        signatureBase64: String,
        publicKeyBase64: String
    ): Boolean {
        if (payloadHash.isBlank() || signatureBase64.isBlank() || publicKeyBase64.isBlank()) {
            return false
        }
        return try {
            val normalizedHashBytes = payloadHash.trim().lowercase().toByteArray(Charsets.UTF_8)
            val signatureBytes = Base64.decode(signatureBase64.trim(), Base64.NO_WRAP)
            val publicKeyBytes = Base64.decode(publicKeyBase64.trim(), Base64.NO_WRAP)

            val keyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC)
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))

            val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
            verifier.initVerify(publicKey)
            verifier.update(normalizedHashBytes)
            verifier.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Erzeugt das kanonische Beweispaket (ByteArray) aus allen protokollierten Daten,
     * Keyframe-Bildhashes, Sensorketten-Digest, Hardware-Uptime und OCR-Werten.
     */
    fun buildCanonicalEvidencePayload(
        protocolId: String,
        modeId: String,
        createdAtUtcIso: String,
        deviceModel: String,
        gpsCoordinates: String,
        primaryOcrValue: String,
        witnessName: String,
        userNotes: String,
        keyframes: List<CapturedKeyframe>,
        sensorReport: SensorIntegrityReport
    ): ByteArray {
        val sb = StringBuilder()
        sb.append("PROOFSEAL_V1_CANONICAL_PAYLOAD\n")
        sb.append("PROTOCOL_ID=").append(protocolId).append('\n')
        sb.append("MODE=").append(modeId).append('\n')
        sb.append("UTC=").append(createdAtUtcIso).append('\n')
        sb.append("DEVICE=").append(deviceModel).append('\n')
        sb.append("UPTIME_START_NS=").append(sensorReport.startElapsedNanos).append('\n')
        sb.append("UPTIME_END_NS=").append(sensorReport.endElapsedNanos).append('\n')
        sb.append("SENSOR_CHAIN_SHA256=").append(sensorReport.sensorChainSha256).append('\n')
        sb.append("PARALLAX_SCORE=").append(sensorReport.parallaxConfidenceScore).append('\n')
        sb.append("CONTINUOUS=").append(sensorReport.isContinuous).append('\n')
        sb.append("GPS=").append(gpsCoordinates).append('\n')
        sb.append("PRIMARY_OCR=").append(primaryOcrValue.trim()).append('\n')
        sb.append("WITNESS=").append(witnessName.trim()).append('\n')
        sb.append("NOTES=").append(userNotes.trim()).append('\n')
        keyframes.forEach { kf ->
            sb.append("KF_").append(kf.index)
                .append("|OFFSET_MS=").append(kf.offsetMillis)
                .append("|UPTIME_NS=").append(kf.elapsedRealtimeNanos)
                .append("|SHA256=").append(kf.imageSha256)
                .append("|OCR=").append(kf.extractedKeyCandidate)
                .append('\n')
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }
}
