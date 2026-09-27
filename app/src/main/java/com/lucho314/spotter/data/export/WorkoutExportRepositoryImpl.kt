package com.lucho314.spotter.data.export

import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.IoDispatcher
import com.lucho314.spotter.core.common.Logger
import com.lucho314.spotter.domain.model.ExportFormat
import com.lucho314.spotter.domain.model.ExportedFile
import com.lucho314.spotter.domain.model.WorkoutExportData
import com.lucho314.spotter.domain.repository.WorkoutExportRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val TAG = "WorkoutExportRepository"

@Singleton
class WorkoutExportRepositoryImpl @Inject constructor(
    private val writer: ExportFileWriter,
    private val pdfRenderer: WorkoutPdfRenderer,
    private val storyRenderer: WorkoutStoryRenderer,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
) : WorkoutExportRepository {

    override suspend fun export(data: WorkoutExportData, format: ExportFormat): AppResult<ExportedFile> = withContext(ioDispatcher) {
        try {
            val uri = writer.write(format) { out ->
                when (format) {
                    ExportFormat.PDF -> pdfRenderer.render(data, out)
                    ExportFormat.STORY -> storyRenderer.render(data, out)
                }
            }
            AppResult.Success(ExportedFile(uri.toString(), format.mimeType))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            logger.w(TAG, "export failed: ${t::class.simpleName}")
            AppResult.Failure(AppError.Unknown(t))
        }
    }
}
