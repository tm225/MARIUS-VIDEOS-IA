package com.mariustia.photoanime

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var puppetView: PuppetAnimatorView
    private lateinit var poseDetector: PoseDetector
    private lateinit var bodySegmenter: BodySegmenter

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { processPhoto(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        puppetView = findViewById(R.id.puppetView)
        poseDetector = PoseDetector(this)
        bodySegmenter = BodySegmenter(this)

        findViewById<android.widget.Button>(R.id.btnPick).setOnClickListener {
            pickImage.launch("image/*")
        }
        findViewById<android.widget.Button>(R.id.btnWalk).setOnClickListener {
            puppetView.play(ClipType.WALK)
        }
        findViewById<android.widget.Button>(R.id.btnDance).setOnClickListener {
            puppetView.play(ClipType.DANCE)
        }
        findViewById<android.widget.Button>(R.id.btnJump).setOnClickListener {
            puppetView.play(ClipType.JUMP)
        }
    }

    private fun processPhoto(uri: Uri) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val original = withContext(Dispatchers.IO) {
                    val input = contentResolver.openInputStream(uri)
                    BitmapFactory.decodeStream(input)
                }

                val (points, cutout) = withContext(Dispatchers.Default) {
                    val pts = poseDetector.detect(original)
                    val cut = bodySegmenter.cutout(original)
                    pts to cut
                }

                if (points == null) {
                    Toast.makeText(this@MainActivity, "Aucune personne détectée dans la photo", Toast.LENGTH_LONG).show()
                    return@launch
                }

                puppetView.setSource(cutout, points)
                Toast.makeText(this@MainActivity, "Corps détecté ! Choisis une animation.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        poseDetector.close()
        bodySegmenter.close()
    }
}
