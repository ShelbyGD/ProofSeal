package de.proofseal.app.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object ShareHelper {

    fun sharePdfCertificate(
        context: Context,
        pdfFile: File,
        protocolTitle: String,
        protocolId: String,
        primaryValue: String,
        sha256Hash: String
    ) {
        if (!pdfFile.exists()) {
            Toast.makeText(
                context,
                "PDF-Zertifikat wurde nicht gefunden: ${pdfFile.name}",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, pdfFile)

            val shareText = buildString {
                append("🛡️ PROOFSEAL – Kryptografisch gesichertes Beweis-Protokoll\n")
                append("• Titel: $protocolTitle\n")
                append("• Protokoll-ID: $protocolId\n")
                if (primaryValue.isNotBlank()) {
                    append("• Erkannter Wert (OCR/Code): $primaryValue\n")
                }
                append("• SHA-256 Siegel: ${sha256Hash.take(24)}…\n")
                append("Signiert über den Android Hardware-Keystore (ECDSA secp256r1).")
            }

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "ProofSeal Beweis-Zertifikat ($protocolTitle)")
                putExtra(Intent.EXTRA_TEXT, shareText)
                clipData = ClipData.newRawUri(pdfFile.name, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooserIntent = Intent.createChooser(
                sendIntent,
                "ProofSeal PDF-Zertifikat teilen (WhatsApp, E-Mail, Drive…)"
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Fehler beim Teilen des PDFs: ${e.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun openPdfExternally(context: Context, pdfFile: File) {
        if (!pdfFile.exists()) {
            Toast.makeText(context, "PDF-Datei nicht vorhanden.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, pdfFile)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(viewIntent, "PDF öffnen mit…"))
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Kein externer PDF-Viewer installiert – nutze die integrierte Vorschau.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
