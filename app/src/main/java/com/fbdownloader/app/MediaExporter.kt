package com.fbdownloader.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Copies finished files into the public Downloads/FBDownloader folder (no storage permission needed). */
object MediaExporter {

    const val FOLDER = "FBDownloader"
    /** RELATIVE_PATH as MediaStore stores it (with trailing slash). */
    val RELATIVE_PATH = "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/"

    /** Finds a file in Downloads/FBDownloader by exact display name (only files this app created are visible). */
    fun find(context: Context, displayName: String): Uri? {
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
            arrayOf(displayName, RELATIVE_PATH),
            null
        )?.use { c ->
            if (c.moveToFirst()) {
                return Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0).toString())
            }
        }
        return null
    }

    /**
     * Returns "Name.ext" if free, otherwise "Name_1.ext", "Name_2.ext", ...
     * Works the same for videos, GIFs, audio and images.
     */
    fun uniqueName(context: Context, baseName: String, ext: String): String {
        val suffix = if (ext.isEmpty()) "" else ".$ext"
        var candidate = "$baseName$suffix"
        var counter = 1
        while (find(context, candidate) != null) {
            candidate = "${baseName}_$counter$suffix"
            counter++
        }
        return candidate
    }

    fun mimeFor(ext: String): String = when (ext.lowercase()) {
        "mp4" -> "video/mp4"
        "gif" -> "image/gif"
        "mp3" -> "audio/mpeg"
        "jpg", "jpeg" -> "image/jpeg"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "m4a" -> "audio/mp4"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    fun export(context: Context, file: File, displayName: String = file.name): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeFor(file.extension))
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: return false
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }
}
