package com.mariustia.photoanime

import android.graphics.PointF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

enum class ClipType { WALK, DANCE, JUMP }

/**
 * Génère, pour un instant t (0..1 = un cycle complet), les décalages
 * d'angle de chaque os (radians) et le déplacement de la racine (hanches).
 * Ordre des os = celui défini dans PuppetRig.boneDefs :
 * 0 épaule-coude G, 1 coude-poignet G, 2 épaule-coude D, 3 coude-poignet D,
 * 4 hanche-genou G, 5 genou-cheville G, 6 hanche-genou D, 7 genou-cheville D,
 * 8 épaule-hanche G, 9 épaule-hanche D
 */
object AnimationClips {

    fun sample(type: ClipType, t: Float): Pair<FloatArray, PointF> {
        val angles = FloatArray(10)
        val root = PointF(0f, 0f)
        val phase = t * 2f * PI.toFloat()

        when (type) {
            ClipType.WALK -> {
                val swing = 0.5f
                // Jambes en opposition de phase
                angles[4] = swing * sin(phase)          // cuisse gauche
                angles[6] = swing * sin(phase + PI.toFloat()) // cuisse droite
                angles[5] = 0.4f * abs(sin(phase)) * -1f      // genou gauche plie à l'appui
                angles[7] = 0.4f * abs(sin(phase + PI.toFloat())) * -1f
                // Bras en opposition avec les jambes opposées
                angles[0] = -swing * 0.6f * sin(phase + PI.toFloat())
                angles[2] = -swing * 0.6f * sin(phase)
                angles[1] = 0.2f
                angles[3] = 0.2f
                // Léger rebond vertical + avancée horizontale visuelle (translation légère, boucle sur place)
                root.y = -6f * abs(sin(phase))
            }
            ClipType.DANCE -> {
                angles[0] = 0.7f * sin(phase)
                angles[2] = 0.7f * sin(phase + PI.toFloat())
                angles[1] = 0.4f * sin(phase * 2f)
                angles[3] = 0.4f * sin(phase * 2f + PI.toFloat())
                angles[4] = 0.25f * sin(phase * 2f)
                angles[6] = 0.25f * sin(phase * 2f + PI.toFloat())
                angles[8] = 0.15f * sin(phase)
                angles[9] = 0.15f * sin(phase)
                root.x = 10f * sin(phase)
                root.y = -8f * abs(sin(phase * 2f))
            }
            ClipType.JUMP -> {
                // Un seul cycle: accroupi -> extension -> vol -> réception
                val crouch = if (t < 0.2f) t / 0.2f else if (t < 0.4f) 1f - (t - 0.2f) / 0.2f else 0f
                angles[4] = 0.5f * crouch
                angles[6] = 0.5f * crouch
                angles[5] = -0.8f * crouch
                angles[7] = -0.8f * crouch
                angles[0] = -0.6f * crouch
                angles[2] = -0.6f * crouch
                val airTime = ((t - 0.4f) / 0.6f).coerceIn(0f, 1f)
                root.y = -120f * sin(airTime * PI.toFloat())
            }
        }
        return angles to root
    }

    fun durationMs(type: ClipType): Long = when (type) {
        ClipType.WALK -> 900L
        ClipType.DANCE -> 1200L
        ClipType.JUMP -> 1400L
    }
}
