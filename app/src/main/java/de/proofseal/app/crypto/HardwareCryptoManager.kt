package de.proofseal.app.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import de.proofseal.app.model.CapturedKeyframe
import de.proofseal.app.model.QrSealPayload
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
import java.util.Locale

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

        /**
         * Erzeugt das deterministische, kanonische ByteArray über alle im QR-Code
         * und PDF verankerten Sicherheitsfelder. Jede Änderung an einem dieser Felder
         * verändert zwingend den resultierenden SHA-256 Payload Hash.
         */
        fun buildCanonicalSealBytes(
            protocolId: String,
            timestampUtc: String,
            uptimeNanos: Long,
            deviceModel: String,
            securityLevel: String,
            modeTitle: String,
            ocrPrimaryValue: String,
            imageHashes: List<String>,
            keyFingerprint: String
        ): ByteArray {
            val sb = StringBuilder()
            sb.append("PROOFSEAL_CANONICAL_SEAL_V2\n")
            sb.append("PROTOCOL_ID=").append(protocolId.trim()).append('\n')
            sb.append("TIMESTAMP_UTC=").append(timestampUtc.trim()).append('\n')
            sb.append("UPTIME_NANOS=").append(uptimeNanos).append('\n')
            sb.append("DEVICE_MODEL=").append(deviceModel.trim()).append('\n')
            sb.append("SECURITY_LEVEL=").append(securityLevel.trim()).append('\n')
            sb.append("MODE_TITLE=").append(modeTitle.trim()).append('\n')
            sb.append("OCR_PRIMARY=").append(ocrPrimaryValue.trim()).append('\n')
            sb.append("IMAGE_HASHES=").append(imageHashes.joinToString("|") { it.trim() }).append('\n')
            sb.append("KEY_FINGERPRINT=").append(keyFingerprint.trim().uppercase(Locale.ROOT)).append('\n')
            return sb.toString().toByteArray(Charsets.UTF_8)
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
        } catch (_: StrongBoxUnavailableException) {
        } catch (_: ProviderException) {
        } catch (_: Exception) {
        }

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
        } catch (_: Exception) {
            prefs.getBoolean(PREF_STRONGBOX_ACTIVE, false)
        }
    }

    fun getHardwareSecurityStatus(): Pair<String, Boolean> {
        val strongBox = ensureKeyPairInitialized()
        val label = if (strongBox) {
            "StrongBox Hardware-SE"
        } else {
            "Standard TEE"
        }
        return label to strongBox
    }

    fun getPublicKeyFingerprint(): String {
        return try {
            ensureKeyPairInitialized()
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val cert = keyStore.getCertificate(KEY_ALIAS) ?: return "UNVERFÜGBAR"
            formatFingerprint(cert.publicKey.encoded)
        } catch (_: Exception) {
            "KEYSTORE-INIT-ERROR"
        }
    }

    fun getPublicKeyBase64(): String {
        return try {
            ensureKeyPairInitialized()
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val cert = keyStore.getCertificate(KEY_ALIAS) ?: return ""
            Base64.encodeToString(cert.publicKey.encoded, Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Prüft, ob das übergebene Zertifikat (Public Key / Fingerprint) vom Hardware-Keystore
     * exakt DIESES Smartphones stammt ("Eigenes Gerät" vs. "Fremdgerät").
     */
    fun isSignedByThisDevice(certPublicKeyBase64: String, certKeyFingerprint: String): Boolean {
        val localPubBase64 = getPublicKeyBase64().trim()
        val localFp = getPublicKeyFingerprint().trim().uppercase(Locale.ROOT)
        if (localPubBase64.isNotEmpty() && certPublicKeyBase64.trim() == localPubBase64) {
            return true
        }
        if (localFp.isNotEmpty() && certKeyFingerprint.trim().uppercase(Locale.ROOT) == localFp) {
            return true
        }
        return false
    }

    /**
     * Berechnet den SHA-256 Hash über das kanonische Beweispaket und signiert diesen
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

        val payloadHashHex = sha256Hex(payloadBytes).lowercase(Locale.ROOT)
        val canonicalHashBytes = payloadHashHex.toByteArray(Charsets.UTF_8)

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
     * Signiert einen bereits berechneten SHA-256 Hex-String (z. B. den Binär-Hash des PDF-Dokumentenstroms)
     * direkt mit dem Hardware-Keystore-Schlüssel.
     */
    @Synchronized
    fun signDigestHex(digestHex: String): String {
        ensureKeyPairInitialized()
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val privateKeyEntry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(privateKeyEntry.privateKey)
        signature.update(digestHex.trim().lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
    }

    /**
     * Prüft lokal (offline) anhand des Payload-Hashes, der ECDSA-Signatur (Base64)
     * und des X.509 Public Keys (Base64), ob die mathematische ECDSA-Signatur gültig ist.
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
            val normalizedHashBytes = payloadHash.trim().lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8)
            val signatureBytes = Base64.decode(signatureBase64.trim(), Base64.NO_WRAP)
            val publicKeyBytes = Base64.decode(publicKeyBase64.trim(), Base64.NO_WRAP)

            val keyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC)
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))

            val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
            verifier.initVerify(publicKey)
            verifier.update(normalizedHashBytes)
            verifier.verify(signatureBytes)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Vollständige zweistufige Integritätsprüfung eines QrSealPayload:
     * 1. Prüft bei v2-Zertifikaten, ob der aus den Datenfeldern (Protokoll-ID, UTC, Uptime,
     *    Gerät, Security-Level, Modus, OCR-Wert, Bild-Hashes, Key-Fingerprint) neu berechnete
     *    SHA-256 exakt mit payloadHash übereinstimmt (verhindert jede Manipulation einzelner JSON-Felder!).
     * 2. Prüft die mathematische ECDSA-Hardware-Signatur über payloadHash.
     */
    fun verifyQrPayloadIntegrity(payload: QrSealPayload): Pair<Boolean, String?> {
        // 1. Prüfe Fingerprint-Konsistenz zum enthaltenen Public Key
        val derivedFp = try {
            val pubBytes = Base64.decode(payload.publicKeyBase64.trim(), Base64.NO_WRAP)
            formatFingerprint(pubBytes)
        } catch (_: Exception) {
            return false to "Ungültiges Public-Key-Format im Zertifikat."
        }

        if (payload.keyFingerprint.isNotBlank() &&
            !payload.keyFingerprint.equals(derivedFp, ignoreCase = true)
        ) {
            return false to "Key-Fingerprint stimmt nicht mit dem eingebetteten Public Key überein!"
        }

        // 2. Bei Schema v2: Berechne den kanonischen SHA-256 Hash über alle Pflichtfelder nach
        if (payload.version >= 2) {
            val canonicalBytes = buildCanonicalSealBytes(
                protocolId = payload.protocolId,
                timestampUtc = payload.timestampUtc,
                uptimeNanos = payload.uptimeNanos,
                deviceModel = payload.deviceModel,
                securityLevel = payload.securityLevel,
                modeTitle = payload.modeTitle,
                ocrPrimaryValue = payload.primaryOcrValue,
                imageHashes = payload.imageHashes,
                keyFingerprint = derivedFp
            )
            val recomputedHash = sha256Hex(canonicalBytes).lowercase(Locale.ROOT)
            if (recomputedHash != payload.payloadHash.trim().lowercase(Locale.ROOT)) {
                return false to "Datenmanipulation erkannt! Die Zertifikatsfelder (OCR, Bild-Hashes oder Metadaten) weichen vom signierten SHA-256 Payload Hash ab."
            }
        }

        // 3. Mathematische ECDSA-Signaturprüfung
        val ecdsaOk = verifySeal(
            payloadHash = payload.payloadHash,
            signatureBase64 = payload.signatureBase64,
            publicKeyBase64 = payload.publicKeyBase64
        )
        if (!ecdsaOk) {
            return false to "Mathematischer ECDSA-Signaturbruch! Die Hardware-Signatur ist ungültig."
        }

        return true to null
    }

    /**
     * Erzeugt das kanonische Beweispaket (ByteArray) über alle Pflichtfelder inkl.
     * der individuellen 16-Zeichen SHA-256 Kurz-Hashes aller aufgenommenen Fotos.
     */
    fun buildCanonicalEvidencePayload(
        protocolId: String,
        modeTitle: String,
        createdAtUtcIso: String,
        uptimeNanos: Long,
        deviceModel: String,
        securityLevel: String,
        primaryOcrValue: String,
        keyframes: List<CapturedKeyframe>,
        keyFingerprint: String
    ): ByteArray {
        val imageHashes = keyframes.map { it.labeledShortHash }
        return buildCanonicalSealBytes(
            protocolId = protocolId,
            timestampUtc = createdAtUtcIso,
            uptimeNanos = uptimeNanos,
            deviceModel = deviceModel,
            securityLevel = securityLevel,
            modeTitle = modeTitle,
            ocrPrimaryValue = primaryOcrValue,
            imageHashes = imageHashes,
            keyFingerprint = keyFingerprint
        )
    }
}
