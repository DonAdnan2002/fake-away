package com.fakeaway.app.features.share_router

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns

/**
 * Typed payload routed from external apps (WhatsApp, Telegram, Facebook, Gallery, Browsers).
 */
sealed interface SharedPayload {
    data class Audio(val uriString: String, val displayName: String) : SharedPayload
    data class Video(val uriString: String, val displayName: String) : SharedPayload
    data class Image(val uriString: String, val displayName: String) : SharedPayload
    data class TextOrUrl(val text: String, val isFacebookLink: Boolean) : SharedPayload
}

/**
 * Parses incoming Android share or view intents into strongly-typed [SharedPayload] targets.
 */
class ParseShareIntentUseCase {

    fun parse(
        action: String?,
        mimeType: String?,
        streamUriString: String?,
        fileNameHint: String?,
        sharedText: String?
    ): SharedPayload? {
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_VIEW) {
            return null
        }

        val uriStr = streamUriString?.trim().orEmpty()
        val mime = mimeType?.lowercase()?.trim().orEmpty()
        val nameHint = fileNameHint?.trim().orEmpty()

        if (uriStr.isNotEmpty()) {
            val label = nameHint.ifEmpty { uriStr.substringAfterLast('/') }.ifEmpty { "Shared Media" }
            val ext = (nameHint.ifEmpty { uriStr }).substringAfterLast('.', "").lowercase()
                .substringBefore('?')

            return when {
                mime.startsWith("audio/") || ext in AUDIO_EXTENSIONS ->
                    SharedPayload.Audio(uriStr, label)
                mime.startsWith("video/") || ext in VIDEO_EXTENSIONS ->
                    SharedPayload.Video(uriStr, label)
                mime.startsWith("image/") || ext in IMAGE_EXTENSIONS ->
                    SharedPayload.Image(uriStr, label)
                mime == "application/octet-stream" ->
                    SharedPayload.Audio(uriStr, label)
                else -> null
            }
        }

        val text = sharedText?.trim().orEmpty()
        if (text.isNotEmpty()) {
            val lower = text.lowercase()
            val isFb = lower.contains("facebook.com") ||
                lower.contains("fb.watch") ||
                lower.contains("fb.me") ||
                lower.contains("m.facebook.com")
            return SharedPayload.TextOrUrl(text, isFb)
        }

        return null
    }

    fun fromAndroidIntent(context: Context, intent: Intent?): SharedPayload? {
        if (intent == null) return null
        val streamUri: Uri? = if (intent.action == Intent.ACTION_VIEW) {
            intent.data
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

        val resolvedName = streamUri?.let { queryDisplayName(context, it) }
        val resolvedMime = intent.type ?: streamUri?.let { context.contentResolver.getType(it) }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)

        return parse(
            action = intent.action,
            mimeType = resolvedMime,
            streamUriString = streamUri?.toString(),
            fileNameHint = resolvedName,
            sharedText = text
        )
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull() ?: uri.lastPathSegment

    companion object {
        private val AUDIO_EXTENSIONS = setOf("opus", "ogg", "oga", "mp3", "m4a", "aac", "wav", "amr", "flac")
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "mov", "3gp", "m4v")
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "heic")
    }
}
