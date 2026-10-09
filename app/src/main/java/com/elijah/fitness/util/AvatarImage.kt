package com.elijah.fitness.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns whatever the photo picker returns into an upload the server will
 * accept: a JPEG no bigger than [MAX_SIDE_PX] on its long side.
 *
 * Why this exists: the avatar endpoint only takes JPEG or PNG up to
 * 2 MB, but a phone camera photo is usually 3–8 MB, and Samsung phones
 * save HEIC by default — so most real-world uploads were rejected.
 * Decoding and re-encoding on the phone fixes both, and also applies the
 * photo's EXIF rotation so portraits don't arrive sideways.
 *
 * Heavy work — call it off the main thread.
 */
object AvatarImage {

    private const val MAX_SIDE_PX = 1024
    private const val JPEG_QUALITY = 85

    /** JPEG bytes ready to upload, or `null` if [uri] isn't a readable image. */
    @WorkerThread
    fun prepareJpeg(resolver: ContentResolver, uri: Uri): ByteArray? {
        val bitmap = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                decodeScaled(resolver, uri)
            } else {
                decodeScaledLegacy(resolver, uri)
            }
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        } ?: return null

        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    /** API 28+: ImageDecoder reads HEIC and applies EXIF rotation itself. */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeScaled(resolver: ContentResolver, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(resolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = minOf(1.0, MAX_SIDE_PX.toDouble() / max(w, h))
            decoder.setTargetSize(
                (w * scale).roundToInt().coerceAtLeast(1),
                (h * scale).roundToInt().coerceAtLeast(1),
            )
            // Software memory so the bitmap can be JPEG-encoded.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    /** API 27: sample down while decoding, then fix rotation from EXIF. */
    private fun decodeScaledLegacy(resolver: ContentResolver, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null

        val rotation = resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val scale = minOf(1f, MAX_SIDE_PX.toFloat() / max(decoded.width, decoded.height))
        if (rotation == 0f && scale == 1f) return decoded
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation)
        }
        val transformed = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (transformed !== decoded) decoded.recycle()
        return transformed
    }
}
