package com.elijah.fitness.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory cache of the current user's body metrics. Fed by
 * `RemoteAuthRepository` on login/register and by the Dashboard's
 * profile refresh (an existing session that pre-dates the height/BMI
 * feature won't have a locally-cached height until `/me` is called).
 *
 * - [bodyWeightKg] powers the calorie estimator (phase 1) and, together
 *   with [heightCm], the Home screen's BMI tile (post-phase 7).
 * - [heightCm] is `null` when the user hasn't set one yet — the BMI
 *   tile renders a "missing" state in that case.
 */
class UserSettings(
    initialBodyWeightKg: Double = DEFAULT_BODY_WEIGHT_KG,
    initialHeightCm: Double? = null,
) {

    private val _bodyWeightKg = MutableStateFlow(initialBodyWeightKg)
    val bodyWeightKg: StateFlow<Double> = _bodyWeightKg.asStateFlow()

    private val _heightCm = MutableStateFlow(initialHeightCm)
    val heightCm: StateFlow<Double?> = _heightCm.asStateFlow()

    fun updateBodyWeightKg(kg: Double) {
        _bodyWeightKg.value = kg
    }

    fun updateHeightCm(cm: Double?) {
        _heightCm.value = cm
    }

    /**
     * Hydrate from a freshly-fetched profile. Missing weight falls back
     * to the default so the calorie estimator never divides by zero.
     */
    fun hydrateFrom(weightKg: Double?, heightCm: Double?) {
        _bodyWeightKg.value = weightKg ?: DEFAULT_BODY_WEIGHT_KG
        _heightCm.value = heightCm
    }

    companion object {
        const val DEFAULT_BODY_WEIGHT_KG: Double = 70.0
    }
}
