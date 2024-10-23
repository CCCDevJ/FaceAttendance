//package com.devjethava.facialattendance
//
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.graphics.Bitmap
//import android.graphics.Matrix
//import android.graphics.Rect
//import android.os.Bundle
//import android.util.Log
//import androidx.activity.enableEdgeToEdge
//import androidx.annotation.OptIn
//import androidx.appcompat.app.AlertDialog
//import androidx.appcompat.app.AppCompatActivity
//import androidx.camera.core.CameraSelector
//import androidx.camera.core.ExperimentalGetImage
//import androidx.camera.core.ImageAnalysis
//import androidx.camera.core.ImageProxy
//import androidx.camera.core.Preview
//import androidx.camera.lifecycle.ProcessCameraProvider
//import androidx.core.app.ActivityCompat
//import androidx.core.content.ContextCompat
//import androidx.core.view.ViewCompat
//import androidx.core.view.WindowInsetsCompat
//import androidx.lifecycle.lifecycleScope
//import com.devjethava.facialattendance.database.AppDatabase
//import com.devjethava.facialattendance.database.entity.Attendance
//import com.devjethava.facialattendance.database.entity.Employee
//import com.devjethava.facialattendance.databinding.ActivityMainBinding
//import com.devjethava.facialattendance.helper.FaceDetectionManager
//import com.google.mlkit.vision.common.InputImage
//import com.google.mlkit.vision.face.Face
//import com.google.mlkit.vision.face.FaceDetectorOptions
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import java.io.ByteArrayOutputStream
//import java.util.Calendar
//import kotlin.math.abs
//import kotlin.math.sqrt
//
//class MainActivity : AppCompatActivity() {
//
//    private lateinit var binding: ActivityMainBinding
//
//    private lateinit var cameraProvider: ProcessCameraProvider
//    private lateinit var faceDetectionManager: FaceDetectionManager
//    private lateinit var database: AppDatabase
//
//    // Track last attendance to prevent duplicate entries
//    private var lastAttendanceTime = 0L
//    private val MIN_ATTENDANCE_INTERVAL = 60000L // 1 minute
//
//    private var isProcessingFace = false
//
//    private var lastRecognizedEmployeeId: String? = null
//    private var recognitionCounter = 0
//    private val REQUIRED_RECOGNITIONS = 3  // Number of consecutive recognitions required
//
//    private val CAMERA_PERMISSION_REQUEST = 100
//    private val TAG = "MainActivity"
//
//    // Debug mode flag
//    private val DEBUG_MODE = true
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        binding = ActivityMainBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
//
//        database = AppDatabase.getDatabase(this)
//
//        setupFaceDetection()
//        setupCamera()
//
//        if (DEBUG_MODE) {
//            binding.debugText.visibility = android.view.View.VISIBLE
//        }
//    }
//
//    private fun setupCamera() {
//        // Check camera permission first
//        if (checkCameraPermission()) {
//            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
//            cameraProviderFuture.addListener({
//                try {
//                    cameraProvider = cameraProviderFuture.get()
//                    bindCameraPreview()
//                } catch (e: Exception) {
//                    Log.e(TAG, "Camera setup failed: ${e.message}")
//                    showError("Failed to setup camera: ${e.message}")
//                }
//            }, ContextCompat.getMainExecutor(this))
//        } else {
//            requestCameraPermission()
//        }
//    }
//
//    private fun checkCameraPermission(): Boolean {
//        return ContextCompat.checkSelfPermission(
//            this, android.Manifest.permission.CAMERA
//        ) == PackageManager.PERMISSION_GRANTED
//    }
//
//    private fun requestCameraPermission() {
//        ActivityCompat.requestPermissions(
//            this, arrayOf(android.Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST
//        )
//    }
//
//    @OptIn(ExperimentalGetImage::class)
//    private fun bindCameraPreview() {
//        try {
//            // Unbind all previous bindings
//            cameraProvider.unbindAll()
//
//            // Preview use case
//            val preview = Preview.Builder().build()
//            preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)
//
//            // Image analysis use case
//            val imageAnalysis =
//                ImageAnalysis.Builder().setTargetRotation(binding.viewFinder.display.rotation)
//                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
//
//            imageAnalysis.setAnalyzer(
//                ContextCompat.getMainExecutor(this)
//            ) { imageProxy ->
//                processImage(imageProxy)
//            }
//
//            // Select front camera
//            val cameraSelector =
//                CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()
//
//            // Bind use cases to camera
//            cameraProvider.bindToLifecycle(
//                this, cameraSelector, preview, imageAnalysis
//            )
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Use case binding failed: ${e.message}")
//            showError("Failed to bind camera: ${e.message}")
//        }
//    }
//
//    @OptIn(ExperimentalGetImage::class)
//    private fun processImage(imageProxy: ImageProxy) {
//        if (isProcessingFace) {
//            imageProxy.close()
//            return
//        }
//
//        val mediaImage = imageProxy.image
//        if (mediaImage != null) {
//            isProcessingFace = true
//            val image = InputImage.fromMediaImage(
//                mediaImage,
//                imageProxy.imageInfo.rotationDegrees
//            )
//
//            lifecycleScope.launch(Dispatchers.Default) {
//                try {
//                    val faces = faceDetectionManager.detectFace(image)
//                    if (faces.isNotEmpty()) {
//                        // Process the largest face in the frame
//                        val largestFace = faces.maxByOrNull { face ->
//                            face.boundingBox.width() * face.boundingBox.height()
//                        }
//
//                        if (DEBUG_MODE) {
//                            updateDebugInfo("Face detected: ${faces.size}, Processing largest face")
//                        }
//
//                        largestFace?.let {
//                            // Extract face region as bitmap
//                            val faceBitmap = extractFaceBitmap(
//                                mediaImage,
//                                it.boundingBox,
//                                imageProxy.imageInfo.rotationDegrees
//                            )
//                            processFaceDetection(it, faceBitmap)
//                        }
//                    }
//                } catch (e: Exception) {
//                    Log.e(TAG, "Face processing error: ${e.message}")
//                    if (DEBUG_MODE) {
//                        updateDebugInfo("Error: ${e.message}")
//                    }
//                } finally {
//                    isProcessingFace = false
//                    withContext(Dispatchers.Main) {
//                        imageProxy.close()
//                    }
//                }
//            }
//        } else {
//            imageProxy.close()
//        }
//    }
//
//    private fun extractFaceBitmap(
//        image: android.media.Image,
//        boundingBox: Rect,
//        rotation: Int
//    ): Bitmap {
//        val yBuffer = image.planes[0].buffer
//        val uBuffer = image.planes[1].buffer
//        val vBuffer = image.planes[2].buffer
//
//        val ySize = yBuffer.remaining()
//        val uSize = uBuffer.remaining()
//        val vSize = vBuffer.remaining()
//
//        val nv21 = ByteArray(ySize + uSize + vSize)
//
//        yBuffer.get(nv21, 0, ySize)
//        vBuffer.get(nv21, ySize, vSize)
//        uBuffer.get(nv21, ySize + vSize, uSize)
//
//        val yuvImage = android.graphics.YuvImage(
//            nv21,
//            android.graphics.ImageFormat.NV21,
//            image.width,
//            image.height,
//            null
//        )
//        val out = ByteArrayOutputStream()
//        yuvImage.compressToJpeg(boundingBox, 100, out)
//        val imageBytes = out.toByteArray()
//        var faceBitmap =
//            android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
//
//        // Rotate bitmap if needed
//        if (rotation != 0) {
//            val matrix = Matrix()
//            matrix.postRotate(rotation.toFloat())
//            faceBitmap = Bitmap.createBitmap(
//                faceBitmap,
//                0,
//                0,
//                faceBitmap.width,
//                faceBitmap.height,
//                matrix,
//                true
//            )
//        }
//
//        return faceBitmap
//    }
//
//    private suspend fun processFaceDetection(face: Face, faceBitmap: Bitmap) {
//        if (System.currentTimeMillis() - lastAttendanceTime < MIN_ATTENDANCE_INTERVAL) {
//            return
//        }
//
//        val faceFeatures = extractFaceFeatures(face, faceBitmap)
//        val matchingEmployee = findMatchingEmployee(faceFeatures)
//
//        if (matchingEmployee != null) {
//            if (DEBUG_MODE) {
//                updateDebugInfo("Match found: ${matchingEmployee.name}")
//            }
//
//            if (matchingEmployee.id == lastRecognizedEmployeeId) {
//                recognitionCounter++
//                if (recognitionCounter >= REQUIRED_RECOGNITIONS) {
//                    markAttendance(matchingEmployee)
//                    resetRecognitionTracking()
//                } else {
//                    if (DEBUG_MODE) {
//                        updateDebugInfo("Recognition counter: $recognitionCounter/${REQUIRED_RECOGNITIONS}")
//                    }
//                }
//            } else {
//                resetRecognitionTracking()
//                lastRecognizedEmployeeId = matchingEmployee.id
//                recognitionCounter = 1
//            }
//        } else {
//            if (DEBUG_MODE) {
//                updateDebugInfo("No match found")
//            }
//            resetRecognitionTracking()
//        }
//    }
//
//    private fun extractFaceFeatures(face: Face, faceBitmap: Bitmap): FloatArray {
//        val features = mutableListOf<Float>()
//
//        // Normalize face landmarks
//        face.allLandmarks.forEach { landmark ->
//            features.add(landmark.position.x / faceBitmap.width)
//            features.add(landmark.position.y / faceBitmap.height)
//        }
//
//        // Add face contours if available
//        face.allContours.forEach { contour ->
//            contour.points.forEach { point ->
//                features.add(point.x / faceBitmap.width)
//                features.add(point.y / faceBitmap.height)
//            }
//        }
//
//        // Add face angles
//        features.add(face.headEulerAngleX ?: 0f)
//        features.add(face.headEulerAngleY ?: 0f)
//        features.add(face.headEulerAngleZ ?: 0f)
//
//        // Add smile probability and eye open probabilities
//        features.add(face.smilingProbability ?: 0f)
//        features.add(face.leftEyeOpenProbability ?: 0f)
//        features.add(face.rightEyeOpenProbability ?: 0f)
//
//        return features.toFloatArray()
//    }
//
//    private fun resetRecognitionTracking() {
//        lastRecognizedEmployeeId = null
//        recognitionCounter = 0
//    }
//
//    private suspend fun findMatchingEmployee(faceFeatures: FloatArray): Employee? =
//        withContext(Dispatchers.IO) {
//            val employees = database.employeeDao().getAllEmployees()
//
//            var bestMatch: Employee? = null
//            var highestSimilarity = SIMILARITY_THRESHOLD
//
//            for (employee in employees) {
//                val storedFeatures = employee.getEmbeddingArray()
//                if (storedFeatures != null) {
//                    // change to Cosine as well for check
//                    val similarity = calculateSimilarity(faceFeatures, storedFeatures)
//                    if (similarity > highestSimilarity) {
//                        highestSimilarity = similarity
//                        bestMatch = employee
//                    }
//                }
//            }
//
//            bestMatch
//        }
//
//    private suspend fun markAttendance(employee: Employee) {
//        val currentTime = System.currentTimeMillis()
//        if (currentTime - lastAttendanceTime < MIN_ATTENDANCE_INTERVAL) {
//            return
//        }
//
//        val attendanceType = determineAttendanceType()
//        val attendance = Attendance(
//            employeeId = employee.id,
//            timestamp = currentTime,
//            type = attendanceType
//        )
//
//        withContext(Dispatchers.IO) {
//            database.attendanceDao().insertAttendance(attendance)
//        }
//
//        lastAttendanceTime = currentTime
//
//        withContext(Dispatchers.Main) {
//            showSuccess("Attendance marked for ${employee.name}\nType: $attendanceType")
//        }
//    }
//
//    private fun updateDebugInfo(message: String) {
//        if (DEBUG_MODE) {
//            lifecycleScope.launch(Dispatchers.Main) {
//                binding.debugText.text = message
//            }
//        }
//    }
//
//    private fun setupFaceDetection() {
//        // Initialize face detector options
//        val options = FaceDetectorOptions.Builder()
//            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
//            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
//            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
//            .setMinFaceSize(0.2f) // Face needs to be at least 35% of frame
//            .enableTracking()
//            .build()
//
//        faceDetectionManager = FaceDetectionManager(options)
//
//        // Setup buttons
//        binding.registerButton.setOnClickListener {
//            startActivity(Intent(this, RegistrationActivity::class.java))
////            finish()
//        }
//
//        binding.viewReportButton.setOnClickListener {
//            startActivity(Intent(this, ReportActivity::class.java))
////            finish()
//        }
//    }
//
//
//    private fun getFaceEmbedding(face: Face): FloatArray {
//
////        val bounds = face.boundingBox
////        val landmarks = face.allLandmarks
////
////        // Convert landmarks to normalized coordinates
////        return landmarks.flatMap { landmark ->
////            val x = (landmark.position.x - bounds.left) / bounds.width()
////            val y = (landmark.position.y - bounds.top) / bounds.height()
////            listOf(x, y)
////        }.toFloatArray()
//
//        val bounds = face.boundingBox
//        val landmarks = face.allLandmarks
//
//        // Enhanced feature extraction
//        val embedding = ArrayList<Float>()
//
//        // Add normalized bounding box dimensions
//        embedding.add(bounds.width().toFloat() / face.boundingBox.height())
//        embedding.add(bounds.height().toFloat() / face.boundingBox.width())
//
//        // Add normalized landmark positions
//        landmarks.forEach { landmark ->
//            val x = (landmark.position.x - bounds.left) / bounds.width()
//            val y = (landmark.position.y - bounds.top) / bounds.height()
//            embedding.add(x)
//            embedding.add(y)
//        }
//
//        // Add face angles if available
//        face.headEulerAngleX.let { embedding.add(it / 360f) }
//        face.headEulerAngleY.let { embedding.add(it / 360f) }
//        face.headEulerAngleZ.let { embedding.add(it / 360f) }
//
//        return embedding.toFloatArray()
//    }
//
//    // Function to find similar faces
//    private suspend fun findSimilarFace(
//        queryEmbedding: FloatArray,
//        threshold: Float = SIMILARITY_THRESHOLD
//    ): Employee? = withContext(Dispatchers.Default) {
//        val employees = database.employeeDao().getAllEmployees()
//
//        employees.find { employee ->
//            val storedEmbedding = employee.getEmbeddingArray()
//            if (storedEmbedding != null && storedEmbedding.size == queryEmbedding.size) {
//                calculateCosineSimilarity(queryEmbedding, storedEmbedding) >= threshold
//            } else {
//                false
//            }
//        }
//    }
//
//    // Cosine similarity calculation
//    private fun calculateCosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
//        if (embedding1.size != embedding2.size) return 0f
//
//        var dotProduct = 0f
//        var norm1 = 0f
//        var norm2 = 0f
//
//        for (i in embedding1.indices) {
//            dotProduct += embedding1[i] * embedding2[i]
//            norm1 += embedding1[i] * embedding1[i]
//            norm2 += embedding2[i] * embedding2[i]
//        }
//
//        norm1 = sqrt(norm1)
//        norm2 = sqrt(norm2)
//
//        return if (norm1 > 0 && norm2 > 0) {
//            dotProduct / (norm1 * norm2)
//        } else {
//            0f
//        }
//    }
//
//    private fun calculateSimilarity(features1: FloatArray, features2: FloatArray): Float {
//        // If features are different lengths, use the shorter length
//        val minLength = minOf(features1.size, features2.size)
//
//        var similarity = 0f
//        var count = 0
//
//        // Calculate average difference for available features
//        for (i in 0 until minLength) {
//            val diff = abs(features1[i] - features2[i])
//            if (!diff.isNaN()) {
//                similarity += 1 - (diff / 2) // Normalize difference to 0-1 range
//                count++
//            }
//        }
//
//        return if (count > 0) similarity / count else 0f
//    }
//
//    private fun determineAttendanceType(): String {
//        val calendar = Calendar.getInstance()
//        val hour = calendar.get(Calendar.HOUR_OF_DAY)
//
//        // Configurable time ranges for check-in and check-out
//        return when (hour) {
//            in 6..12 -> "CHECK_IN"
//            in 16..22 -> "CHECK_OUT"
//            else -> "IRREGULAR"
//        }
//    }
//
//    private fun showError(message: String) {
//        runOnUiThread {
//            AlertDialog.Builder(this).setTitle("Error").setMessage(message)
//                .setPositiveButton("OK", null).show()
//        }
//    }
//
//    private fun showSuccess(message: String) {
//        runOnUiThread {
//            AlertDialog.Builder(this).setTitle("Success").setMessage(message)
//                .setPositiveButton("OK", null).show()
//        }
//    }
//
//    override fun onRequestPermissionsResult(
//        requestCode: Int, permissions: Array<String>, grantResults: IntArray
//    ) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == CAMERA_PERMISSION_REQUEST) {
//            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                setupCamera()
//            } else {
//                showError("Camera permission is required")
//                finish()
//            }
//        }
//    }
//
//    override fun onRestart() {
//        super.onRestart()
//        setupCamera()
//    }
//
//    companion object {
//        private const val SIMILARITY_THRESHOLD = 0.75f
//    }
//}