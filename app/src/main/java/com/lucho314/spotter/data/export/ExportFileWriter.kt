package com.lucho314.spotter.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.domain.model.ExportFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Writes a rendered export file to `cacheDir/exports/` and hands back its `FileProvider` Uri. */
@Singleton
class ExportFileWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
) {
    /** Runs [block] against the new file's [OutputStream]; on any exception, the partial file is deleted and the exception rethrown. */
    fun write(format: ExportFormat, block: (OutputStream) -> Unit): Uri {
        val dir = File(context.cacheDir, ExportFileNames.DIRECTORY).apply { mkdirs() }
        purgeStaleFiles(dir)
        val file = File(dir, ExportFileNames.fileName(format, timeProvider.now()))
        try {
            file.outputStream().use(block)
        } catch (t: Throwable) {
            file.delete()
            throw t
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun purgeStaleFiles(dir: File) {
        val now = timeProvider.now().toEpochMilli()
        dir.listFiles()?.forEach { file ->
            if (ExportFileNames.isStale(file.lastModified(), now)) file.delete()
        }
    }
}
