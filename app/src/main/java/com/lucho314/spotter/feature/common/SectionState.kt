package com.lucho314.spotter.feature.common

import androidx.annotation.StringRes

/** State of one independently-loaded UI section (e.g. a dashboard stat card). */
sealed interface SectionState<out T> {
    data object Loading : SectionState<Nothing>
    data class Loaded<T>(val value: T) : SectionState<T>
    data class Error(@StringRes val messageRes: Int) : SectionState<Nothing>
}
