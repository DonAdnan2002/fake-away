package com.fakeaway.app.core.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.exp

/**
 * Thread-safe wrapper around ONNX Runtime session for single float-tensor classification models.
 */
class OnnxRuntimeSession(
    private val modelFile: File
) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setInterOpNumThreads(1)
        }
        session = env.createSession(modelFile.absolutePath, opts)
    }

    /**
     * Runs float tensor inference with [shape] and returns raw logits from output index 0.
     */
    fun runFloatInference(inputData: FloatArray, shape: LongArray): FloatArray {
        val inputName = session.inputNames.firstOrNull() ?: error("ONNX model has no inputs")
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), shape)
        tensor.use { inputTensor ->
            session.run(mapOf(inputName to inputTensor)).use { results ->
                val raw = results[0].value
                @Suppress("UNCHECKED_CAST")
                return when (raw) {
                    is Array<*> -> (raw[0] as? FloatArray) ?: error("Unexpected 2D tensor output")
                    is FloatArray -> raw
                    else -> error("Unsupported ONNX output type: ${raw?.javaClass?.simpleName}")
                }
            }
        }
    }

    override fun close() {
        runCatching { session.close() }
    }

    companion object {
        /**
         * Numerically stable softmax probability for [targetIndex] within [logits].
         */
        fun softmaxProbability(logits: FloatArray, targetIndex: Int): Float {
            if (logits.isEmpty() || targetIndex !in logits.indices) return 0.5f
            val maxLogit = logits.max()
            var sumExp = 0.0
            val exps = DoubleArray(logits.size) { i ->
                val e = exp((logits[i] - maxLogit).toDouble())
                sumExp += e
                e
            }
            return if (sumExp > 0.0) (exps[targetIndex] / sumExp).toFloat() else 0.5f
        }
    }
}
