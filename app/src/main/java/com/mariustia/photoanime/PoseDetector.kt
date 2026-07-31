package com.mariustia.photoanime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

/**
 * Détecte les 33 points du corps sur une photo, 100% hors ligne
 * (modèle .task embarqué dans les assets de l'APK).
 */
class PoseDetector(context: Context) {

    private val landmarker: PoseLandmarker

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_lite.task")
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setNumPoses(1)
            .build()

        landmarker = PoseLandmarker.createFromOptions(context, options)
    }

    /** Retourne les points clés en pixels (échelle de l'image d'entrée), ou null si personne détectée. */
    fun detect(bitmap: Bitmap): List<PointF>? {
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result = landmarker.detect(mpImage)
        if (result.landmarks().isEmpty()) return null

        val landmarks = result.landmarks()[0]
        return landmarks.map {
            PointF(it.x() * bitmap.width, it.y() * bitmap.height)
        }
    }

    fun close() = landmarker.close()

    companion object {
        // Index MediaPipe Pose (33 points) utiles pour notre squelette
        const val NOSE = 0
        const val LEFT_SHOULDER = 11
        const val RIGHT_SHOULDER = 12
        const val LEFT_ELBOW = 13
        const val RIGHT_ELBOW = 14
        const val LEFT_WRIST = 15
        const val RIGHT_WRIST = 16
        const val LEFT_HIP = 23
        const val RIGHT_HIP = 24
        const val LEFT_KNEE = 25
        const val RIGHT_KNEE = 26
        const val LEFT_ANKLE = 27
        const val RIGHT_ANKLE = 28
    }
}
