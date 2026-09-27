package com.lucho314.spotter.domain.repository

import com.lucho314.spotter.core.common.AppResult

/**
 * Camera capture staging and AI-import image encoding. The feature layer never touches `Uri`
 * internals, `Bitmap` or `FileProvider` directly - everything Android-specific lives behind this
 * port's implementation in `data/image/`.
 */
interface ImageRepository {
    /**
     * Creates an empty temp file under `cacheDir/camera/`, returned as a `FileProvider`
     * `content://` Uri string for `ActivityResultContracts.TakePicture()` to write into. Purges
     * files older than 1 hour first.
     */
    suspend fun createCameraCaptureUri(): AppResult<String>

    /** Best effort: deletes every file under `cacheDir/camera/`. */
    suspend fun clearCameraCaptures()

    /**
     * Decodes the image at [uri] (downsampled), rotates it per its EXIF orientation, scales it down
     * to at most 1600px on its longest side, and re-encodes it as JPEG (quality 80, then 70, then
     * 60) until its base64 length is at most [com.lucho314.spotter.domain.model.AI_IMPORT_MAX_BASE64_LENGTH].
     * The re-encoded image carries no EXIF metadata (including GPS).
     *
     * Failures: [com.lucho314.spotter.core.common.AppError.Validation] with
     * [com.lucho314.spotter.core.common.ValidationReason.IMAGE_UNREADABLE] or
     * [com.lucho314.spotter.core.common.ValidationReason.IMAGE_TOO_LARGE]. Never
     * [com.lucho314.spotter.core.common.AppError.Network]: this never touches the network.
     */
    suspend fun encodeForAiImport(uri: String): AppResult<String>
}
