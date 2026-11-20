package com.fakeaway.app.core.onnx

/**
 * Specifications for optional on-device quantized ONNX neural models.
 */
enum class ModelSpec(
    val id: String,
    val fileName: String,
    val downloadUrl: String,
    val expectedSizeMb: Float
) {
    VOICE_WAV2VEC2(
        id = "voice_wav2vec2",
        fileName = "voice_deepfake_wav2vec2.onnx",
        downloadUrl = "https://huggingface.co/ai8shiro/deepfake-audio-wav2vec2-ONNX/resolve/main/onnx/model_q4f16.onnx",
        expectedSizeMb = 63.4f
    ),
    IMAGE_SMOGY(
        id = "image_smogy",
        fileName = "image_ai_smogy.onnx",
        downloadUrl = "https://huggingface.co/onnx-community/SMOGY-Ai-images-detector-ONNX/resolve/main/onnx/model_q4f16.onnx",
        expectedSizeMb = 50.1f
    )
}
