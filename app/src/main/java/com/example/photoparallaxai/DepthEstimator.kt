package com.example.photoparallaxai

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Estime une carte de profondeur à partir d'une photo, entièrement sur l'appareil
 * (aucun appel réseau) grâce à un modèle MiDaS v2 small quantifié en TFLite.
 *
 * IMPORTANT : le fichier modèle n'est PAS inclus dans ce dépôt (voir README.md
 * pour le télécharger et le placer dans app/src/main/assets/midas_small.tflite).
 */
class DepthEstimator(context: Context) {

    private var interpreter: Interpreter
    private var gpuDelegate: GpuDelegate? = null

    // Le modèle MiDaS v2 small attend des entrées 256x256
    private val inputSize = 256

    init {
        val model = loadModelFile(context, "midas_small.tflite")
        val options = Interpreter.Options()

        // Utilise le GPU si le device le supporte, sinon repli CPU (toujours local)
        val compatList = CompatibilityList()
        if (compatList.isDelegateSupportedOnThisDevice) {
            gpuDelegate = GpuDelegate(compatList.bestOptionsForThisDevice)
            options.addDelegate(gpuDelegate)
        } else {
            options.setNumThreads(4)
        }

        interpreter = Interpreter(model, options)
    }

    private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
        val fd = context.assets.openFd(filename)
        val inputStream = FileInputStream(fd.fileDescriptor)
        val channel = inputStream.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    /**
     * Retourne une carte de profondeur normalisée [0f..1f] (1f = très proche, 0f = lointain),
     * redimensionnée à la taille de l'image d'origine.
     */
    fun estimateDepth(original: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(original, inputSize, inputSize, true)
        val inputBuffer = bitmapToByteBuffer(resized)

        // Sortie MiDaS small : [1, 256, 256]
        val output = Array(1) { Array(inputSize) { FloatArray(inputSize) } }
        interpreter.run(inputBuffer, output)

        val raw = output[0]
        var min = Float.MAX_VALUE
        var max = Float.MIN_VALUE
        for (row in raw) for (v in row) {
            if (v < min) min = v
            if (v > max) max = v
        }
        val range = (max - min).takeIf { it > 1e-6f } ?: 1f

        // Normalise et remet à la taille d'origine (interpolation bilinéaire simple)
        val normalized = FloatArray(inputSize * inputSize)
        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                normalized[y * inputSize + x] = (raw[y][x] - min) / range
            }
        }
        return upscaleDepthMap(normalized, inputSize, inputSize, original.width, original.height)
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (pixel in pixels) {
            // Normalisation ImageNet standard attendue par MiDaS
            buffer.putFloat((((pixel shr 16) and 0xFF) / 255f - 0.485f) / 0.229f)
            buffer.putFloat((((pixel shr 8) and 0xFF) / 255f - 0.456f) / 0.224f)
            buffer.putFloat(((pixel and 0xFF) / 255f - 0.406f) / 0.225f)
        }
        buffer.rewind()
        return buffer
    }

    private fun upscaleDepthMap(
        src: FloatArray, srcW: Int, srcH: Int, dstW: Int, dstH: Int
    ): FloatArray {
        val dst = FloatArray(dstW * dstH)
        for (y in 0 until dstH) {
            val sy = (y.toFloat() / dstH * srcH).toInt().coerceIn(0, srcH - 1)
            for (x in 0 until dstW) {
                val sx = (x.toFloat() / dstW * srcW).toInt().coerceIn(0, srcW - 1)
                dst[y * dstW + x] = src[sy * srcW + sx]
            }
        }
        return dst
    }

    fun close() {
        interpreter.close()
        gpuDelegate?.close()
    }
}
