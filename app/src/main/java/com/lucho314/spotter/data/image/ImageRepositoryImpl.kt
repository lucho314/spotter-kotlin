package com.lucho314.spotter.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.IoDispatcher
import com.lucho314.spotter.core.common.Logger
import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.core.common.ValidationReason
import com.lucho314.spotter.domain.model.AI_IMPORT_MAX_BASE64_LENGTH
import com.lucho314.spotter.domain.repository.ImageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val TAG = "ImageRepository"
private const val CAMERA_DIRECTORY = "camera"
private const val CAMERA_FILE_MAX_AGE_MS = 60 * 60 * 1000L

@Singleton
class ImageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val timeProvider: TimeProvider,
    private val logger: Logger,
) : ImageRepository {

    override suspend fun createCameraCaptureUri(): AppResult<String> = withContext(ioDispatcher) {
        try {
            val dir = File(context.cacheDir, CAMERA_DIRECTORY).apply { mkdirs() }
            purgeStaleFiles(dir)
            val file = File.createTempFile("capture-", ".jpg", dir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            AppResult.Success(uri.toString())
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Unknown(t))
        }
    }

    override suspend fun clearCameraCaptures() {
        withContext(ioDispatcher) {
            runCatching { File(context.cacheDir, CAMERA_DIRECTORY).listFiles()?.forEach { it.delete() } }
                .onFailure { logger.w(TAG, "clearCameraCaptures failed: ${it::class.simpleName}") }
        }
    }

    private fun purgeStaleFiles(dir: File) {
        val now = timeProvider.now().toEpochMilli()
        dir.listFiles()?.forEach { file ->
            if (now - file.lastModified() > CAMERA_FILE_MAX_AGE_MS) file.delete()
        }
    }

    override suspend fun encodeForAiImport(uri: String): AppResult<String> = withContext(ioDispatcher) {
        var scaled: Bitmap? = null
        var rotated: Bitmap? = null
        try {
            val resolver = context.contentResolver
            val parsedUri = Uri.parse(uri)

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(parsedUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@withContext AppResult.Failure(AppError.Validation(ValidationReason.IMAGE_UNREADABLE))
            }

            val orientation = runCatching {
                resolver.openInputStream(parsedUri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

            val sampleSize = ImageSizing.inSampleSize(bounds.outWidth, bounds.outHeight)
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = resolver.openInputStream(parsedUri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
                ?: return@withContext AppResult.Failure(AppError.Validation(ValidationReason.IMAGE_UNREADABLE))

            val (targetWidth, targetHeight) = ImageSizing.scaledSize(decoded.width, decoded.height)
            scaled = if (targetWidth != decoded.width || targetHeight != decoded.height) {
                Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true).also { if (it !== decoded) decoded.recycle() }
            } else {
                decoded
            }

            val transform = ImageSizing.exifTransform(orientation)
            rotated = if (transform.rotationDegrees != 0 || transform.flipHorizontal) {
                val matrix = Matrix().apply {
                    setRotate(transform.rotationDegrees.toFloat())
                    if (transform.flipHorizontal) postScale(-1f, 1f)
                }
                Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
            } else {
                scaled
            }

            for (quality in ImageSizing.JPEG_QUALITIES) {
                val bytes = ByteArrayOutputStream().use { stream ->
                    rotated.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                    stream.toByteArray()
                }
                if (ImageSizing.base64Length(bytes.size) <= AI_IMPORT_MAX_BASE64_LENGTH) {
                    return@withContext AppResult.Success(Base64.getEncoder().encodeToString(bytes))
                }
            }
            AppResult.Failure(AppError.Validation(ValidationReason.IMAGE_TOO_LARGE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            AppResult.Failure(AppError.Validation(ValidationReason.IMAGE_TOO_LARGE))
        } catch (e: FileNotFoundException) {
            AppResult.Failure(AppError.Validation(ValidationReason.IMAGE_UNREADABLE))
        } catch (e: SecurityException) {
            AppResult.Failure(AppError.Validation(ValidationReason.IMAGE_UNREADABLE))
        } catch (e: IOException) {
            AppResult.Failure(AppError.Validation(ValidationReason.IMAGE_UNREADABLE))
        } catch (e: IllegalArgumentException) {
            AppResult.Failure(AppError.Validation(ValidationReason.IMAGE_UNREADABLE))
        } finally {
            // Recycle every intermediate bitmap distinct from each other and from the final result.
            listOfNotNull(scaled, rotated).distinct().forEach { if (!it.isRecycled) it.recycle() }
        }
    }
}
