package com.mariustia.photoanime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import java.nio.FloatBuffer

/**
 * Découpe la silhouette de la personne (fond transparent), hors ligne.
 */
class BodySegmenter(context: Context) {
    private val segmenter: ImageSegmenter

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("selfie_segmenter.tflite")
            .build()
        val options = ImageSegmenter.ImageSegmenterOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setOutputConfidenceMasks(true)
            .build()
        segmenter = ImageSegmenter.createFromOptions(context, options)
    }

    /** Retourne un bitmap ARGB où le fond est transparent et le sujet opaque. */
    fun cutout(bitmap: Bitmap): Bitmap {
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result = segmenter.segment(mpImage)

        val maskImage = result.confidenceMasks().get()[0]
        val mask: FloatBuffer = ByteBufferExtractor.extract(maskImage).asFloatBuffer()

        val w = bitmap.width
        val h = bitmap.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val srcPixels = IntArray(w * h)
        bitmap.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val dstPixels = IntArray(w * h)

        mask.rewind()
        for (i in 0 until w * h) {
            val confidence = mask.get(i)
            dstPixels[i] = if (confidence > 0.5f) {
                srcPixels[i]
            } else {
                Color.TRANSPARENT
            }
        }
        output.setPixels(dstPixels, 0, w, 0, 0, w, h)
        return output
    }

    fun close() = segmenter.close()
}
