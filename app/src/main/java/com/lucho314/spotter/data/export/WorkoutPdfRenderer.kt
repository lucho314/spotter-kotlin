package com.lucho314.spotter.data.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import com.lucho314.spotter.R
import com.lucho314.spotter.domain.model.ExportExercise
import com.lucho314.spotter.domain.model.WorkoutExportData
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 36f
private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

/**
 * Renders a [WorkoutExportData] as a paginated A4 (595x842pt) PDF via `android.graphics.pdf.PdfDocument`.
 * Follows the app's dark theme ([ExportPalette]) and typefaces; not a pixel-perfect port of the RN
 * app's `workout-export.ts` (inaccessible from this environment - section 7, deviation 15).
 */
@Singleton
class WorkoutPdfRenderer @Inject constructor(@ApplicationContext private val context: Context) {

    private val spaceGroteskBold: Typeface by lazy { loadFont(R.font.space_grotesk_bold, bold = true) }
    private val interRegular: Typeface by lazy { loadFont(R.font.inter_regular, bold = false) }
    private val interSemiBold: Typeface by lazy { loadFont(R.font.inter_semibold, bold = true) }

    private fun loadFont(resId: Int, bold: Boolean): Typeface =
        runCatching { ResourcesCompat.getFont(context, resId) }.getOrNull()
            ?: if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

    /** Mutable "current page" state, so pagination can be driven from deep inside the per-row loop. */
    private inner class PageState(document: PdfDocument) {
        val document = document
        var pageNumber = 1
        var page: PdfDocument.Page = document.startPage(pageInfo(pageNumber))
        var canvas: Canvas = page.canvas
        val cursor = PageCursor(top = MARGIN, bottom = PAGE_HEIGHT - MARGIN)

        init {
            drawBackground(canvas)
        }

        /** Finishes the current page, starts a new one (with a fresh background), and resets the cursor. */
        fun breakPage() {
            drawFooter(canvas, pageNumber)
            document.finishPage(page)
            pageNumber++
            page = document.startPage(pageInfo(pageNumber))
            canvas = page.canvas
            drawBackground(canvas)
            cursor.newPage()
        }

        fun ensureSpace(height: Float) {
            if (cursor.needsNewPage(height)) breakPage()
        }
    }

