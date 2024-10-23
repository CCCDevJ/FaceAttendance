package com.devjethava.facialattendance

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.devjethava.facialattendance.database.AppDatabase
import com.devjethava.facialattendance.database.entity.Employee
import com.devjethava.facialattendance.databinding.ActivityRegistrationBinding
import com.devjethava.facialattendance.databinding.DialogRegistrationBinding
import com.google.mediapipe.framework.MediaPipeException
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Detection
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.sqrt

class RegistrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegistrationBinding
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var faceDetector: FaceDetector
    private lateinit var database: AppDatabase

    private var capturedFace: Detection? = null  // Use the correct Detection type
    private var capturedImage: Bitmap? = null

    private val CAMERA_PERMISSION_REQUEST = 100
    private val TAG = "RegistrationActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        database = AppDatabase.getDatabase(this)

        // Check camera permission first
        if (checkCameraPermission()) {
            initializeCamera()
        } else {
            requestCameraPermission()
        }

        setupUI()
        copyModelToLocalFile()
        initializeFaceDetector()
    }

    private fun copyModelToLocalFile() {
        try {
            // Copy model from assets to local storage
            val modelPath = File(filesDir, "face_detection_short_range.tflite")
            if (!modelPath.exists()) {
                assets.open("face_detection_short_range.tflite").use { input ->
                    FileOutputStream(modelPath).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error copying model file: ${e.message}")
        }
    }

    private fun initializeFaceDetector() {
        try {
            // Use local file path instead of asset path
            val modelPath = File(filesDir, "face_detection_short_range.tflite").absolutePath

            val baseOptions = BaseOptions.builder().setModelAssetPath(modelPath).build()

            val options = FaceDetector.FaceDetectorOptions.builder().setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE).build()

            faceDetector = FaceDetector.createFromOptions(this, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize face detector: ${e.message}")
            // Show error to user
            e.printStackTrace()
            Toast.makeText(this, "Failed to initialize face detection", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(android.Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeCamera()
            } else {
                showError("Camera permission is required")
                finish()
            }
        }
    }

    private fun initializeCamera() {
        verifyModel()
        setupMediaPipe()
        setupCamera()
    }

    private fun verifyModel() {
        try {
            val modelFile = "face_detection_short_range.tflite"
            val modelSize = assets.open(modelFile).use { it.available() }
            Log.d(TAG, "Model file size: $modelSize bytes")
            if (modelSize < 1000) {  // Basic size check
                Log.e(TAG, "Model file seems too small")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Model verification failed: ${e.message}")
        }
    }

    private fun setupMediaPipe() {
        try {
            // First verify the model file exists
            val modelFile = "face_detection_short_range.tflite"
            assets.open(modelFile).use {
                Log.d(TAG, "Model file found in assets")
            }

            val baseOptions =
                BaseOptions.builder().setModelAssetPath("face_detection_short_range.tflite").build()

            val options = FaceDetector.FaceDetectorOptions.builder().setBaseOptions(baseOptions)
                .setMinDetectionConfidence(0.5f).setRunningMode(RunningMode.IMAGE).build()

            faceDetector = FaceDetector.createFromOptions(this, options)
            Log.d(TAG, "MediaPipe Face Detector initialized successfully")

        } catch (e: IOException) {
            Log.e(TAG, "Error: Model file not found in assets")
            Toast.makeText(
                this, "Face detection initialization failed - Model not found", Toast.LENGTH_LONG
            ).show()
        } catch (e: MediaPipeException) {
            Log.e(TAG, "MediaPipe error: ${e.message}")
            Toast.makeText(
                this, "Face detection initialization failed - MediaPipe error", Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up MediaPipe: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Face detection initialization failed", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraPreview()
            } catch (e: Exception) {
                Log.e(TAG, "Camera setup failed: ${e.message}")
                showError("Failed to setup camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun bindCameraPreview() {
        try {
            // Unbind all previous bindings
            cameraProvider.unbindAll()

            // Preview use case
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)

            // Image analysis use case
            val imageAnalysis =
                ImageAnalysis.Builder().setTargetRotation(binding.viewFinder.display.rotation)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()

            imageAnalysis.setAnalyzer(
                ContextCompat.getMainExecutor(this)
            ) { imageProxy ->
                processImage(imageProxy)
            }

            // Select front camera
            val cameraSelector =
                CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()

            // Bind use cases to camera
            cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalysis
            )

        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed: ${e.message}")
            showError("Failed to bind camera: ${e.message}")
        }
    }

    @ExperimentalGetImage
    private fun processImage(imageProxy: ImageProxy) {
        // Check if detector is initialized
        if (!::faceDetector.isInitialized) {
            Log.e(TAG, "Face detector not initialized")
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            try {
                // Convert ImageProxy to MPImage safely
                val bitmap = imageProxy.toBitmap()
                val mpImage = BitmapImageBuilder(bitmap).apply {
                    requestedOrientation = imageProxy.imageInfo.rotationDegrees
                }.build()

                val detectionResult = faceDetector.detect(mpImage)

                if (detectionResult.detections().isNotEmpty()) {
                    capturedFace = detectionResult.detections()[0]
                    capturedImage = bitmap
                    runOnUiThread {
                        updateUI(true)
                    }
                } else {
                    runOnUiThread {
                        updateUI(false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image: ${e.message}")
                runOnUiThread {
                    updateUI(false)
                }
            }
        }
        imageProxy.close()
    }

    private fun setupUI() {
        binding.captureButton.setOnClickListener {
            if (capturedFace != null && capturedImage != null) {
                showRegistrationDialog()
            } else {
                showError("No face detected")
            }
        }
    }

    private fun getFaceEmbedding(bitmap: Bitmap): FloatArray? {
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = faceDetector.detect(mpImage)

            if (result.detections().isEmpty()) {
                throw Exception("No face detected during embedding generation")
            } else {
                // Log detection details
                val detection = result.detections()[0]
                Log.d(TAG, "Face detected with confidence: ${detection.categories()[0].score()}")

                // Get embeddings
                val embeddings = detection.keypoints().get()
                if (embeddings.isNotEmpty()) {
                    Log.d(TAG, "Embedding size: ${embeddings.size}")
                    Log.d(TAG, "First few values: ${embeddings.take(5)}")

                    val faceEmbedding = FloatArray(embeddings.size * 2)
                    for (i in 0 until embeddings.size) {
                        val keypoint = embeddings[i]
                        faceEmbedding[i * 2] = keypoint.x()
                        faceEmbedding[i * 2 + 1] = keypoint.y()
                    }

//                    [ 0.5054561, 0.4880188, 0.51261806, 0.5538109, 0.47113913, 0.49859673, 0.44775456, 0.51016617, 0.5191468, 0.5053663, 0.5240676, 0.6384204 ]

                    Log.d(TAG, "faceEmbedding: $faceEmbedding")
                    val norm = sqrt(faceEmbedding.sumOf { it * it.toDouble() }).toFloat()

//                    return FloatArray(embeddings.size * 2)
                    return faceEmbedding.map { it / norm }.toFloatArray()
                } else {
                    Log.e(TAG, "Embeddings are null")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting face embedding: ${e.message}")
            e.printStackTrace()
        }

//        val face = result.detections()[0]
//        val keypoints = face.keypoints()
//        val embedding = FloatArray(keypoints.get().size * 2)
//
//        for (i in 0 until keypoints.get().size) {
//            val keypoint = keypoints.get()[i]
//            embedding[i * 2] = keypoint.x()
//            embedding[i * 2 + 1] = keypoint.y()
//        }
//
//        val norm = sqrt(embedding.sumOf { it * it.toDouble() }).toFloat()
//        return embedding.map { it / norm }.toFloatArray()
        return null
    }

    private fun registerEmployee(name: String, id: String) {
        lifecycleScope.launch {
            try {
                val faceEmbedding = getFaceEmbedding(capturedImage!!)
//                val faceEmbedding = getFaceEmbedding()

                if (faceEmbedding == null || faceEmbedding.all { it == 0f }) {
                    Log.e(TAG, "Invalid embedding generated")
                    runOnUiThread {
                        Toast.makeText(
                            this@RegistrationActivity,
                            "Failed to process face, please try again",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }
                val employee = Employee.fromEmbedding(
                    id = id, name = name, embedding = faceEmbedding
                )

                database.employeeDao().insertEmployee(employee)
                showSuccess("Employee registered successfully")
//                finish()
            } catch (e: Exception) {
                showError("Registration failed: ${e.message}")
            }
        }
    }

    private fun showRegistrationDialog() {
        val dialogBinding = DialogRegistrationBinding.inflate(layoutInflater)

        AlertDialog.Builder(this).setTitle("Register Employee").setView(dialogBinding.root)
            .setPositiveButton("Register") { _, _ ->
                val name = dialogBinding.nameInput.text.toString()
                val id = dialogBinding.idInput.text.toString()

                if (name.isNotBlank() && id.isNotBlank()) {
                    registerEmployee(name, id)
                } else {
                    showError("Please fill all fields")
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun updateUI(faceDetected: Boolean) {
        runOnUiThread {
            binding.statusText.text = if (faceDetected) {
                "Face detected - Ready to capture"
            } else {
                "No face detected"
            }
            binding.captureButton.isEnabled = faceDetected
        }
    }

    private fun showError(message: String) {
        runOnUiThread {
            AlertDialog.Builder(this).setTitle("Error").setMessage(message)
                .setPositiveButton("OK", null).show()
        }
    }

    private fun showSuccess(message: String) {
        runOnUiThread {
            AlertDialog.Builder(this).setTitle("Success").setMessage(message)
                .setPositiveButton("OK", null).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::faceDetector.isInitialized) {
            faceDetector.close()
        }
    }
}