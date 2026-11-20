package com.fakeaway.app.features.share_router

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseShareIntentUseCaseTest {

    private val useCase = ParseShareIntentUseCase()

    @Test
    fun parse_routesWhatsAppOpusTelegramAudioVideoImageAndFacebookLink() {
        val whatsappVoice = useCase.parse(
            action = Intent.ACTION_SEND,
            mimeType = "audio/ogg; codecs=opus",
            streamUriString = "content://com.whatsapp.provider/media/PTT-20261119-WA0001.opus",
            fileNameHint = "PTT-20261119-WA0001.opus",
            sharedText = null
        )
        assertTrue(whatsappVoice is SharedPayload.Audio)
        assertEquals("PTT-20261119-WA0001.opus", (whatsappVoice as SharedPayload.Audio).displayName)

        val telegramOctetAudio = useCase.parse(
            action = Intent.ACTION_SEND,
            mimeType = "application/octet-stream",
            streamUriString = "content://org.telegram.messenger/cache/voice_note.m4a",
            fileNameHint = "voice_note.m4a",
            sharedText = null
        )
        assertTrue(telegramOctetAudio is SharedPayload.Audio)

        val videoShare = useCase.parse(
            action = Intent.ACTION_SEND,
            mimeType = "video/mp4",
            streamUriString = "content://media/external/video/clip.mp4",
            fileNameHint = "clip.mp4",
            sharedText = null
        )
        assertTrue(videoShare is SharedPayload.Video)

        val imageShare = useCase.parse(
            action = Intent.ACTION_SEND,
            mimeType = "image/png",
            streamUriString = "content://media/external/images/post.png",
            fileNameHint = "post.png",
            sharedText = null
        )
        assertTrue(imageShare is SharedPayload.Image)

        val fbPost = useCase.parse(
            action = Intent.ACTION_SEND,
            mimeType = "text/plain",
            streamUriString = null,
            fileNameHint = null,
            sharedText = "https://www.facebook.com/watch/?v=123456789"
        )
        assertTrue(fbPost is SharedPayload.TextOrUrl)
        assertTrue((fbPost as SharedPayload.TextOrUrl).isFacebookLink)

        val plainText = useCase.parse(
            action = Intent.ACTION_SEND,
            mimeType = "text/plain",
            streamUriString = null,
            fileNameHint = null,
            sharedText = "Unverified WhatsApp rumor text"
        )
        assertFalse((plainText as SharedPayload.TextOrUrl).isFacebookLink)
    }
}
