package com.example.photoparallaxai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var depthEstimator: DepthEstimator
    private var selectedBitmap: Bitmap? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { loadAndShow(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        depthEstimator = DepthEstimator(this)

        findViewById<android.widget.Button>(R.id.btnPick).setOnClickListener {
            pickImage.launch("image/*")
        }

        findViewById<android.widget.Button>(R.id.btnGenerate).setOnClickListener {
            selectedBitmap?.let { generateVideo(it) }
                ?: Toast.makeText(this, "Choisis d'abord une photo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadAndShow(uri: Uri) {
        val input = contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(input)
        // Redimensionne pour rester raisonnable en mémoire/temps de calcul sur mobile
        val maxDim = 720
        val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        selectedBitmap = resized
        findViewById<android.widget.ImageView>(R.id.imagePreview).setImageBitmap(resized)
    }

    private fun generateVideo(bitmap: Bitmap) {
        val progress = findViewById<android.widget.ProgressBar>(R.id.progressBar)
        progress.visibility = android.view.View.VISIBLE

        lifecycleScope.launch {
            try {
                val outputFile = withContext(Dispatchers.Default) {
                    // 1. Estimation de profondeur (IA locale, TFLite)
                    val depth = depthEstimator.estimateDepth(bitmap)

                    // 2. Génération de la vidéo parallax + encodage MP4 local
                    val encoder = ParallaxVideoEncoder(bitmap.width, bitmap.height)
                    val file = File(getExternalFilesDir(null), "parallax_${System.currentTimeMillis()}.mp4")
                    encoder.generate(bitmap, depth, file)
                    file
                }
                progress.visibility = android.view.View.GONE
                Toast.makeText(
                    this@MainActivity,
                    "Vidéo générée : ${outputFile.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                progress.visibility = android.view.View.GONE
                Toast.makeText(this@MainActivity, "Erreur : ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        depthEstimator.close()
    }
}
