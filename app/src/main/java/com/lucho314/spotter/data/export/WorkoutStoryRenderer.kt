package com.lucho314.spotter.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
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

private const val WIDTH = 1080
private const val HEIGHT = 1920
private const val PADDING = 96f
private const val CONTENT_WIDTH = WIDTH - 2 * PADDING

/**
 * Renders a [WorkoutExportData] as a vertical (1080x1920) JPEG "story" image for social sharing.
 * Not a pixel-perfect port of the RN app's `workout-export.ts` (inaccessible from this environment
 * - section 7, deviation 15).
 */
@Singleton
class WorkoutStoryRenderer @Inject constructor(@ApplicationContext private val context: Context) {

    private val spaceGroteskBold: Typeface by lazy { loadFont(R.font.space_grotesk_bold, bold = true) }
    private val interRegular: Typeface by lazy { loadFont(R.font.inter_regular, bold = false) }
    private val interSemiBold: Typeface by lazy { loadFont(R.font.inter_semibold, bold = true) }

    private fun loadFont(resId: Int, bold: Boolean): Typeface =
        runCatching { ResourcesCompat.getFont(context, resId) }.getOrNull()
            ?: if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

    fun render(data: WorkoutExportData, out: OutputStream) {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            drawBackground(canvas)

            var y = PADDING
            val brandPaint = textPaint(spaceGroteskBold, 64f, ExportPalette.LIME)
            canvas.drawText(context.getString(R.string.export_brand), PADDING, y + brandPaint.textSize, brandPaint)
            y += brandPaint.lineHeight() + 24f

            val datePaint = textPaint(interRegular, 36f, ExportPalette.ON_SURFACE_VARIANT)
            canvas.drawText(data.dateText, PADDING, y + datePaint.textSize, datePaint)
            y += datePaint.lineHeight() + 32f

            val titlePaint = textPaint(spaceGroteskBold, 88f, ExportPalette.ON_SURFACE)
            val title = data.routineName ?: context.getString(R.string.history_free_workout)
            val titleLayout = staticLayout(title, titlePaint, CONTENT_WIDTH.toInt(), maxLines = 2)
            canvas.save()
            canvas.translate(PADDING, y)
            titleLayout.draw(canvas)
            canvas.restore()
            y += titleLayout.height + 56f

            y = drawTiles(canvas, data, y)
            y += 48f

            val sectionPaint = textPaint(interSemiBold, 40f, ExportPalette.ON_SURFACE_VARIANT)
            canvas.drawText(context.getString(R.string.export_exercises_label), PADDING, y + sectionPaint.textSize, sectionPaint)
            y += sectionPaint.lineHeight() + 24f

            val namePaint = textPaint(interSemiBold, 44f, ExportPalette.ON_SURFACE)
            val summaryPaint = textPaint(interRegular, 34f, ExportPalette.ON_SURFACE_VARIANT)
            data.storyExercises.forEach { exercise ->
                val name = exercise.name ?: context.getString(R.string.session_detail_unknown_exercise)
                val ellipsizedName = TextUtils.ellipsize(name, namePaint, CONTENT_WIDTH, TextUtils.TruncateAt.END)
                canvas.drawText(ellipsizedName.toString(), PADDING, y + namePaint.textSize, namePaint)
                y += namePaint.lineHeight() + 8f

                val summary = exerciseSummaryText(exercise, data.unitLabel)
                val ellipsizedSummary = TextUtils.ellipsize(summary, summaryPaint, CONTENT_WIDTH, TextUtils.TruncateAt.END)
                canvas.drawText(ellipsizedSummary.toString(), PADDING, y + summaryPaint.textSize, summaryPaint)
                y += summaryPaint.lineHeight() + 32f
            }

            if (data.hiddenStoryExerciseCount > 0) {
                val morePaint = textPaint(interSemiBold, 36f, ExportPalette.LIME)
                val moreText = context.resources.getQuantityString(
                    R.plurals.export_more_exercises,
                    data.hiddenStoryExerciseCount,
                    data.hiddenStoryExerciseCount,
                )
                canvas.drawText(moreText, PADDING, y + morePaint.textSize, morePaint)
            }

            val footerPaint = textPaint(interRegular, 28f, ExportPalette.OUTLINE)
            canvas.drawText(context.getString(R.string.export_footer), PADDING, HEIGHT - PADDING, footerPaint)

            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        } finally {
            bitmap.recycle()
        }
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

    private fun drawBackground(canvas: Canvas) {
        val gradient = LinearGradient(0f, 0f, 0f, HEIGHT.toFloat(), ExportPalette.BACKGROUND, ExportPalette.SURFACE_LOW, Shader.TileMode.CLAMP)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gradient }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
    }

    private fun drawTiles(canvas: Canvas, data: WorkoutExportData, top: Float): Float {
        val tileHeight = 200f
        val gap = 24f
        val tileWidth = (CONTENT_WIDTH - 2 * gap) / 3
        val tiles = listOf(
            Triple(context.getString(R.string.session_detail_duration), data.durationText, ExportPalette.CYAN),
            Triple(context.getString(R.string.session_detail_volume), data.totalVolumeText, ExportPalette.LIME),
            Triple(context.getString(R.string.session_detail_sets), data.totalSets.toString(), ExportPalette.ON_SURFACE),
        )
        val tileBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ExportPalette.SURFACE_CONTAINER }
        val valuePaint = textPaint(spaceGroteskBold, 72f, ExportPalette.ON_SURFACE)
        val labelPaint = textPaint(interRegular, 30f, ExportPalette.ON_SURFACE_VARIANT)
        tiles.forEachIndexed { index, (label, value, color) ->
            val left = PADDING + index * (tileWidth + gap)
            val rect = RectF(left, top, left + tileWidth, top + tileHeight)
            canvas.drawRoundRect(rect, 24f, 24f, tileBackground)
            valuePaint.color = color
            canvas.drawText(value, left + 20f, top + 90f, valuePaint)
            canvas.drawText(label, left + 20f, top + tileHeight - 30f, labelPaint)
        }
        return top + tileHeight
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
