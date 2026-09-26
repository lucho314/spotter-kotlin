package com.lucho314.spotter.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.ValidationReason
import com.lucho314.spotter.domain.model.Profile
import com.lucho314.spotter.domain.model.ProfileEdit
import com.lucho314.spotter.domain.model.ProfileGoal
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.testutil.FakeProfileRepository
import com.lucho314.spotter.testutil.FakeTimeProvider
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val USER_ID = "user-1"

class UpdateProfileUseCaseTest {

    private val profileRepository = FakeProfileRepository()
    private val timeProvider = FakeTimeProvider(instant = Instant.parse("2026-01-15T10:00:00Z"))
    private val useCase = UpdateProfileUseCase(profileRepository, timeProvider)

    private val current = Profile(
        id = USER_ID, displayName = "Ada", avatarUrl = null, weightKg = 70.0, heightCm = 170,
        birthDate = null, goal = null, rawGoal = "strength",
    )

    private suspend fun invoke(edit: ProfileEdit) = useCase(USER_ID, current, edit)

    // --- Weight ---

    @Test
    fun `weight below 20kg is BODY_WEIGHT_RANGE`() = runTest {
        val result = invoke(ProfileEdit.Weight("19,99", WeightUnit.KG))
        assertThat(result).isEqualTo(AppResult.Failure(AppError.Validation(ValidationReason.BODY_WEIGHT_RANGE)))
    }

    @Test
    fun `weight of 20 and 400 kg are valid boundaries`() = runTest {
        assertThat(invoke(ProfileEdit.Weight("20", WeightUnit.KG))).isInstanceOf(AppResult.Success::class.java)
        assertThat(invoke(ProfileEdit.Weight("400", WeightUnit.KG))).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `weight above 400kg is BODY_WEIGHT_RANGE`() = runTest {
        val result = invoke(ProfileEdit.Weight("400.01", WeightUnit.KG))
        assertThat(result).isEqualTo(AppResult.Failure(AppError.Validation(ValidationReason.BODY_WEIGHT_RANGE)))
    }

    @Test
    fun `non-numeric weight is WEIGHT_INVALID`() = runTest {
        val result = invoke(ProfileEdit.Weight("abc", WeightUnit.KG))
        assertThat(result).isEqualTo(AppResult.Failure(AppError.Validation(ValidationReason.WEIGHT_INVALID)))
    }

    @Test
    fun `100 lb converts to 45,36 kg`() = runTest {
        val result = invoke(ProfileEdit.Weight("100", WeightUnit.LB)) as AppResult.Success
        assertThat(result.value.weightKg).isWithin(0.01).of(45.36)
    }

    @Test
    fun `an empty weight clears the field`() = runTest {
        val result = invoke(ProfileEdit.Weight("", WeightUnit.KG)) as AppResult.Success
        assertThat(result.value.weightKg).isNull()
    }

    // --- Height ---

    @Test
    fun `height boundaries 99,100,250,251`() = runTest {
        assertThat(invoke(ProfileEdit.Height("99"))).isEqualTo(AppResult.Failure(AppError.Validation(ValidationReason.HEIGHT_RANGE)))
        assertThat(invoke(ProfileEdit.Height("100"))).isInstanceOf(AppResult.Success::class.java)
        assertThat(invoke(ProfileEdit.Height("250"))).isInstanceOf(AppResult.Success::class.java)
        assertThat(invoke(ProfileEdit.Height("251"))).isEqualTo(AppResult.Failure(AppError.Validation(ValidationReason.HEIGHT_RANGE)))
    }

    @Test
    fun `a non-integer height is HEIGHT_RANGE`() = runTest {
        val result = invoke(ProfileEdit.Height("1.8"))
        assertThat(result).isEqualTo(AppResult.Failure(AppError.Validation(ValidationReason.HEIGHT_RANGE)))
    }

    // --- Birth date ---

    @Test
    fun `an invalid calendar date is BIRTH_DATE_INVALID`() = runTest {
        assertThat(invoke(ProfileEdit.BirthDate("31/02/2000"))).isEqualTo(AppResult.Failure(AppError.Validation(ValidationReason.BIRTH_DATE_INVALID)))
        assertThat(invoke(ProfileEdit.BirthDate("29/02/2001"))).isEqualTo(AppResult.Failure(AppError.Validation(ValidationReason.BIRTH_DATE_INVALID)))
    }

    @Test
    fun `Feb 29 on a leap year is valid`() = runTest {
        // Now is 2026-01-15, so a 2000-02-29 birth date gives an age of 25 - within range.
        val result = invoke(ProfileEdit.BirthDate("29/02/2000"))
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `a future birth date is BIRTH_DATE_INVALID`() = runTest {
        val result = invoke(ProfileEdit.BirthDate("01/01/2027"))
        assertThat(result).isEqualTo(AppResult.Failure(AppError.Validation(ValidationReason.BIRTH_DATE_INVALID)))
    }

    @Test
    fun `age 9 and age 101 are AGE_RANGE`() = runTest {
        // "now" is 2026-01-15.
        assertThat(invoke(ProfileEdit.BirthDate("15/06/2016"))).isEqualTo(AppResult.Failure(AppError.Validation(ValidationReason.AGE_RANGE))) // turned 9 on 2025-06-15
        assertThat(invoke(ProfileEdit.BirthDate("10/01/1925"))).isEqualTo(AppResult.Failure(AppError.Validation(ValidationReason.AGE_RANGE))) // turned 101 on 2026-01-10
    }

    @Test
    fun `an empty birth date clears the field`() = runTest {
        val result = invoke(ProfileEdit.BirthDate("")) as AppResult.Success
        assertThat(result.value.birthDate).isNull()
    }

    // --- Goal ---

    @Test
    fun `Goal sends the apiValue`() = runTest {
        invoke(ProfileEdit.Goal(ProfileGoal.LOSE_WEIGHT))

        assertThat(profileRepository.updatePhysicalCalls.single().goal).isEqualTo("lose_weight")
    }

    @Test
    fun `Goal null clears it`() = runTest {
        invoke(ProfileEdit.Goal(null))

        assertThat(profileRepository.updatePhysicalCalls.single().goal).isNull()
    }

    // --- rawGoal passthrough, validation short-circuit, repository errors, returned Profile ---

    @Test
    fun `editing the weight preserves an unrecognized rawGoal`() = runTest {
        invoke(ProfileEdit.Weight("75", WeightUnit.KG))

        assertThat(profileRepository.updatePhysicalCalls.single().goal).isEqualTo("strength")
    }

    @Test
    fun `an invalid edit never calls the repository`() = runTest {
        invoke(ProfileEdit.Weight("abc", WeightUnit.KG))

        assertThat(profileRepository.updatePhysicalCalls).isEmpty()
    }

    @Test
    fun `a repository error is propagated`() = runTest {
        profileRepository.updatePhysicalResult = AppResult.Failure(AppError.Network)

        val result = invoke(ProfileEdit.Weight("75", WeightUnit.KG))

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Network))
    }

    @Test
    fun `returns the updated Profile`() = runTest {
        val result = invoke(ProfileEdit.Weight("75", WeightUnit.KG)) as AppResult.Success

        assertThat(result.value.weightKg).isEqualTo(75.0)
        assertThat(result.value.heightCm).isEqualTo(current.heightCm)
        assertThat(result.value.rawGoal).isEqualTo(current.rawGoal)
    }
}
