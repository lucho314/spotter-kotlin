package com.lucho314.spotter.feature.profile

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucho314.spotter.R
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.domain.calc.AgeCalculator
import com.lucho314.spotter.domain.model.Profile
import com.lucho314.spotter.domain.model.ProfileEdit
import com.lucho314.spotter.domain.model.ProfileGoal
import com.lucho314.spotter.domain.model.ProfileStats
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.repository.PreferencesRepository
import com.lucho314.spotter.domain.usecase.GetProfileOverviewUseCase
import com.lucho314.spotter.domain.usecase.SignOutRisk
import com.lucho314.spotter.domain.usecase.SignOutUseCase
import com.lucho314.spotter.domain.usecase.UpdateProfileUseCase
import com.lucho314.spotter.feature.common.toMessageRes
import com.lucho314.spotter.feature.common.toUserMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which physical field's edit dialog (if any) is open. */
enum class ProfileField { WEIGHT, HEIGHT, BIRTH_DATE, GOAL }

data class ProfileUiState(
    val displayName: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val loading: Boolean = true,
    @StringRes val loadErrorRes: Int? = null,
    val profile: Profile? = null,
    val age: Int? = null,
    val stats: ProfileStats? = null,
    /** `true` when the profile loaded but at least one of its stat counts failed - shown as "—". */
    val statsUnavailable: Boolean = false,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val editing: ProfileField? = null,
    @StringRes val editErrorRes: Int? = null,
    val savingEdit: Boolean = false,
    val signOutPrompt: SignOutRisk? = null,
    val signingOut: Boolean = false,
)

/** One-shot effects: snackbar messages (both a successful save and a sign-out failure). */
sealed interface ProfileEvent {
    data class Message(@StringRes val messageRes: Int) : ProfileEvent
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val getProfileOverviewUseCase: GetProfileOverviewUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val userId = authRepository.currentUser()?.id.orEmpty()

    private val _uiState = MutableStateFlow(
        authRepository.currentUser().let { user ->
            ProfileUiState(displayName = user?.displayName, email = user?.email, avatarUrl = user?.avatarUrl)
        },
    )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<ProfileEvent>(Channel.BUFFERED)
    val events: Flow<ProfileEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.weightUnit.collect { unit -> _uiState.update { it.copy(weightUnit = unit) } }
        }
        load()
    }

    fun retry() = load()

    private fun load() {
        _uiState.update { it.copy(loading = true, loadErrorRes = null) }
        viewModelScope.launch {
            when (val result = getProfileOverviewUseCase(userId)) {
                is AppResult.Success -> {
                    val overview = result.value
                    _uiState.update {
                        it.copy(
                            loading = false,
                            loadErrorRes = null,
                            profile = overview.profile,
                            age = ageOf(overview.profile),
                            stats = overview.stats,
                            statsUnavailable = overview.stats == null,
                            displayName = overview.profile.displayName,
                            avatarUrl = authRepository.currentUser()?.avatarUrl ?: overview.profile.avatarUrl,
                        )
                    }
                }
                is AppResult.Failure -> _uiState.update { it.copy(loading = false, loadErrorRes = result.error.toMessageRes()) }
            }
        }
    }

    private fun ageOf(profile: Profile): Int? =
        profile.birthDate?.let { AgeCalculator.age(it, timeProvider.now().atZone(timeProvider.zone()).toLocalDate()) }

    fun onEdit(field: ProfileField) {
        _uiState.update { it.copy(editing = field, editErrorRes = null) }
    }

    fun onEditDismiss() {
        _uiState.update { it.copy(editing = null, editErrorRes = null) }
    }

    fun onSaveWeight(text: String) = save(ProfileEdit.Weight(text, uiState.value.weightUnit))

    fun onSaveHeight(text: String) = save(ProfileEdit.Height(text))

    fun onSaveBirthDate(text: String) = save(ProfileEdit.BirthDate(text))

    fun onSaveGoal(goal: ProfileGoal?) = save(ProfileEdit.Goal(goal))

    private fun save(edit: ProfileEdit) {
        val state = _uiState.value
        if (state.savingEdit) return
        val profile = state.profile ?: return
        _uiState.update { it.copy(savingEdit = true, editErrorRes = null) }
        viewModelScope.launch {
            when (val result = updateProfileUseCase(userId, profile, edit)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(savingEdit = false, editing = null, profile = result.value, age = ageOf(result.value))
                    }
                    eventChannel.send(ProfileEvent.Message(R.string.profile_saved))
                }
                is AppResult.Failure -> _uiState.update { it.copy(savingEdit = false, editErrorRes = result.error.toUserMessageRes()) }
            }
        }
    }

    fun onWeightUnitChange(unit: WeightUnit) {
        viewModelScope.launch { preferencesRepository.setWeightUnit(unit) }
    }

    fun onSignOutClick() {
        viewModelScope.launch {
            val risk = signOutUseCase.risk(userId)
            _uiState.update { it.copy(signOutPrompt = risk) }
        }
    }

    fun onSignOutDismiss() {
        _uiState.update { it.copy(signOutPrompt = null) }
    }

    fun onSignOutConfirmed() {
        _uiState.update { it.copy(signOutPrompt = null, signingOut = true) }
        viewModelScope.launch {
            when (val result = signOutUseCase()) {
                is AppResult.Success -> _uiState.update { it.copy(signingOut = false) }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(signingOut = false) }
                    eventChannel.send(ProfileEvent.Message(result.error.toMessageRes()))
                }
            }
        }
    }
}
