package de.proofseal.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.proofseal.app.model.CapturedKeyframe
import de.proofseal.app.model.QrSealPayload
import de.proofseal.app.model.SensorGraphPoint
import org.json.JSONArray

@Entity(tableName = "proof_records")
data class ProofRecordEntity(
    @PrimaryKey
    val protocolId: String,
    val title: String,
    val modeId: String,
    val modeTitle: String,
    val createdAtEpochMillis: Long,
    val createdAtUtcIso: String,
    val createdAtFormatted: String,
    val uptimeSpanFormatted: String,
    val startElapsedNanos: Long,
    val endElapsedNanos: Long,
    val durationSeconds: Float,
    val deviceModel: String,
    val hardwareSecurityLevel: String,
    val isStrongBoxBacked: Boolean,
    val gpsLocationText: String,
    val parallaxConfidenceScore: Int,
    val isSensorChainContinuous: Boolean,
    val maxSensorGapMillis: Long,
    val totalSensorSamples: Int,
    val sensorChainSha256: String,
    val primaryOcrValue: String,
    val fullOcrSummary: String,
    val detectedBarcodesJoined: String,
    val witnessName: String,
    val userNotes: String,
    val payloadSha256Hex: String,
    val signatureBase64: String,
    val publicKeyBase64: String,
    val publicKeyFingerprint: String,
    val pdfFilePath: String,
    val previewImagePath: String,
    val keyframesJson: String,
    val sensorGraphJson: String
) {
    fun parseKeyframes(): List<CapturedKeyframe> {
        return try {
            val arr = JSONArray(keyframesJson)
            val list = ArrayList<CapturedKeyframe>(arr.length())
            for (i in 0 until arr.length()) {
                list.add(CapturedKeyframe.fromJson(arr.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseSensorGraph(): List<SensorGraphPoint> {
        return try {
            val arr = JSONArray(sensorGraphJson)
            val list = ArrayList<SensorGraphPoint>(arr.length())
            for (i in 0 until arr.length()) {
                list.add(SensorGraphPoint.fromJson(arr.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun toQrSealPayload(): QrSealPayload {
        return QrSealPayload(
            version = 1,
            protocolId = protocolId,
            timestampUtc = createdAtUtcIso,
            deviceModel = deviceModel,
            securityLevel = hardwareSecurityLevel,
            modeTitle = modeTitle,
            primaryOcrValue = primaryOcrValue.ifBlank { "-" },
            parallaxScore = parallaxConfidenceScore,
            isContinuous = isSensorChainContinuous,
            payloadHash = payloadSha256Hex,
            signatureBase64 = signatureBase64,
            publicKeyBase64 = publicKeyBase64
        )
    }
}
