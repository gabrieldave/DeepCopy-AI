package com.blackeye.klipnote

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.core.content.ContextCompat

fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText("KlipNote", text))
}

/**
 * Archivo guardado en Descargas/KlipNote (MediaStore content Uri).
 */
data class SavedMediaInfo(
    val uri: Uri,
    val displayName: String,
    val isVideo: Boolean
) {
    val mimeType: String get() = if (isVideo) "video/mp4" else "audio/mp4"
    val openLabel: String get() = if (isVideo) "Abrir video" else "Abrir audio"
}

object SavedMediaActions {

    fun openWithPlayer(context: Context, media: SavedMediaInfo) {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(media.uri, media.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(Intent.createChooser(view, media.openLabel))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No hay app para abrir este archivo", Toast.LENGTH_SHORT).show()
        }
    }

    fun share(context: Context, media: SavedMediaInfo) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = media.mimeType
            putExtra(Intent.EXTRA_STREAM, media.uri)
            putExtra(Intent.EXTRA_SUBJECT, media.displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(Intent.createChooser(send, "Compartir ${media.displayName}"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No hay app para compartir", Toast.LENGTH_SHORT).show()
        }
    }

    fun openDownloadsFolder(context: Context) {
        val app = context.applicationContext
        // 1) Lista de descargas del sistema
        try {
            val dm = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(dm)
            return
        } catch (_: Exception) {
            // continuar
        }
        // 2) DocumentsUI apuntando a Download/KlipNote
        try {
            val docId = "primary:Download/KlipNote"
            val uri = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                docId
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (Build.VERSION.SDK_INT >= 26) {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                }
            }
            context.startActivity(intent)
            return
        } catch (_: Exception) {
            // continuar
        }
        try {
            val browse = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    Uri.parse("content://com.android.externalstorage.documents/document/primary:Download"),
                    DocumentsContract.Document.MIME_TYPE_DIR
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browse)
            return
        } catch (_: Exception) {
            // fallthrough
        }
        Toast.makeText(
            app,
            "Busca el archivo en Descargas/KlipNote",
            Toast.LENGTH_LONG
        ).show()
    }

    fun fromHistory(
        downloadUri: String?,
        downloadDisplayName: String?,
        downloadIsVideo: Boolean
    ): SavedMediaInfo? {
        if (downloadUri.isNullOrBlank() || downloadDisplayName.isNullOrBlank()) return null
        return runCatching {
            SavedMediaInfo(
                uri = Uri.parse(downloadUri),
                displayName = downloadDisplayName,
                isVideo = downloadIsVideo
            )
        }.getOrNull()
    }
}

fun vibrateShort(context: Context) {
    runCatching {
        val vibrator = ContextCompat.getSystemService(context, android.os.Vibrator::class.java)
            ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(
                android.os.VibrationEffect.createOneShot(40, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(40)
        }
    }
}
