package com.mariustia.photoanime

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Affiche la photo découpée et la déforme en temps réel selon le squelette
 * animé, en utilisant une grille de déformation (mesh warp) pilotée par les
 * positions d'articulations. Approche légère, tourne à 30+ fps sur mobile.
 */
class PuppetAnimatorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var cutoutBitmap: Bitmap? = null
    private var rig: PuppetRig? = null
    private var basePoints: List<PointF>? = null
    private var animator: ValueAnimator? = null

    private val meshCols = 12
    private val meshRows = 16
    private var verts = FloatArray((meshCols + 1) * (meshRows + 1) * 2)
    private var origVerts = FloatArray(verts.size)

    fun setSource(bitmap: Bitmap, points: List<PointF>) {
        cutoutBitmap = bitmap
        basePoints = points
        rig = PuppetRig(points)
        buildBaseMesh(bitmap)
        invalidate()
    }

    private fun buildBaseMesh(bmp: Bitmap) {
        var idx = 0
        for (row in 0..meshRows) {
            for (col in 0..meshCols) {
                val x = bmp.width * col / meshCols.toFloat()
                val y = bmp.height * row / meshRows.toFloat()
                origVerts[idx] = x; verts[idx] = x; idx++
                origVerts[idx] = y; verts[idx] = y; idx++
            }
        }
    }

    fun play(type: ClipType) {
        animator?.cancel()
        val r = rig ?: return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = AnimationClips.durationMs(type)
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                val (angles, rootOffset) = AnimationClips.sample(type, t)
                val posed = r.pose(angles, rootOffset)
                warpMesh(posed)
                invalidate()
            }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        verts = origVerts.copyOf()
        invalidate()
    }

    /**
     * Déplace chaque sommet du maillage en fonction de sa distance pondérée
     * aux articulations posées (skinning simple par distance inverse).
     */
    private fun warpMesh(posed: Map<Int, PointF>) {
        val base = basePoints ?: return
        val jointIdx = posed.keys.toList()
        var vi = 0
        for (row in 0..meshRows) {
            for (col in 0..meshCols) {
                val ox = origVerts[vi]
                val oy = origVerts[vi + 1]
                var sumW = 0f
                var dx = 0f
                var dy = 0f
                for (j in jointIdx) {
                    val bp = base[j] ?: continue
                    val np = posed[j] ?: continue
                    val d = Math.hypot((ox - bp.x).toDouble(), (oy - bp.y).toDouble()).toFloat() + 1f
                    val w = 1f / (d * d)
                    sumW += w
                    dx += (np.x - bp.x) * w
                    dy += (np.y - bp.y) * w
                }
                if (sumW > 0f) {
                    verts[vi] = ox + dx / sumW
                    verts[vi + 1] = oy + dy / sumW
                }
                vi += 2
            }
        }
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = cutoutBitmap ?: return
        val scale = minOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        val offsetX = (width - bmp.width * scale) / 2f
        val offsetY = (height - bmp.height * scale) / 2f

        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        canvas.drawBitmapMesh(bmp, meshCols, meshRows, verts, 0, null, 0, null)
        canvas.restore()
    }
}
