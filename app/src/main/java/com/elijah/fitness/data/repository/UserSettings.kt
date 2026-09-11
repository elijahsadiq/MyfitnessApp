package com.elijah.fitness.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User preferences that feed calorie calculations and future goal logic.
 * Phase 1 exposes just the body weight; the Profile screen (Step 5) reads
 * it and later phases will let the user edit it and persist via DataStore.
 */
class UserSettings(initialBodyWeightKg: Double = DEFAULT_BODY_WEIGHT_KG) {

    private val _bodyWeightKg = MutableStateFlow(initialBodyWeightKg)
    val bodyWeightKg: StateFlow<Double> = _bodyWeightKg.asStateFlow()

    fun updateBodyWeightKg(kg: Double) {
        _bodyWeightKg.value = kg
    }

    companion object {
        const val DEFAULT_BODY_WEIGHT_KG: Double = 70.0
    }
}
