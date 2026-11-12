# 🛡️ ProofSeal (`de.proofseal.app`)

**ProofSeal** ist ein kryptografischer Beweis-Protokoll-Generator (*„Digitaler Zeuge“*) für Android (`minSdk = 29`, `targetSdk = 35`, `compileSdk = 35`, 100 % Kotlin & Jetpack Compose Material 3).

Statt einzelner, leicht manipulierbarer Fotos zeichnet ProofSeal eine **lückenlose Sensorkette (Continuous Sensor Chain)** auf, prüft über 3D-Beschleunigungs- und Gyroskopdaten gegen abgefilmte Bildschirme (**Anti-Screen-Spoofing / 3D-Parallaxe**), extrahiert Seriennummern, Sendungsnummern und Zählerstände 100 % offline per **Google ML Kit (OCR & Barcode)** und generiert lokal ein **WhatsApp-festes, mit dem Android Hardware-Keystore (`StrongBox` / `TEE`) signiertes 2-Seiten-DIN-A4-PDF-Zertifikat**.

---

## Architektur & Kern-Module

- **`HardwareCryptoManager.kt` (`de.proofseal.app.crypto`):**
  - Erzeugt ein `ECDSA` Schlüsselpaar (`secp256r1`, `SHA256withECDSA`) im `AndroidKeyStore`.
  - **StrongBox-Fallback:** Versucht zuerst `.setIsStrongBoxBacked(true)` (dedizierter Hardware-Sicherheitschip) und fällt bei `StrongBoxUnavailableException` / `ProviderException` automatisch und sauber auf `.setIsStrongBoxBacked(false)` (Standard-TEE) zurück.
  - Signiert das kanonische Beweispaket (`signPayload`) und verifiziert eigene oder gescannte Zertifikate lokal (`verifySeal`).
- **`SensorChainRecorder.kt` (`de.proofseal.app.sensor`):**
  - Hochfrequente Aufzeichnung (`SENSOR_DELAY_GAME`) von `TYPE_ACCELEROMETER`, `TYPE_GYROSCOPE`, `TYPE_PRESSURE` und `TYPE_LIGHT`.
  - Monotone Hardware-Uptime (`SystemClock.elapsedRealtimeNanos()`) gegen Systemuhr-Manipulation.
  - `analyzeIntegrity()` prüft auf Datenlücken (`> 500 ms`) und berechnet den `ParallaxConfidenceScore` (`0–100 %`).
- **`GuidedCaptureScreen.kt` (`de.proofseal.app.ui.screens`):**
  - Echtzeit-Sensor-Oszilloskop am oberen Bildschirmrand + CameraX + Bundled Google ML Kit OCR & Barcode-Scanner.
  - **Modus 1:** *Paket-Versand & Verkauf* (3 Pflicht-Schritte / 4 Keyframes ohne Unterbrechung der Sensorkette).
  - **Modus 2:** *Zählerstand, Kfz & Übergabe* (3D-Parallaxen-Prüfung + direktes OCR-Prüffeld).
  - **Modus 3:** *Vorfall & Zustands-Beweis* (Schnell-Protokoll inkl. GPS-Koordinaten, Zeugenfeld & Freitext-Notiz).
- **`ProofPdfGenerator.kt` (`de.proofseal.app.pdf`):**
  - Erzeugt über `android.graphics.pdf.PdfDocument` ein druckbares 2-Seiten-DIN-A4-PDF (`595 x 842 pt`, `< 2,5 MB`):
    - **Seite 1:** Kopfbalken, Verifikations-Badge, strukturierte Metadaten-Box und 2x2 Keyframe-Raster mit Millisekunden-Offset (`+0.00s`, `+4.82s` etc.) und individuellem Bild-SHA-256.
    - **Seite 2:** Gezeichnetes lückenloses Sensor-Verlaufsdiagramm (`Canvas.drawLine`) mit Foto-Trigger-Markierungen, kryptografischer Signaturblock in Monospace-Schrift, eingebetteter ZXing-Verifikations-QR-Code und rechtlicher Fußhinweis.
- **`SealVerifierScreen.kt` (`de.proofseal.app.ui.screens`):**
  - Eingebauter QR-Code-Scanner & mathematischer `ECDSA`-Echtheits-Prüfer inkl. Manipulations-Simulation.
- **Room Database & `ShareHelper.kt` (`de.proofseal.app.data`, `de.proofseal.app.util`):**
  - Lokale Speicherung und Volltextsuche über alle Protokolle (`ProofRecordEntity`, `ProofRecordDao`, `ProofDatabase`) sowie direktes Teilen per WhatsApp, E-Mail oder Google Drive über `FileProvider`.

---

## Cloud-Build via GitHub Actions

Die Datei `.github/workflows/android-build.yml` kompiliert bei jedem `push` oder `workflow_dispatch` über Java 17 (`temurin`) und Gradle `8.9` (`gradle assembleDebug`) automatisch die Debug-APK und stellt sie als GitHub-Action-Artifact **`ProofSeal-Debug-APK`** bereit.