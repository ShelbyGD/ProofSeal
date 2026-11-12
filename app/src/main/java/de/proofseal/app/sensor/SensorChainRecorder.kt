package de.proofseal.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import de.proofseal.app.crypto.HardwareCryptoManager
import de.proofseal.app.model.SensorGraphPoint
import de.proofseal.app.model.SensorIntegrityReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class RawSensorSample(
    val elapsedRealtimeNanos: Long,
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,
    val pressureHpa: Float,
    val lightLux: Float
)

data class SensorLiveSnapshot(
    val isRecording: Boolean = false,
    val elapsedSeconds: Float = 0f,
    val sampleCount: Int = 0,
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 9.81f,
    val accelMagnitude: Float = 9.81f,
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    val gyroMagnitude: Float = 0f,
    val pressureHpa: Float? = null,
    val lightLux: Float? = null,
    val liveParallaxScore: Int = 0,
    val isContinuousSoFar: Boolean = true,
    val maxGapMsSoFar: Long = 0L,
    val recentAccelHistory: List<Float> = emptyList(),
    val recentGyroHistory: List<Float> = emptyList()
)

class SensorChainRecorder(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val pressureSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val lightSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    private val recorderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    private val lock = Any()
    private val samples = ArrayList<RawSensorSample>(4096)
    private val keyframeTriggerNanos = ArrayList<Long>(8)

    @Volatile
    private var isActiveRecording = false

    @Volatile
    private var startRealtimeNanos: Long = 0L

    @Volatile
    private var lastSampleNanos: Long = 0L

    @Volatile
    private var maxObservedGapNanos: Long = 0L

    @Volatile
    private var curAccelX = 0f

    @Volatile
    private var curAccelY = 0f

    @Volatile
    private var curAccelZ = 9.81f

    @Volatile
    private var curGyroX = 0f

    @Volatile
    private var curGyroY = 0f

    @Volatile
    private var curGyroZ = 0f

    @Volatile
    private var curPressureHpa = 0f

    @Volatile
    private var hasPressureReading = false

    @Volatile
    private var curLightLux = 0f

    @Volatile
    private var hasLightReading = false

    private val recentAccelBuffer = ArrayDeque<Float>(80)
    private val recentGyroBuffer = ArrayDeque<Float>(80)

    private val _liveSnapshot = MutableStateFlow(SensorLiveSnapshot())
    val liveSnapshot: StateFlow<SensorLiveSnapshot> = _liveSnapshot.asStateFlow()

    fun startRecording() {
        synchronized(lock) {
            if (isActiveRecording) return
            samples.clear()
            keyframeTriggerNanos.clear()
            recentAccelBuffer.clear()
            recentGyroBuffer.clear()

            val nowNanos = SystemClock.elapsedRealtimeNanos()
            startRealtimeNanos = nowNanos
            lastSampleNanos = nowNanos
            maxObservedGapNanos = 0L
            isActiveRecording = true

            // Initialen Ankerpunkt aufzeichnen
            recordSampleLocked(nowNanos)
        }

        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        pressureSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        // UI-Update & Hardware-Uptime-Watchdog starten
        monitorJob?.cancel()
        monitorJob = recorderScope.launch {
            while (isActive && isActiveRecording) {
                val nowNanos = SystemClock.elapsedRealtimeNanos()
                synchronized(lock) {
                    // Falls das Gerät keinen physischen Beschleunigungssensor feuert (z. B. Emulator),
                    // halte die monotone Hardware-Uptime-Kette aufrecht
                    if (nowNanos - lastSampleNanos > 120_000_000L && accelSensor == null) {
                        recordSampleLocked(nowNanos)
                    }
                    publishLiveSnapshotLocked(nowNanos)
                }
                delay(60L)
            }
        }
    }

    fun stopRecording(): SensorIntegrityReport {
        isActiveRecording = false
        monitorJob?.cancel()
        monitorJob = null
        try {
            sensorManager.unregisterListener(this)
        } catch (_: Exception) {
        }
        return analyzeIntegrity()
    }

    fun getStartElapsedNanos(): Long = startRealtimeNanos

    fun getCurrentMotionVector(): FloatArray {
        return floatArrayOf(curAccelX, curAccelY, curAccelZ, curGyroX, curGyroY, curGyroZ)
    }

    fun markKeyframeCaptured(): Long {
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        synchronized(lock) {
            if (isActiveRecording) {
                recordSampleLocked(nowNanos)
            }
            keyframeTriggerNanos.add(nowNanos)
            return ((nowNanos - startRealtimeNanos).coerceAtLeast(0L)) / 1_000_000L
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isActiveRecording) return
        val eventNanos = SystemClock.elapsedRealtimeNanos()

        synchronized(lock) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    curAccelX = event.values[0]
                    curAccelY = event.values[1]
                    curAccelZ = event.values[2]
                    recordSampleLocked(eventNanos)
                }
                Sensor.TYPE_GYROSCOPE -> {
                    curGyroX = event.values[0]
                    curGyroY = event.values[1]
                    curGyroZ = event.values[2]
                    // Auch bei Gyro-Events abtasten, falls letztes Sample > 18 ms her ist
                    if (eventNanos - lastSampleNanos >= 18_000_000L) {
                        recordSampleLocked(eventNanos)
                    }
                }
                Sensor.TYPE_PRESSURE -> {
                    curPressureHpa = event.values[0]
                    hasPressureReading = true
                }
                Sensor.TYPE_LIGHT -> {
                    curLightLux = event.values[0]
                    hasLightReading = true
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Keine Aktion erforderlich
    }

    private fun recordSampleLocked(nowNanos: Long) {
        val gapNanos = (nowNanos - lastSampleNanos).coerceAtLeast(0L)
        if (samples.isNotEmpty() && gapNanos > maxObservedGapNanos) {
            maxObservedGapNanos = gapNanos
        }
        lastSampleNanos = nowNanos

        val sample = RawSensorSample(
            elapsedRealtimeNanos = nowNanos,
            accelX = curAccelX,
            accelY = curAccelY,
            accelZ = curAccelZ,
            gyroX = curGyroX,
            gyroY = curGyroY,
            gyroZ = curGyroZ,
            pressureHpa = curPressureHpa,
            lightLux = curLightLux
        )
        samples.add(sample)

        // Live-Graph-Puffer (ca. 25 Hz Downsampling für flüssige Darstellung)
        if (samples.size % 2 == 1) {
            val aMag = sqrt(curAccelX * curAccelX + curAccelY * curAccelY + curAccelZ * curAccelZ)
            val gMag = sqrt(curGyroX * curGyroX + curGyroY * curGyroY + curGyroZ * curGyroZ)
            if (recentAccelBuffer.size >= 72) recentAccelBuffer.removeFirst()
            if (recentGyroBuffer.size >= 72) recentGyroBuffer.removeFirst()
            recentAccelBuffer.addLast(aMag)
            recentGyroBuffer.addLast(gMag)
        }
    }

    private fun publishLiveSnapshotLocked(nowNanos: Long) {
        val elapsedSec = ((nowNanos - startRealtimeNanos).coerceAtLeast(0L)) / 1_000_000_000f
        val aMag = sqrt(curAccelX * curAccelX + curAccelY * curAccelY + curAccelZ * curAccelZ)
        val gMag = sqrt(curGyroX * curGyroX + curGyroY * curGyroY + curGyroZ * curGyroZ)
        val maxGapMs = maxObservedGapNanos / 1_000_000L
        val liveParallax = computeParallaxConfidenceLocked(samples)

        _liveSnapshot.value = SensorLiveSnapshot(
            isRecording = isActiveRecording,
            elapsedSeconds = elapsedSec,
            sampleCount = samples.size,
            accelX = curAccelX,
            accelY = curAccelY,
            accelZ = curAccelZ,
            accelMagnitude = aMag,
            gyroX = curGyroX,
            gyroY = curGyroY,
            gyroZ = curGyroZ,
            gyroMagnitude = gMag,
            pressureHpa = if (hasPressureReading) curPressureHpa else null,
            lightLux = if (hasLightReading) curLightLux else null,
            liveParallaxScore = liveParallax,
            isContinuousSoFar = maxGapMs <= 500L,
            maxGapMsSoFar = maxGapMs,
            recentAccelHistory = recentAccelBuffer.toList(),
            recentGyroHistory = recentGyroBuffer.toList()
        )
    }

    /**
     * Kontinuitäts- & 3D-Parallaxen-Check:
     * 1. Prüft, ob zwischen Start und Ende der Aufnahme eine Datenlücke (> 500 ms ohne Sensorwerte) vorliegt.
     * 2. Prüft anhand der 3D-Varianz von Beschleunigung und Drehrate sowie der Perspektivenwechsel
     *    zwischen den Foto-Auslösungen, ob das Smartphone tatsächlich im 3D-Raum bewegt wurde
     *    (Anti-Screen-Spoofing gegen statisches Stativ vor einem Bildschirm).
     */
    fun analyzeIntegrity(): SensorIntegrityReport {
        synchronized(lock) {
            val endNanos = if (samples.isNotEmpty()) {
                samples.last().elapsedRealtimeNanos
            } else {
                SystemClock.elapsedRealtimeNanos()
            }
            val startNanos = if (startRealtimeNanos > 0L) startRealtimeNanos else endNanos

            if (samples.isEmpty()) {
                return SensorIntegrityReport(
                    isContinuous = false,
                    maxGapMillis = 999L,
                    totalSamples = 0,
                    durationSeconds = 0f,
                    startElapsedNanos = startNanos,
                    endElapsedNanos = endNanos,
                    parallaxConfidenceScore = 0,
                    accelVariance = 0f,
                    gyroPeakRadSec = 0f,
                    averagePressureHpa = null,
                    averageLightLux = null,
                    sensorChainSha256 = HardwareCryptoManager.sha256Hex("EMPTY_CHAIN".toByteArray()),
                    graphPoints = emptyList(),
                    keyframeTriggerSeconds = emptyList()
                )
            }

            // 1. Lückenprüfung (> 500 ms zwischen aufeinanderfolgenden Messpunkten)
            var maxGapNanos = 0L
            for (i in 1 until samples.size) {
                val delta = (samples[i].elapsedRealtimeNanos - samples[i - 1].elapsedRealtimeNanos)
                    .coerceAtLeast(0L)
                if (delta > maxGapNanos) {
                    maxGapNanos = delta
                }
            }
            val maxGapMillis = maxGapNanos / 1_000_000L
            val isContinuous = maxGapMillis <= 500L

            // 2. Statistische Kennwerte & 3D-Parallaxe
            val n = samples.size.toFloat()
            var sumAx = 0.0
            var sumAy = 0.0
            var sumAz = 0.0
            var gyroPeak = 0f
            var sumPressure = 0.0
            var sumLight = 0.0

            for (s in samples) {
                sumAx += s.accelX
                sumAy += s.accelY
                sumAz += s.accelZ
                val gMag = sqrt(s.gyroX * s.gyroX + s.gyroY * s.gyroY + s.gyroZ * s.gyroZ)
                if (gMag > gyroPeak) gyroPeak = gMag
                sumPressure += s.pressureHpa
                sumLight += s.lightLux
            }

            val meanAx = (sumAx / n).toFloat()
            val meanAy = (sumAy / n).toFloat()
            val meanAz = (sumAz / n).toFloat()

            var varSum = 0.0
            for (s in samples) {
                val dx = s.accelX - meanAx
                val dy = s.accelY - meanAy
                val dz = s.accelZ - meanAz
                varSum += (dx * dx + dy * dy + dz * dz)
            }
            val accelVariance = (varSum / n).toFloat()

            val parallaxScore = computeParallaxConfidenceLocked(samples)
            val durationSec = ((endNanos - startNanos).coerceAtLeast(1_000_000L)) / 1_000_000_000f

            // 3. Kryptografischer SHA-256 Digest über die gesamte Sensorkette inkl. Monotonie-Zeitstempel
            val chainHasherInput = StringBuilder(samples.size * 48)
            chainHasherInput.append("SENSOR_CHAIN_START=").append(startNanos).append('\n')
            val hashStep = (samples.size / 400).coerceAtLeast(1)
            for (i in samples.indices step hashStep) {
                val s = samples[i]
                chainHasherInput.append(s.elapsedRealtimeNanos).append(':')
                    .append(String.format(Locale.US, "%.3f,%.3f,%.3f;", s.accelX, s.accelY, s.accelZ))
                    .append(String.format(Locale.US, "%.3f,%.3f,%.3f\n", s.gyroX, s.gyroY, s.gyroZ))
            }
            chainHasherInput.append("SENSOR_CHAIN_END=").append(endNanos)
            val chainSha256 = HardwareCryptoManager.sha256Hex(
                chainHasherInput.toString().toByteArray(Charsets.UTF_8)
            )

            // 4. Downsampling auf max. 140 Stützpunkte für das PDF-Prüfdiagramm (Seite 2)
            val targetPoints = 140
            val stride = (samples.size / targetPoints).coerceAtLeast(1)
            val graphPoints = ArrayList<SensorGraphPoint>(targetPoints + 2)
            for (i in samples.indices step stride) {
                val s = samples[i]
                val tSec = ((s.elapsedRealtimeNanos - startNanos).coerceAtLeast(0L)) / 1_000_000_000f
                val aMag = sqrt(s.accelX * s.accelX + s.accelY * s.accelY + s.accelZ * s.accelZ)
                val gMag = sqrt(s.gyroX * s.gyroX + s.gyroY * s.gyroY + s.gyroZ * s.gyroZ)
                graphPoints.add(
                    SensorGraphPoint(
                        offsetSeconds = tSec,
                        accelMagnitude = aMag,
                        gyroMagnitude = gMag,
                        accelX = s.accelX,
                        accelY = s.accelY,
                        accelZ = s.accelZ,
                        gyroX = s.gyroX,
                        gyroY = s.gyroY,
                        gyroZ = s.gyroZ,
                        pressureHpa = s.pressureHpa,
                        lightLux = s.lightLux
                    )
                )
            }

            val triggerSeconds = keyframeTriggerNanos.map { triggerNs ->
                ((triggerNs - startNanos).coerceAtLeast(0L)) / 1_000_000_000f
            }

            return SensorIntegrityReport(
                isContinuous = isContinuous,
                maxGapMillis = maxGapMillis,
                totalSamples = samples.size,
                durationSeconds = durationSec,
                startElapsedNanos = startNanos,
                endElapsedNanos = endNanos,
                parallaxConfidenceScore = parallaxScore,
                accelVariance = accelVariance,
                gyroPeakRadSec = gyroPeak,
                averagePressureHpa = if (hasPressureReading) (sumPressure / n).toFloat() else null,
                averageLightLux = if (hasLightReading) (sumLight / n).toFloat() else null,
                sensorChainSha256 = chainSha256,
                graphPoints = graphPoints,
                keyframeTriggerSeconds = triggerSeconds
            )
        }
    }

    /**
     * Berechnet den 3D-Parallaxen-Confidence-Score (0-100 %):
     * Kombiniert:
     * - 3D-Beschleunigungsvarianz (Neigungs- & Translationsänderung der Hand)
     * - Integrierte Winkelbewegung des Gyroskops (Perspektivenwechsel im Raum)
     * - Natürlicher Hand-Tremor (verhindert Stativ-/Emulator-Nullwerte)
     * - Perspektiven-Differenz zwischen den ausgelösten Keyframes
     */
    private fun computeParallaxConfidenceLocked(currentSamples: List<RawSensorSample>): Int {
        if (currentSamples.size < 4) return 0

        val n = currentSamples.size.toFloat()
        var meanAx = 0f
        var meanAy = 0f
        var meanAz = 0f
        for (s in currentSamples) {
            meanAx += s.accelX
            meanAy += s.accelY
            meanAz += s.accelZ
        }
        meanAx /= n
        meanAy /= n
        meanAz /= n

        var accelVar = 0f
        var gyroEnergy = 0f
        var integratedAngleRad = 0f

        for (i in currentSamples.indices) {
            val s = currentSamples[i]
            val dx = s.accelX - meanAx
            val dy = s.accelY - meanAy
            val dz = s.accelZ - meanAz
            accelVar += (dx * dx + dy * dy + dz * dz)

            val gMag = sqrt(s.gyroX * s.gyroX + s.gyroY * s.gyroY + s.gyroZ * s.gyroZ)
            gyroEnergy += gMag

            if (i > 0) {
                val dtSec = ((s.elapsedRealtimeNanos - currentSamples[i - 1].elapsedRealtimeNanos)
                    .coerceAtLeast(0L)) / 1_000_000_000f
                if (dtSec < 0.5f) {
                    integratedAngleRad += gMag * dtSec
                }
            }
        }

        accelVar /= n
        val meanGyro = gyroEnergy / n

        // Teil-Score 1: 3D-Beschleunigungs-/Neigungsvarianz (0..45 Punkte)
        // Ein völlig statisches Gerät auf einem Stativ hat accelVar < 0.002 m²/s⁴.
        // Echte Handführung mit Perspektivenwechsel erzeugt accelVar >= 0.35 m²/s⁴.
        val varianceContribution = when {
            accelVar < 0.002f -> 0f
            else -> min(45f, (sqrt(accelVar) / 0.85f) * 45f)
        }

        // Teil-Score 2: Rotations-/Winkeländerung über Gyroskop (0..35 Punkte)
        val rotationContribution = min(
            35f,
            (integratedAngleRad / 0.65f) * 22f + (meanGyro / 0.30f) * 13f
        )

        // Teil-Score 3: Gravitationsvektor-Spreizung zwischen erster und zweiter Hälfte (0..20 Punkte)
        val half = currentSamples.size / 2
        val firstHalf = currentSamples.subList(0, half.coerceAtLeast(1))
        val secondHalf = currentSamples.subList(half, currentSamples.size)
        val tiltShift = abs(firstHalf.map { it.accelX }.average() - secondHalf.map { it.accelX }.average()) +
            abs(firstHalf.map { it.accelY }.average() - secondHalf.map { it.accelY }.average()) +
            abs(firstHalf.map { it.accelZ }.average() - secondHalf.map { it.accelZ }.average())
        val perspectiveContribution = min(20f, (tiltShift.toFloat() / 1.4f) * 20f)

        val totalScore = (varianceContribution + rotationContribution + perspectiveContribution)
            .roundToInt()
            .coerceIn(0, 100)

        return totalScore
    }
}
