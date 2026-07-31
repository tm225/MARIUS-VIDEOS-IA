package com.mariustia.photoanime

import android.graphics.PointF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Un os = deux points reliés, avec sa longueur d'origine (rigide). */
data class Bone(val startIdx: Int, val endIdx: Int, val length: Float, val baseAngle: Float)

/**
 * Construit un squelette articulé rigide à partir des 33 points MediaPipe
 * détectés sur la photo statique. Les longueurs des os sont figées (mesurées
 * une fois), seuls les ANGLES changent pendant l'animation -> c'est ce qui
 * permet de faire "marcher" une photo fixe de façon crédible.
 */
class PuppetRig(private val basePoints: List<PointF>) {

    private val boneDefs = listOf(
        PoseDetector.LEFT_SHOULDER to PoseDetector.LEFT_ELBOW,
        PoseDetector.LEFT_ELBOW to PoseDetector.LEFT_WRIST,
        PoseDetector.RIGHT_SHOULDER to PoseDetector.RIGHT_ELBOW,
        PoseDetector.RIGHT_ELBOW to PoseDetector.RIGHT_WRIST,
        PoseDetector.LEFT_HIP to PoseDetector.LEFT_KNEE,
        PoseDetector.LEFT_KNEE to PoseDetector.LEFT_ANKLE,
        PoseDetector.RIGHT_HIP to PoseDetector.RIGHT_KNEE,
        PoseDetector.RIGHT_KNEE to PoseDetector.RIGHT_ANKLE,
        PoseDetector.LEFT_SHOULDER to PoseDetector.LEFT_HIP,
        PoseDetector.RIGHT_SHOULDER to PoseDetector.RIGHT_HIP
    )

    val bones: List<Bone> = boneDefs.map { (a, b) ->
        val p1 = basePoints[a]
        val p2 = basePoints[b]
        val len = hypot((p2.x - p1.x).toDouble(), (p2.y - p1.y).toDouble()).toFloat()
        val angle = atan2((p2.y - p1.y), (p2.x - p1.x))
        Bone(a, b, len, angle)
    }

    /**
     * Applique des décalages d'angle (radians) par os, indexés dans le même
     * ordre que [bones], et renvoie la nouvelle position de chaque point
     * (forward kinematics simple, en partant des hanches comme racine).
     */
    fun pose(angleDeltas: FloatArray, rootOffset: PointF): Map<Int, PointF> {
        val result = HashMap<Int, PointF>()
        // Racine : milieu des hanches, translatée pour marche/saut
        val hipCenter = PointF(
            (basePoints[PoseDetector.LEFT_HIP].x + basePoints[PoseDetector.RIGHT_HIP].x) / 2f + rootOffset.x,
            (basePoints[PoseDetector.LEFT_HIP].y + basePoints[PoseDetector.RIGHT_HIP].y) / 2f + rootOffset.y
        )
        result[PoseDetector.LEFT_HIP] = PointF(
            basePoints[PoseDetector.LEFT_HIP].x + rootOffset.x,
            basePoints[PoseDetector.LEFT_HIP].y + rootOffset.y
        )
        result[PoseDetector.RIGHT_HIP] = PointF(
            basePoints[PoseDetector.RIGHT_HIP].x + rootOffset.x,
            basePoints[PoseDetector.RIGHT_HIP].y + rootOffset.y
        )
        result[PoseDetector.LEFT_SHOULDER] = PointF(
            basePoints[PoseDetector.LEFT_SHOULDER].x + rootOffset.x,
            basePoints[PoseDetector.LEFT_SHOULDER].y + rootOffset.y
        )
        result[PoseDetector.RIGHT_SHOULDER] = PointF(
            basePoints[PoseDetector.RIGHT_SHOULDER].x + rootOffset.x,
            basePoints[PoseDetector.RIGHT_SHOULDER].y + rootOffset.y
        )
        result[PoseDetector.NOSE] = PointF(
            basePoints[PoseDetector.NOSE].x + rootOffset.x,
            basePoints[PoseDetector.NOSE].y + rootOffset.y
        )

        bones.forEachIndexed { i, bone ->
            val start = result[bone.startIdx] ?: PointF(
                basePoints[bone.startIdx].x + rootOffset.x,
                basePoints[bone.startIdx].y + rootOffset.y
            )
            val newAngle = bone.baseAngle + angleDeltas[i]
            val end = PointF(
                start.x + bone.length * cos(newAngle),
                start.y + bone.length * sin(newAngle)
            )
            result[bone.startIdx] = start
            result[bone.endIdx] = end
        }
        return result
    }

    fun boneIndexFor(startIdx: Int, endIdx: Int): Int =
        boneDefs.indexOfFirst { it.first == startIdx && it.second == endIdx }
}