    fun render(data: WorkoutExportData, out: OutputStream) {
        val document = PdfDocument()
        try {
            val state = PageState(document)

            drawHeader(state.canvas, data, state.cursor)
            drawHeroStats(state.canvas, data, state.cursor)
            drawSectionLabel(state.canvas, state.cursor)

            data.exercises.forEach { exercise -> drawExerciseBlock(state, exercise, data.unitLabel) }

            drawFooter(state.canvas, state.pageNumber)
            document.finishPage(state.page)
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    private fun pageInfo(pageNumber: Int) = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()

    private fun drawBackground(canvas: Canvas) {
        canvas.drawColor(ExportPalette.BACKGROUND)
    }

    private fun drawHeader(canvas: Canvas, data: WorkoutExportData, cursor: PageCursor) {
        val brandPaint = textPaint(spaceGroteskBold, 20f, ExportPalette.LIME)
        canvas.drawText(context.getString(R.string.export_brand), MARGIN, cursor.y + brandPaint.textSize, brandPaint)
        cursor.advance(brandPaint.lineHeight() + 6f)

        val datePaint = textPaint(interRegular, 10f, ExportPalette.ON_SURFACE_VARIANT)
        canvas.drawText(data.dateText, MARGIN, cursor.y + datePaint.textSize, datePaint)
        cursor.advance(datePaint.lineHeight() + 8f)

        val titlePaint = textPaint(spaceGroteskBold, 22f, ExportPalette.ON_SURFACE)
        val title = data.routineName ?: context.getString(R.string.history_free_workout)
        val layout = staticLayout(title, titlePaint, CONTENT_WIDTH.toInt(), maxLines = 2)
        canvas.save()
        canvas.translate(MARGIN, cursor.y)
        layout.draw(canvas)
        canvas.restore()
        cursor.advance(layout.height.toFloat() + 16f)
    }

    private fun drawHeroStats(canvas: Canvas, data: WorkoutExportData, cursor: PageCursor) {
        val boxHeight = 64f
        val gap = 12f
        val boxWidth = (CONTENT_WIDTH - 2 * gap) / 3
        val stats = listOf(
            Triple(context.getString(R.string.session_detail_duration), data.durationText, ExportPalette.CYAN),
            Triple(context.getString(R.string.session_detail_volume), data.totalVolumeText, ExportPalette.LIME),
            Triple(context.getString(R.string.session_detail_sets), data.totalSets.toString(), ExportPalette.ON_SURFACE),
        )
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ExportPalette.SURFACE_CONTAINER }
        val labelPaint = textPaint(interRegular, 9f, ExportPalette.ON_SURFACE_VARIANT)
        stats.forEachIndexed { index, (label, value, color) ->
            val left = MARGIN + index * (boxWidth + gap)
            val rect = RectF(left, cursor.y, left + boxWidth, cursor.y + boxHeight)
            canvas.drawRoundRect(rect, 8f, 8f, boxPaint)
            val valuePaint = textPaint(spaceGroteskBold, 18f, color)
            canvas.drawText(value, left + 10f, cursor.y + 30f, valuePaint)
            canvas.drawText(label, left + 10f, cursor.y + boxHeight - 12f, labelPaint)
        }
        cursor.advance(boxHeight + 20f)
    }

    private fun drawSectionLabel(canvas: Canvas, cursor: PageCursor) {
        val paint = textPaint(interSemiBold, 12f, ExportPalette.ON_SURFACE_VARIANT)
        canvas.drawText(context.getString(R.string.export_exercises_label), MARGIN, cursor.y + paint.textSize, paint)
        cursor.advance(paint.lineHeight() + 10f)
    }

    private fun tableColumns(unitLabel: String) = listOf(
        "#",
        context.getString(R.string.export_table_weight, unitLabel),
        context.getString(R.string.export_table_reps),
        context.getString(R.string.export_table_rpe),
    )

    /**
     * Draws one exercise's title, summary and set table, breaking onto a new page - repeating the
     * table header under an "(cont.)" title - if a row doesn't fit (reserves space for the title,
     * table header and first row together up front, so a lone oversize title never causes an
     * infinite loop: see [PageCursor.needsNewPage]).
     */
    private fun drawExerciseBlock(state: PageState, exercise: ExportExercise, unitLabel: String) {
        val name = exercise.name ?: context.getString(R.string.session_detail_unknown_exercise)
        val namePaint = textPaint(interSemiBold, 13f, ExportPalette.ON_SURFACE)
        val summaryPaint = textPaint(interRegular, 10f, ExportPalette.ON_SURFACE_VARIANT)
        val headerPaint = textPaint(interRegular, 9f, ExportPalette.OUTLINE)
        val rowHeight = textPaint(interRegular, 10f, ExportPalette.ON_SURFACE).lineHeight() + 4f
        val firstBlockHeight = namePaint.lineHeight() + 4f + summaryPaint.lineHeight() + 8f + headerPaint.lineHeight() + 4f + rowHeight

        state.ensureSpace(firstBlockHeight)
        drawExerciseTitle(state.canvas, state.cursor, name, namePaint, continued = false)
        drawExerciseSummary(state.canvas, state.cursor, exercise, unitLabel, summaryPaint)
        drawTableHeader(state, unitLabel, headerPaint)

        val rowPaint = textPaint(interRegular, 10f, ExportPalette.ON_SURFACE)
        val warmupPaint = textPaint(interRegular, 10f, ExportPalette.ON_SURFACE_VARIANT)
        exercise.sets.forEachIndexed { index, set ->
            if (index > 0 && state.cursor.needsNewPage(rowHeight)) {
                state.breakPage()
                drawExerciseTitle(state.canvas, state.cursor, name, namePaint, continued = true)
                drawTableHeader(state, unitLabel, headerPaint)
            }
            val paint = if (set.isWarmup) warmupPaint else rowPaint
            val warmupPrefix = if (set.isWarmup) "${context.getString(R.string.export_warmup_short)} " else ""
            val cells = listOf("$warmupPrefix#${set.setNumber}", set.weightText, set.reps.toString(), set.rpeText ?: "—")
            drawTableRow(state.canvas, state.cursor.y + paint.textSize, cells, paint)
            state.cursor.advance(rowHeight)
        }
        state.cursor.advance(14f)
    }

    private fun drawExerciseTitle(canvas: Canvas, cursor: PageCursor, name: String, paint: TextPaint, continued: Boolean) {
        val displayName = if (continued) context.getString(R.string.export_continued, name) else name
        val ellipsized = TextUtils.ellipsize(displayName, paint, CONTENT_WIDTH, TextUtils.TruncateAt.END)
        canvas.drawText(ellipsized.toString(), MARGIN, cursor.y + paint.textSize, paint)
        cursor.advance(paint.lineHeight() + 4f)
    }

    private fun drawExerciseSummary(canvas: Canvas, cursor: PageCursor, exercise: ExportExercise, unitLabel: String, paint: TextPaint) {
        val summary = "${exerciseSummaryText(exercise, unitLabel)} - ${exercise.volumeText}"
        canvas.drawText(summary, MARGIN, cursor.y + paint.textSize, paint)
        cursor.advance(paint.lineHeight() + 8f)
    }

    private fun exerciseSummaryText(exercise: ExportExercise, unitLabel: String): String {
        val top = exercise.topSet ?: return context.getString(R.string.session_detail_sets) + ": " + exercise.workingSetCount
        return context.resources.getQuantityString(
            R.plurals.export_exercise_summary,
            top.reps,
            exercise.workingSetCount,
            top.reps,
            top.weightText,
            unitLabel,
        )
    }

    private fun drawTableHeader(state: PageState, unitLabel: String, paint: TextPaint) {
        drawTableRow(state.canvas, state.cursor.y + paint.textSize, tableColumns(unitLabel), paint)
        state.cursor.advance(paint.lineHeight() + 4f)
    }

    private fun drawTableRow(canvas: Canvas, baseline: Float, cells: List<String>, paint: Paint) {
        val columnWidth = CONTENT_WIDTH / cells.size
        cells.forEachIndexed { index, cell -> canvas.drawText(cell, MARGIN + index * columnWidth, baseline, paint) }
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int) {
        val leftPaint = textPaint(interRegular, 8f, ExportPalette.OUTLINE)
        canvas.drawText(context.getString(R.string.export_footer), MARGIN, PAGE_HEIGHT - MARGIN / 2, leftPaint)
        val rightPaint = textPaint(interRegular, 8f, ExportPalette.OUTLINE).apply { textAlign = Paint.Align.RIGHT }
        val pageText = context.getString(R.string.export_page_number, pageNumber)
        canvas.drawText(pageText, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - MARGIN / 2, rightPaint)
    }

    private fun textPaint(typeface: Typeface, size: Float, color: Int): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = size
            this.color = color
        }

    private fun Paint.lineHeight(): Float = fontMetrics.descent - fontMetrics.ascent

    private fun staticLayout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
}
