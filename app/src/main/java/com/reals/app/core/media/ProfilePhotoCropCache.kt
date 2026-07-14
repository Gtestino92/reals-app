package com.reals.app.core.media

import android.content.Context
import android.net.Uri
import java.io.File

internal const val ProfilePhotoCropCacheDirectoryName: String = "profile-photo-crops"

internal fun profilePhotoCropCacheDirectory(context: Context): File =
    File(context.cacheDir, ProfilePhotoCropCacheDirectoryName).also { it.mkdirs() }

internal fun isOwnedProfilePhotoCropFile(uri: Uri, cacheDir: File): Boolean {
    return isOwnedProfilePhotoCropFile(
        scheme = uri.scheme,
        path = uri.path,
        cacheDir = cacheDir,
    )
}

internal fun isOwnedProfilePhotoCropFile(scheme: String?, path: String?, cacheDir: File): Boolean {
    if (scheme != "file") return false
    val file = path?.let(::File) ?: return false
    return isOwnedProfilePhotoCropFile(file, cacheDir)
}

internal fun isOwnedProfilePhotoCropFile(file: File, cacheDir: File): Boolean {
    val canonicalFile = runCatching { file.canonicalFile }.getOrNull() ?: return false
    val canonicalDirectory = runCatching { cacheDir.canonicalFile }.getOrNull() ?: return false
    return canonicalFile.isFile && canonicalFile.parentFile == canonicalDirectory
}

internal fun deleteOwnedProfilePhotoCropFile(uri: Uri, cacheDir: File) {
    if (!isOwnedProfilePhotoCropFile(uri, cacheDir)) return
    runCatching { File(requireNotNull(uri.path)).delete() }
}

internal fun deleteStaleProfilePhotoCropFiles(cacheDir: File, nowMillis: Long, maxAgeMillis: Long) {
    val canonicalDirectory = runCatching { cacheDir.canonicalFile }.getOrNull() ?: return
    cacheDir.listFiles()?.forEach { file ->
        val canonicalFile = runCatching { file.canonicalFile }.getOrNull() ?: return@forEach
        if (
            canonicalFile.isFile &&
            canonicalFile.parentFile == canonicalDirectory &&
            nowMillis - canonicalFile.lastModified() > maxAgeMillis
        ) {
            runCatching { canonicalFile.delete() }
        }
    }
}
