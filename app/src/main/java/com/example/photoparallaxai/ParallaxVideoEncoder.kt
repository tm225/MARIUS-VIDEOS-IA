package com.example.photoparallaxai

import android.graphics.*
import android.media.*
import android.view.Surface
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * Crée une vidéo MP4 "effet parallax" à partir d'une photo + sa carte de profondeur,
 * en encodant les frames localement via MediaCodec (aucune dépendance réseau ni FFmpeg).
 *
 * Principe : les pixels proches (profondeur haute) se déplacent plus que les pixels
 * lointains quand la caméra virtuelle "orbite" légèrement autour de la scène,
 * ce qui simule un mouvement de caméra 3D à partir d'une image 2D.
 */
class ParallaxVideoEncoder(
    private val width: Int,
    private val height: Int,
    private val fps: Int = 30,
    private val durationSeconds: Int = 3
) {

    private val bitRate = 6_000_000

    fun generate(
        photo: Bitmap,
        depthMap: FloatArray,
        outputFile: File,
        maxShiftPx: Float = width * 0.04f
    ) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface: Surface = encoder.createInputSurface()
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false

        val totalFrames = fps * durationSeconds
        val bufferInfo = MediaCodec.BufferInfo()

        for (frame in 0 until totalFrames) {
            val t = frame.toFloat() / totalFrames
            // Trajectoire de caméra : léger mouvement circulaire (effet "living photo")
            val angle = t * 2f * Math.PI.toFloat()
            val camX = cos(angle) * maxShiftPx
            val camY = sin(angle) * maxShiftPx * 0.5f

            val canvas = inputSurface.lockHardwareCanvas()
            try {
                renderParallaxFrame(canvas, photo, depthMap, camX, camY)
            } finally {
                inputSurface.unlockCanvasAndPost(canvas)
            }

            drainEncoder(encoder, muxer, bufferInfo) { index, format2 ->
                trackIndex = muxer.addTrack(format2)
                muxer.start()
                muxerStarted = true
            }
        }

        encoder.signalEndOfInputStream()
        drainEncoder(encoder, muxer, bufferInfo, endOfStream = true) { index, format2 ->
            if (!muxerStarted) {
                trackIndex = muxer.addTrack(format2)
                muxer.start()
                muxerStarted = true
            }
        }

        encoder.stop()
        encoder.release()
        if (muxerStarted) {
            muxer.stop()
        }
        muxer.release()
        inputSurface.release()
    }

    /**
     * Rend une frame en découpant l'image en bandes de profondeur (layers) et en
     * appliquant à chacune un décalage proportionnel à sa profondeur moyenne.
     * Approche simple et rapide adaptée à un prototype (des artefacts de bord
     * légers sont normaux sur les zones à forte discontinuité de profondeur).
     */
    private fun renderParallaxFrame(
        canvas: Canvas,
        photo: Bitmap,
        depthMap: FloatArray,
        camX: Float,
        camY: Float
    ) {
        canvas.drawColor(Color.BLACK)
        val numLayers = 6
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        for (layer in 0 until numLayers) {
            val depthLo = layer.toFloat() / numLayers
            val depthHi = (layer + 1).toFloat() / numLayers
            val layerDepth = (depthLo + depthHi) / 2f // 0 = loin, 1 = proche

            // Les objets proches bougent plus que l'arrière-plan (effet parallax classique)
            val shiftX = camX * layerDepth
            val shiftY = camY * layerDepth
            // Léger zoom supplémentaire sur le premier plan pour renforcer l'illusion 3D
            val scale = 1f + 0.015f * layerDepth

            val layerBitmap = extractLayer(photo, depthMap, depthLo, depthHi)

            canvas.save()
            canvas.translate(width / 2f, height / 2f)
            canvas.scale(scale, scale)
            canvas.translate(-width / 2f + shiftX, -height / 2f + shiftY)
            canvas.drawBitmap(layerBitmap, 0f, 0f, paint)
            canvas.restore()
        }
    }

    /** Extrait les pixels dont la profondeur tombe dans [depthLo, depthHi], le reste transparent. */
    private fun extractLayer(photo: Bitmap, depthMap: FloatArray, depthLo: Float, depthHi: Float): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val srcPixels = IntArray(width * height)
        photo.getPixels(srcPixels, 0, width, 0, 0, width, height)
        val dstPixels = IntArray(width * height)

        for (i in srcPixels.indices) {
            val d = depthMap[i]
            dstPixels[i] = if (d in depthLo..depthHi) srcPixels[i] else Color.TRANSPARENT
        }
        out.setPixels(dstPixels, 0, width, 0, 0, width, height)
        return out
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean = false,
        onFormatChanged: (Int, MediaFormat) -> Unit
    ) {
        val timeoutUs = 10_000L
        while (true) {
            val outIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return else continue
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    onFormatChanged(0, encoder.outputFormat)
                }
                outIndex >= 0 -> {
                    val encodedData = encoder.getOutputBuffer(outIndex)
                        ?: throw RuntimeException("buffer encodeur nul")
                    if (bufferInfo.size > 0) {
                        muxer.writeSampleData(0, encodedData, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }
}
