package com.fakeaway.app.e2e

import com.fakeaway.app.core.domain.DispatcherProvider
import com.fakeaway.app.features.history.domain.ScanHistoryItem
import com.fakeaway.app.features.history.domain.ScanHistoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TestDispatcherProvider(
    private val dispatcher: CoroutineDispatcher
) : DispatcherProvider {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}

class InMemoryScanHistoryRepository : ScanHistoryRepository {
    private val items = MutableStateFlow<List<ScanHistoryItem>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<ScanHistoryItem>> = items.asStateFlow()

    override suspend fun save(item: ScanHistoryItem): Long {
        val assigned = item.copy(id = nextId++)
        items.value = listOf(assigned) + items.value
        return assigned.id
    }

    override suspend fun delete(id: Long) {
        items.value = items.value.filterNot { it.id == id }
    }

    override suspend fun clearAll() {
        items.value = emptyList()
    }

    fun currentItems(): List<ScanHistoryItem> = items.value
}

fun encodePcm16Wav(samples: FloatArray, sampleRate: Int = 16_000): ByteArray {
    val dataBytes = samples.size * 2
    val out = ByteArrayOutputStream(44 + dataBytes)
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    header.put("RIFF".toByteArray(Charsets.US_ASCII))
    header.putInt(36 + dataBytes)
    header.put("WAVE".toByteArray(Charsets.US_ASCII))
    header.put("fmt ".toByteArray(Charsets.US_ASCII))
    header.putInt(16)
    header.putShort(1) // PCM
    header.putShort(1) // Mono
    header.putInt(sampleRate)
    header.putInt(sampleRate * 2)
    header.putShort(2)
    header.putShort(16)
    header.put("data".toByteArray(Charsets.US_ASCII))
    header.putInt(dataBytes)
    out.write(header.array())

    val pcmBuf = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
    for (s in samples) {
        val clamped = (s.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        pcmBuf.putShort(clamped)
    }
    out.write(pcmBuf.array())
    return out.toByteArray()
}
