package com.lucho314.spotter.domain.usecase

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.NewRoutineExercise
import com.lucho314.spotter.domain.model.RoutineInput
import com.lucho314.spotter.domain.model.TemplateDetail
import com.lucho314.spotter.domain.model.UNASSIGNED_DAY_NUMBER
import com.lucho314.spotter.domain.repository.RoutineRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Adopts a [TemplateDetail] by creating **one routine per template day** (RN parity, section 5:
 * "adoptar crea una rutina por día"). Each created routine has no `routine_days` row of its own -
 * its exercises are inserted at [UNASSIGNED_DAY_NUMBER], not `1` like the RN app's day-less
 * routines: reusing our own sentinel keeps every screen's "unassigned" grouping consistent instead
 * of importing the legacy day-numbering quirk this migration is fixing (see
 * [com.lucho314.spotter.domain.calc.RoutineOrdering.suggestedFirstDayNumber]'s KDoc for the
 * concrete bug that quirk causes once the user tries to organize the routine by days).
 *
 * Not transactional server-side (RN bug 22, `services/templates.ts:52-96`): if any step after the
 * first routine was created fails, every routine created so far by this call is deleted as a
 * best-effort compensation before the original error is returned. This includes **cancellation**
 * (e.g. the screen navigating away/being cleared mid-adoption): the compensation itself runs in
 * [NonCancellable] so it isn't cut short by the very cancellation that triggered it, and the
 * [CancellationException] is rethrown afterwards so coroutine cancellation still propagates
 * normally (review carry-over: without this, navigating back while adopting left half-created
 * routines behind, same as RN bug 22).
 *
 * **[RESUELTO en FASE 6] Residual risk, shared with [com.lucho314.spotter.domain.usecase.ImportSharedRoutineUseCase]:**
 * if the cancellation lands while `createRoutine` itself is in flight (not yet returned), the
 * routine can end up created server-side with no id known here to compensate with. This is
 * mitigated by `BackHandler` in the screens driving both use cases, which disables navigating away
 * while the operation is running.
 */
class AdoptTemplateUseCase @Inject constructor(
    private val routineRepository: RoutineRepository,
) {
    suspend operator fun invoke(userId: String, template: TemplateDetail): AppResult<List<String>> {
        val createdRoutineIds = mutableListOf<String>()
        try {
            for (day in template.days) {
                val input = RoutineInput(
                    name = "${template.summary.name} - ${day.name}",
                    description = template.summary.description,
                    daysPerWeek = template.summary.daysPerWeek,
                )
                val createResult = routineRepository.createRoutine(userId, input, sourceTemplateId = template.summary.id)
                val routineId = when (createResult) {
                    is AppResult.Success -> createResult.value
                    is AppResult.Failure -> return compensateAndReturn(userId, createdRoutineIds, createResult)
                }
                createdRoutineIds += routineId

                if (day.exercises.isEmpty()) continue
                val items = day.exercises.map { exercise ->
                    NewRoutineExercise(
                        exerciseId = exercise.exerciseId,
                        dayNumber = UNASSIGNED_DAY_NUMBER,
                        targetSets = exercise.targetSets,
                        targetReps = exercise.targetReps,
                        restSeconds = exercise.restSeconds,
                    ) to exercise.sortOrder
                }
                val addResult = routineRepository.addExercises(userId, routineId, items)
                if (addResult is AppResult.Failure) return compensateAndReturn(userId, createdRoutineIds, addResult)
            }
            return AppResult.Success(createdRoutineIds)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { compensate(userId, createdRoutineIds) }
            throw e
        }
    }

    private suspend fun compensateAndReturn(
        userId: String,
        createdRoutineIds: List<String>,
        failure: AppResult.Failure,
    ): AppResult.Failure {
        compensate(userId, createdRoutineIds)
        return failure
    }

    private suspend fun compensate(userId: String, createdRoutineIds: List<String>) {
        // Best-effort: a compensation failure must not hide the original error/cancellation, and
        // there is nothing more useful to do with it here than move on to the next routine.
        createdRoutineIds.forEach { routineRepository.deleteRoutine(userId, it) }
    }
}
