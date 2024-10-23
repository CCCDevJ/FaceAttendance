package com.devjethava.facialattendance.helper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.ImageProxy

fun ByteArray.toFloatArray(): FloatArray {
    return FloatArray(this.size) { this[it].toFloat() }
}

// FloatArray extension
fun FloatArray.toByteArray(): ByteArray {
    return ByteArray(this.size) { this[it].toInt().toByte() }
}

// ImageProxy extension
fun ImageProxy.toBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}