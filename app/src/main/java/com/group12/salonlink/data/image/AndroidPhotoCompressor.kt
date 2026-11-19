package com.group12.salonlink.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.group12.salonlink.core.di.DefaultDispatcher
import com.group12.salonlink.domain.model.DataError
import com.group12.salonlink.domain.model.DataException
import com.group12.salonlink.domain.repository.PhotoCompressor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Turns a picked photo into a JPEG of at most [MAX_SIDE] px and about [TARGET_BYTES].
 *
 * WHY so small: photos are stored inside Firestore documents (1 MB limit, no Cloud Storage on
 * Spark), and a moodboard only needs phone-screen quality. Lower quality is tried until it fits.
 */
class AndroidPhotoCompressor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : PhotoCompressor {

    override suspend fun compress(uri: String): ByteArray = withContext(dispatcher) {
        try {
            val bitmap = decode(Uri.parse(uri))
            try {
                var quality = 82
                var bytes = bitmap.toJpeg(quality)
                while (bytes.size > TARGET_BYTES && quality > 40) {
                    quality -= 12
                    bytes = bitmap.toJpeg(quality)
                }
                if (bytes.size > MAX_BYTES) throw DataException(DataError.INVALID_REQUEST)
                bytes
            } finally {
                bitmap.recycle()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: DataException) {
            throw e
        } catch (e: Exception) {
            throw DataException(DataError.INVALID_REQUEST, e)
        }
    }

    /** Decodes already scaled down. On Android 9+ ImageDecoder also applies the EXIF rotation. */
    private fun decode(uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = minOf(1.0, MAX_SIDE.toDouble() / maxOf(w, h))
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw DataException(DataError.INVALID_REQUEST)
        val scale = minOf(1.0, MAX_SIDE.toDouble() / maxOf(decoded.width, decoded.height))
        if (scale >= 1.0) return decoded
        val scaled = Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun Bitmap.toJpeg(quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }

    private companion object {
        const val MAX_SIDE = 1024
        const val TARGET_BYTES = 200_000
        const val MAX_BYTES = 300_000 // must match the photo size limit in firestore.rules
    }
}
