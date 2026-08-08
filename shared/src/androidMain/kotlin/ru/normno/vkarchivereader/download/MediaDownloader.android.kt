package ru.normno.vkarchivereader.download

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private class AndroidMediaDownloader(private val context: Context) : MediaDownloader {
    private val client = HttpClient(OkHttp)

    override suspend fun download(url: String, fileName: String): DownloadResult =
        withContext(Dispatchers.IO) {
            try {
                val bytes = client.get(url).body<ByteArray>()
                if (bytes.isEmpty()) return@withContext DownloadResult.FAILED
                val mime = mimeTypeForName(fileName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveViaMediaStore(bytes, fileName, mime)
                else saveLegacy(bytes, fileName, mime)
            } catch (e: Throwable) {
                DownloadResult.FAILED
            }
        }

    // Images land in Pictures/gallery; everything else (video, audio, docs) in Downloads.
    private fun saveViaMediaStore(bytes: ByteArray, fileName: String, mime: String): DownloadResult {
        val isImage = isImageMime(mime)
        val collection =
            if (isImage) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val relativePath =
            if (isImage) "${Environment.DIRECTORY_PICTURES}/VkArchiveReader"
            else "${Environment.DIRECTORY_DOWNLOADS}/VkArchiveReader"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: return DownloadResult.FAILED
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return DownloadResult.FAILED
        return DownloadResult.SAVED
    }

    private fun saveLegacy(bytes: ByteArray, fileName: String, mime: String): DownloadResult {
        val target = if (isImageMime(mime)) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS
        @Suppress("DEPRECATION")
        val dir = File(
            Environment.getExternalStoragePublicDirectory(target),
            "VkArchiveReader",
        ).apply { mkdirs() }
        File(dir, fileName).writeBytes(bytes)
        return DownloadResult.SAVED
    }
}

@Composable
actual fun rememberMediaDownloader(): MediaDownloader {
    val context = LocalContext.current

    // Pre-Android 10 needs the write-storage permission; request it up front.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { }
        LaunchedEffect(Unit) {
            val granted = context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    return remember { AndroidMediaDownloader(context.applicationContext) }
}
