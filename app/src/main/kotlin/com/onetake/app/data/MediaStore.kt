package com.onetake.app.data

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import java.io.File

// MARK: - Media files (recorded takes, slate/full-length clips, exports) — filesDir, not cacheDir

/** Moves real recordings out of the temporary cache directory `CameraController` records into,
 *  so they survive past the recording session instead of being purged by the system (mirrors
 *  iOS's `MediaStore.persist` moving out of tmp into Documents). */
object MediaStore {
    fun directory(context: Context, subfolder: String): File {
        val dir = File(context.filesDir, subfolder)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Moves a just-recorded temp file to permanent on-disk storage, replacing anything already
     *  there. Returns null (rather than a bogus Uri) if there was nothing real to persist — e.g.
     *  no camera hardware, where `CameraController` never produces a real recording. */
    fun persist(context: Context, tempUri: Uri?, name: String, subfolder: String): Uri? {
        if (tempUri == null) return null
        val tempFile = tempUri.path?.let { File(it) } ?: return null
        if (!tempFile.exists()) return null
        val dest = File(directory(context, subfolder), name)
        if (dest.exists()) dest.delete()
        return try {
            if (!tempFile.renameTo(dest)) {
                tempFile.copyTo(dest, overwrite = true)
                tempFile.delete()
            }
            dest.toUri()
        } catch (_: Exception) {
            null
        }
    }

    /** Total bytes under a subfolder — used by Privacy & data instead of guessing. */
    fun sizeOnDisk(context: Context, subfolder: String): Long {
        val dir = File(context.filesDir, subfolder)
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun deleteAll(context: Context, subfolders: List<String>) {
        for (folder in subfolders) {
            File(context.filesDir, folder).deleteRecursively()
        }
    }
}
