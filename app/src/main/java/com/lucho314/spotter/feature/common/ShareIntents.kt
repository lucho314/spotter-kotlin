package com.lucho314.spotter.feature.common

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens the system share sheet for [text]. @return false if no activity can handle it. */
fun Context.launchShareText(text: String, chooserTitle: String): Boolean {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    return try {
        startActivity(Intent.createChooser(intent, chooserTitle))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/**
 * Opens the system share sheet for the file at [uri] (expected to be a `FileProvider` Uri). The
 * `clipData` (on top of `EXTRA_STREAM`) is what makes the receiving app's read-URI-permission grant
 * actually work through `Intent.createChooser`. @return false if no activity can handle it.
 */
fun Context.launchShareFile(uri: Uri, mimeType: String, chooserTitle: String): Boolean {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        startActivity(Intent.createChooser(intent, chooserTitle))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

fun Context.copyPlainTextToClipboard(label: String, text: String) {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
}
