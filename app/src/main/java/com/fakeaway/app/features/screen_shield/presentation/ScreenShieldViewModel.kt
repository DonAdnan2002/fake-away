package com.fakeaway.app.features.screen_shield.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fakeaway.app.core.domain.AppLanguage
import com.fakeaway.app.core.domain.DispatcherProvider
import com.fakeaway.app.features.screen_shield.data.ScreenImageCropper
import com.fakeaway.app.features.screen_shield.data.ShieldRuntimeState
import com.fakeaway.app.features.screen_shield.domain.AnalyzeImageUseCase
import com.fakeaway.app.features.screen_shield.domain.AnalyzeScreenUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ScreenShieldViewModel(
    private val analyzeImageUseCase: AnalyzeImageUseCase,
    private val analyzeScreenUseCase: AnalyzeScreenUseCase,
    private val shieldRuntimeState: ShieldRuntimeState,
    private val dispatchers: DispatcherProvider,
    private val currentLanguage: () -> AppLanguage
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScreenShieldUiState())
    val uiState: StateFlow<ScreenShieldUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            shieldRuntimeState.isShieldRunning.collect { running ->
                _uiState.update { it.copy(isShieldActive = running) }
            }
        }
        viewModelScope.launch {
            shieldRuntimeState.latestScreenScan.collect { scan ->
                if (scan != null) {
                    _uiState.update {
                        it.copy(
                            latestScreenResult = scan,
                            latestImageResult = scan.imageResult,
                            selectedPreviewPath = scan.previewImagePath
                        )
                    }
                }
            }
        }
    }

    fun inspectImageUri(
        context: Context,
        uri: Uri,
        runFullScreenOcrToo: Boolean = true,
        bannerMessage: String? = null
    ) {
        _uiState.update { it.copy(isAnalyzing = true, bannerMessage = bannerMessage) }
        viewModelScope.launch {
            val loaded = withContext(dispatchers.io) {
                runCatching {
                    val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching null
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)
                    val sample = calculateInSampleSize(opts.outWidth, opts.outHeight, 1080, 1920)
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                    val bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
                        ?: return@runCatching null
                    val previewPath = File(context.cacheDir, "last_picked_image.jpg").let { f ->
                        FileOutputStream(f).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 85, out) }
                        f.absolutePath
                    }
                    Triple(bmp, rawBytes, previewPath)
                }.getOrNull()
            }

            if (loaded == null) {
                _uiState.update { it.copy(isAnalyzing = false) }
                return@launch
            }

            val (bitmap, rawBytes, previewPath) = loaded
            try {
                val pixels = ScreenImageCropper.toArgbPixels(bitmap)
                if (runFullScreenOcrToo) {
                    val screenRes = analyzeScreenUseCase(
                        ocrPixels = pixels,
                        ocrWidth = bitmap.width,
                        ocrHeight = bitmap.height,
                        mediaPixels = pixels,
                        mediaWidth = bitmap.width,
                        mediaHeight = bitmap.height,
                        uiLanguage = currentLanguage(),
                        previewImagePath = previewPath
                    )
                    // Also inject rawBytes metadata inspection into image result
                    val rawImgRes = analyzeImageUseCase(
                        argbPixels = pixels,
                        width = bitmap.width,
                        height = bitmap.height,
                        rawBytes = rawBytes,
                        title = "Shared / Picked Image"
                    )
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            latestScreenResult = screenRes.copy(imageResult = rawImgRes),
                            latestImageResult = rawImgRes,
                            selectedPreviewPath = previewPath
                        )
                    }
                } else {
                    val imgRes = analyzeImageUseCase(
                        argbPixels = pixels,
                        width = bitmap.width,
                        height = bitmap.height,
                        rawBytes = rawBytes
                    )
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            latestImageResult = imgRes,
                            selectedPreviewPath = previewPath
                        )
                    }
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun calculateInSampleSize(w: Int, h: Int, reqW: Int, reqH: Int): Int {
        var inSample = 1
        while (h / inSample > reqH * 2 || w / inSample > reqW * 2) {
            inSample *= 2
        }
        return inSample
    }
}
