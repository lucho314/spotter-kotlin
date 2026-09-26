package com.lucho314.spotter.domain.usecase

import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.core.common.ValidationReason
import com.lucho314.spotter.domain.calc.AgeCalculator
import com.lucho314.spotter.domain.calc.WeightConverter
import com.lucho314.spotter.domain.calc.WeightInputParser
import com.lucho314.spotter.domain.model.Profile
import com.lucho314.spotter.domain.model.ProfileEdit
import com.lucho314.spotter.domain.model.ProfileGoal
import com.lucho314.spotter.domain.repository.ProfileRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import javax.inject.Inject

private val BIRTH_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
private const val MIN_BODY_WEIGHT_KG = 20.0
private const val MAX_BODY_WEIGHT_KG = 400.0
private const val MIN_HEIGHT_CM = 100
private const val MAX_HEIGHT_CM = 250
private const val MIN_AGE = 10
private const val MAX_AGE = 100

/**
 * Applies one [ProfileEdit] on top of [current] (the other physical fields pass through
 * unchanged), validating it first - an invalid edit never reaches [ProfileRepository]. An empty
 * text field (after `trim`) clears that field, for weight/height/birth date.
 */
class UpdateProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(userId: String, current: Profile, edit: ProfileEdit): AppResult<Profile> {
        val fields = when (edit) {
            is ProfileEdit.Weight -> parseWeight(edit, current)
            is ProfileEdit.Height -> parseHeight(edit, current)
            is ProfileEdit.BirthDate -> parseBirthDate(edit, current)
            is ProfileEdit.Goal -> ParsedFields.Valid(current.weightKg, current.heightCm, current.birthDate, edit.goal?.apiValue)
        }
        if (fields is ParsedFields.Invalid) return AppResult.Failure(AppError.Validation(fields.reason))
        check(fields is ParsedFields.Valid)

        return when (
            val result = profileRepository.updatePhysical(
                userId = userId,
                weightKg = fields.weightKg,
                heightCm = fields.heightCm,
                birthDate = fields.birthDate,
                goal = fields.rawGoal,
            )
        ) {
            is AppResult.Failure -> result
            is AppResult.Success -> AppResult.Success(
                current.copy(
                    weightKg = fields.weightKg,
                    heightCm = fields.heightCm,
                    birthDate = fields.birthDate,
                    goal = ProfileGoal.fromApi(fields.rawGoal),
                    rawGoal = fields.rawGoal,
                ),
            )
        }
    }

    private fun parseWeight(edit: ProfileEdit.Weight, current: Profile): ParsedFields {
        val text = edit.text.trim()
        if (text.isEmpty()) return ParsedFields.Valid(null, current.heightCm, current.birthDate, current.rawGoal)
        val rawValue = WeightInputParser.parseWeight(text) ?: return ParsedFields.Invalid(ValidationReason.WEIGHT_INVALID)
        val kg = WeightConverter.toKg(rawValue, edit.unit)
        if (kg !in MIN_BODY_WEIGHT_KG..MAX_BODY_WEIGHT_KG) return ParsedFields.Invalid(ValidationReason.BODY_WEIGHT_RANGE)
        return ParsedFields.Valid(kg, current.heightCm, current.birthDate, current.rawGoal)
    }

    private fun parseHeight(edit: ProfileEdit.Height, current: Profile): ParsedFields {
        val text = edit.text.trim()
        if (text.isEmpty()) return ParsedFields.Valid(current.weightKg, null, current.birthDate, current.rawGoal)
        val heightCm = text.toIntOrNull() ?: return ParsedFields.Invalid(ValidationReason.HEIGHT_RANGE)
        if (heightCm !in MIN_HEIGHT_CM..MAX_HEIGHT_CM) return ParsedFields.Invalid(ValidationReason.HEIGHT_RANGE)
        return ParsedFields.Valid(current.weightKg, heightCm, current.birthDate, current.rawGoal)
    }

    private fun parseBirthDate(edit: ProfileEdit.BirthDate, current: Profile): ParsedFields {
        val text = edit.text.trim()
        if (text.isEmpty()) return ParsedFields.Valid(current.weightKg, current.heightCm, null, current.rawGoal)
        val birthDate = try {
            LocalDate.parse(text, BIRTH_DATE_FORMATTER)
        } catch (e: DateTimeParseException) {
            return ParsedFields.Invalid(ValidationReason.BIRTH_DATE_INVALID)
        }
        val today = timeProvider.now().atZone(timeProvider.zone()).toLocalDate()
        if (birthDate.isAfter(today)) return ParsedFields.Invalid(ValidationReason.BIRTH_DATE_INVALID)
        val age = AgeCalculator.age(birthDate, today)
        if (age !in MIN_AGE..MAX_AGE) return ParsedFields.Invalid(ValidationReason.AGE_RANGE)
        return ParsedFields.Valid(current.weightKg, current.heightCm, birthDate, current.rawGoal)
    }

    private sealed interface ParsedFields {
        data class Valid(val weightKg: Double?, val heightCm: Int?, val birthDate: LocalDate?, val rawGoal: String?) : ParsedFields
        data class Invalid(val reason: ValidationReason) : ParsedFields
    }
}
