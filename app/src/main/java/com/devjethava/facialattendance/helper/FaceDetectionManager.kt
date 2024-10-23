package com.devjethava.facialattendance.helper

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

//class FaceDetectionManager {
//    private val faceDetector = FaceDetection.getClient(
//        FaceDetectorOptions.Builder()
//            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
//            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
//            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
//            .setMinFaceSize(0.35f) // Face needs to be at least 35% of frame
//            .build()
//    )
//
//    fun detectFace(image: InputImage): List<Face> {
//        return try {
//            Tasks.await(faceDetector.process(image))
//        } catch (e: Exception) {
//            emptyList()
//        }
//    }
//}

//class FaceDetectionManager(options: FaceDetectorOptions) {
//    private val faceDetector = FaceDetection.getClient(options)
//
//    fun detectFace(image: InputImage): List<Face> {
//        return try {
//            Tasks.await(faceDetector.process(image))
//        } catch (e: Exception) {
//            emptyList()
//        }
//    }
//}

class FaceDetectionManager(options: FaceDetectorOptions) {
    private val faceDetector: FaceDetector = FaceDetection.getClient(options)

    suspend fun detectFace(image: InputImage): List<Face> = suspendCoroutine { continuation ->
        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                continuation.resume(faces)
            }
            .addOnFailureListener { e ->
                continuation.resumeWithException(e)
            }
    }
}