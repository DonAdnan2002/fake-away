package com.fakeaway.app.core.onnx

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Progress event emitted while downloading an optional ONNX neural pack.
 */
data class ModelDownloadProgress(
    val spec: ModelSpec,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val percent: Int,
    val completed: Boolean = false,
    val error: String? = null
)

/**
 * Manages resolution, bundled asset extraction, streaming download, and removal of ONNX models.
 */
class OnnxModelStore(
    private val context: Context,
    private val httpClient: OkHttpClient
) {
    private val modelsDir: File by lazy {
        File(context.filesDir, "models").apply { mkdirs() }
    }

    private val _installedStates = MutableStateFlow(readInstalledMap())
    val installedStates: StateFlow<Map<ModelSpec, Boolean>> = _installedStates.asStateFlow()

    /**
     * Returns the readable model [File] if installed locally or bundled in APK assets, else null.
     */
    fun resolveModelFile(spec: ModelSpec): File? {
        val localFile = File(modelsDir, spec.fileName)
        if (localFile.exists() && localFile.length() > 1024L) {
            return localFile
        }
        return extractBundledAssetIfPresent(spec, localFile)
    }

    fun isInstalled(spec: ModelSpec): Boolean = resolveModelFile(spec) != null

    fun refreshStates() {
        _installedStates.value = readInstalledMap()
    }

    fun deleteModel(spec: ModelSpec): Boolean {
        val file = File(modelsDir, spec.fileName)
        val tmp = File(modelsDir, "${spec.fileName}.part")
        if (tmp.exists()) tmp.delete()
        val deleted = !file.exists() || file.delete()
        refreshStates()
        return deleted
    }

    /**
     * Streams a model download into a `.part` temp file and atomically renames upon completion.
     */
    fun downloadModel(spec: ModelSpec): Flow<ModelDownloadProgress> = flow {
        val target = File(modelsDir, spec.fileName)
        val tempFile = File(modelsDir, "${spec.fileName}.part")
        val request = Request.Builder().url(spec.downloadUrl).build()
        val call = httpClient.newCall(request)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    emit(
                        ModelDownloadProgress(
                            spec = spec,
                            bytesDownloaded = 0L,
                            totalBytes = 0L,
                            percent = 0,
                            error = "HTTP ${response.code}"
                        )
                    )
                    return@flow
                }
                val body = response.body ?: error("Empty response body")
                val total = body.contentLength().coerceAtLeast((spec.expectedSizeMb * 1024 * 1024).toLong())
                var downloaded = 0L
                var lastPct = -1
                val buffer = ByteArray(16 * 1024)

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val pct = ((downloaded * 100L) / total).toInt().coerceIn(0, 99)
                            if (pct != lastPct) {
                                lastPct = pct
                                emit(ModelDownloadProgress(spec, downloaded, total, pct))
                            }
                        }
                        output.flush()
                    }
                }

                if (target.exists()) target.delete()
                check(tempFile.renameTo(target)) { "Failed to finalize model file" }
                refreshStates()
                emit(
                    ModelDownloadProgress(
                        spec = spec,
                        bytesDownloaded = downloaded,
                        totalBytes = downloaded,
                        percent = 100,
                        completed = true
                    )
                )
            }
        } catch (t: Throwable) {
            if (tempFile.exists()) tempFile.delete()
            emit(
                ModelDownloadProgress(
                    spec = spec,
                    bytesDownloaded = 0L,
                    totalBytes = 0L,
                    percent = 0,
                    error = t.message ?: "Download failed"
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    private fun extractBundledAssetIfPresent(spec: ModelSpec, dest: File): File? {
        val assetList = runCatching { context.assets.list("models")?.toSet() }.getOrNull().orEmpty()
        if (!assetList.contains(spec.fileName)) return null
        return runCatching {
            val tmp = File(modelsDir, "${spec.fileName}.asset.part")
            context.assets.open("models/${spec.fileName}").use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            }
            if (dest.exists()) dest.delete()
            tmp.renameTo(dest)
            dest
        }.getOrNull()
    }

    private fun readInstalledMap(): Map<ModelSpec, Boolean> =
        ModelSpec.entries.associateWith { spec ->
            val file = File(modelsDir, spec.fileName)
            (file.exists() && file.length() > 1024L) ||
                runCatching { context.assets.list("models")?.contains(spec.fileName) == true }
                    .getOrDefault(false)
        }
}
