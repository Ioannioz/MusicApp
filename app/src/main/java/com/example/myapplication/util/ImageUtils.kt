package com.example.myapplication.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import java.io.ByteArrayOutputStream

object ImageUtils {

    private val bitmapCache = LruCache<String, Bitmap>(30)

    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 75): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        return "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun getExifRotationDegrees(context: Context, uri: Uri): Float {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return 0f
            val exif = ExifInterface(input)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            input.close()

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (_: Exception) {
            0f
        }
    }

    fun uriToBase64(context: Context, uri: Uri, targetSize: Int = 140): String? {
        return try {
            val degrees = getExifRotationDegrees(context, uri)

            val input = context.contentResolver.openInputStream(uri) ?: return null
            val original = BitmapFactory.decodeStream(input) ?: return null
            input.close()

            val rotated = if (degrees != 0f) {
                val matrix = Matrix().apply { postRotate(degrees) }
                Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
            } else {
                original
            }

            val scaled = Bitmap.createScaledBitmap(rotated, targetSize, targetSize, true)
            bitmapToBase64(scaled)
        } catch (_: Exception) {
            null
        }
    }

    fun decodeBase64ToBitmap(base64Str: String?): Bitmap? {
        if (base64Str.isNullOrEmpty()) return null

        val cached = bitmapCache.get(base64Str)
        if (cached != null && !cached.isRecycled) {
            return cached
        }

        return try {
            val cleanBase64 = if (base64Str.contains(",")) base64Str.substringAfter(",") else base64Str
            val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val decodedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            if (decodedBitmap != null) {
                bitmapCache.put(base64Str, decodedBitmap)
            }
            decodedBitmap
        } catch (_: Exception) {
            null
        }
    }
}
