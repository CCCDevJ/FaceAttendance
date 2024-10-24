package com.devjethava.facialattendance

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
import com.devjethava.facialattendance.database.entity.Attendance
import com.devjethava.facialattendance.database.entity.Employee
import com.devjethava.facialattendance.databinding.ActivityAttendanceBinding
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

class AttendanceActivity : AppCompatActivity() {

    private val TAG = AttendanceActivity::class.java.name

    // Debug mode flag
    private val SIMILARITY_THRESHOLD = 0.75f

    private val MODEL_PATH = "face_detection_short_range.tflite"

    private lateinit var binding: ActivityAttendanceBinding
    private lateinit var database: AppDatabase

    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var faceDetector: FaceDetector

    private val CAMERA_PERMISSION_REQUEST = 100
    private var capturedFace: Detection? = null
    private var capturedImage: Bitmap? = null

    private var flagAttendance = -1


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAttendanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        database = AppDatabase.getDatabase(this)
        flagAttendance = -1

        initProcess()

    }

    override fun onRestart() {
        super.onRestart()
        initProcess()
    }

    private fun initProcess() {
        if (checkCameraPermission()) {
            initializeCamera()
        } else {
            requestCameraPermission()
        }

        copyModelToLocalFile()
        initializeFaceDetector()
    }

    private fun copyModelToLocalFile() {
        try {
            val modelPath = File(filesDir, MODEL_PATH)
            if (!modelPath.exists()) {
                assets.open(MODEL_PATH).use { input ->
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
            val modelPath = File(filesDir, MODEL_PATH).absolutePath
            val baseOptions = BaseOptions.builder().setModelAssetPath(modelPath).build()

            val options = FaceDetector.FaceDetectorOptions.builder().setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE).build()

            faceDetector = FaceDetector.createFromOptions(this, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize face detector: ${e.message}")
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
        setupMediaPipe()
        setupCamera()
    }

    private fun setupMediaPipe() {
        try {
            assets.open(MODEL_PATH).use {
                Log.d(TAG, "Model file found in assets")
            }

            val baseOptions = BaseOptions.builder().setModelAssetPath(MODEL_PATH).build()

            val options = FaceDetector.FaceDetectorOptions.builder().setBaseOptions(baseOptions)
                .setMinDetectionConfidence(0.5f).setRunningMode(RunningMode.IMAGE).build()

            faceDetector = FaceDetector.createFromOptions(this, options)
            Log.d(TAG, "MediaPipe Face Detector initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up MediaPipe: ${e.message}")
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
            cameraProvider.unbindAll()

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)

            val imageAnalysis =
                ImageAnalysis.Builder().setTargetRotation(binding.viewFinder.display.rotation)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()

            imageAnalysis.setAnalyzer(
                ContextCompat.getMainExecutor(this)
            ) { imageProxy ->
                processImage(imageProxy)
            }

            val cameraSelector =
                CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()

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
        if (!::faceDetector.isInitialized) {
            Log.e(TAG, "Face detector not initialized")
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            try {
                val bitmap = imageProxy.toBitmap()
                val mpImage = BitmapImageBuilder(bitmap).apply {
                    requestedOrientation = imageProxy.imageInfo.rotationDegrees
                }.build()

                val detectionResult = faceDetector.detect(mpImage)

                if (detectionResult.detections().isNotEmpty()) {
                    capturedFace = detectionResult.detections()[0]
                    capturedImage = bitmap
                    runOnUiThread {
                        markAttendance()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image: ${e.message}")
            }
        }
        imageProxy.close()
    }

    private fun getFaceEmbedding(bitmap: Bitmap): FloatArray? {
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = faceDetector.detect(mpImage)

            if (result.detections().isEmpty()) {
                throw Exception("No face detected during embedding generation")
            }

            val detection = result.detections()[0]
            val embeddings = detection.keypoints().get()
            if (embeddings.isNotEmpty()) {
                val faceEmbedding = FloatArray(embeddings.size * 2)
                for (i in embeddings.indices) {
                    val keypoint = embeddings[i]
                    faceEmbedding[i * 2] = keypoint.x()
                    faceEmbedding[i * 2 + 1] = keypoint.y()
                }

                val norm = sqrt(faceEmbedding.sumOf { it * it.toDouble() }).toFloat()
                return faceEmbedding.map { it / norm }.toFloatArray()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting face embedding: ${e.message}")
        }
        return null
    }

    private fun markAttendance() {
        lifecycleScope.launch {
            try {
                val faceEmbedding = getFaceEmbedding(capturedImage!!)
                if (faceEmbedding != null) {
                    val employee = findMatchingEmployee(faceEmbedding)
                    if (employee != null) {
                        logAttendance(employee)
                        finish()
                    } else {
                        Log.e(TAG, "No matching employee found")
                    }
                } else {
                    Log.e(TAG, "Failed to process face embedding")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marking attendance: ${e.message}")
            }
        }
    }

    private suspend fun findMatchingEmployee(faceEmbedding: FloatArray): Employee? {
        val employees = database.employeeDao().getAllEmployees()
        return employees.find { employee ->
            val similarity = cosineSimilarity(faceEmbedding, employee.getEmbeddingArray()!!)
            similarity > SIMILARITY_THRESHOLD // Similarity threshold for matching
        }
    }

    private fun cosineSimilarity(x1: FloatArray, x2: FloatArray): Float {
        val mag1 = sqrt(x1.map { it * it }.sum())
        val mag2 = sqrt(x2.map { it * it }.sum())
        val dot = x1.mapIndexed { i, xi -> xi * x2[i] }.sum()
        return dot / (mag1 * mag2)
    }

    private fun logAttendance(employee: Employee) {
        val attendance = Attendance(
            id = 0,
            employeeId = employee.id,
            timestamp = System.currentTimeMillis(),
            type = "Attendance"
        )
        lifecycleScope.launch {
            flagAttendance++
            if (flagAttendance == 0) {
                database.attendanceDao().insertAttendance(attendance)
                Toast.makeText(
                    this@AttendanceActivity,
                    ("${employee.name} marked present!"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    }

    private fun showError(message: String) {
        AlertDialog.Builder(this).setTitle("Error").setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }.show()
    }

    private fun showSuccess(message: String) {
        AlertDialog.Builder(this).setTitle("Success").setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }.show()
    }
}