package com.elijah.fitness.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory cache of the current user's body metrics. Fed by
 * `RemoteAuthRepository` on login and by the Dashboard's
 * profile refresh (an existing session that pre-dates the height/BMI
 * feature won't have a locally-cached height until `/me` is called).
 *
 * - [bodyWeightKg] powers the calorie estimator (phase 1) and, together
 *   with [heightCm], the Home screen's BMI tile (post-phase 7).
 * - [heightCm] is `null` when the user hasn't set one yet — the BMI
 *   tile renders a "missing" state in that case.
 * - [weightIsFallback] is `true` while [bodyWeightKg] is the 70 kg
 *   default rather than the user's real weight (phase 8d). Calorie
 *   estimates still work; the New goal sheet shows a hint to add one.
 * - "Profile loaded" (phase 8d) is separate from the fallback flag: a
 *   loaded profile with no weight means "use 70 kg", while a profile
 *   that hasn't loaded yet (just signed out) means "weight unknown".
 *   [weightForSavingKgOrNull] tells the two apart.
 * - [loadedProfileWeight] (phase 8d) is the profile weight as last
 *   loaded. [ProfileWeightPersistence] saves it on the device and hands
 *   it back through [restoreSaved] at the next app start, so a workout
 *   saved offline right after a cold start still stores its calories.
 */
class UserSettings(
    initialBodyWeightKg: Double = DEFAULT_BODY_WEIGHT_KG,
    initialHeightCm: Double? = null,
) {

    private val _bodyWeightKg = MutableStateFlow(initialBodyWeightKg)
    val bodyWeightKg: StateFlow<Double> = _bodyWeightKg.asStateFlow()

    private val _heightCm = MutableStateFlow(initialHeightCm)
    val heightCm: StateFlow<Double?> = _heightCm.asStateFlow()

    /** True until a real weight arrives from the profile; true again after [reset]. */
    private val _weightIsFallback = MutableStateFlow(true)
    val weightIsFallback: StateFlow<Boolean> = _weightIsFallback.asStateFlow()

    /** True once a profile (or a real weight) has arrived; false again after [reset]. */
    @Volatile
    private var profileLoaded = false

    private val _loadedProfileWeight = MutableStateFlow<ProfileWeight?>(null)

    /**
     * The profile weight as last loaded (from the server, or restored
     * from the device). `null` until a profile has loaded, and again
     * after [reset].
     */
    val loadedProfileWeight: StateFlow<ProfileWeight?> = _loadedProfileWeight.asStateFlow()

    /** How many times [reset] has run; see [restoreSaved]. */
    @Volatile
    var resetCount: Int = 0
        private set

    /**
     * A loaded profile's weight. [weightKg] is `null` when that profile
     * has no weight (calories then use the 70 kg fallback).
     */
    data class ProfileWeight(val weightKg: Double?)

    /**
     * The weight a save path stores a workout's calories with:
     * - the user's profile weight;
     * - the 70 kg fallback when the loaded profile has no weight (the
     *   brief's rule: use 70 kg and flag it, see [weightIsFallback]);
     * - `null` while no profile has loaded yet, so a guess is never
     *   stored for a user who may well have a real weight. Those
     *   workouts keep no stored value and screens work it out later.
     */
    fun weightForSavingKgOrNull(): Double? =
        if (profileLoaded) _bodyWeightKg.value else null

    fun updateBodyWeightKg(kg: Double) {
        if (!isUsableWeight(kg)) return
        _bodyWeightKg.value = kg
        _weightIsFallback.value = false
        profileLoaded = true
        _loadedProfileWeight.value = ProfileWeight(kg)
    }

    fun updateHeightCm(cm: Double?) {
        _heightCm.value = cm
    }

    /**
     * Hydrate from a freshly-fetched profile. Missing weight falls back
     * to the default so the calorie estimator never divides by zero.
     */
    fun hydrateFrom(weightKg: Double?, heightCm: Double?) {
        val usable = weightKg?.takeIf(::isUsableWeight)
        _bodyWeightKg.value = usable ?: DEFAULT_BODY_WEIGHT_KG
        _weightIsFallback.value = usable == null
        _heightCm.value = heightCm
        profileLoaded = true
        _loadedProfileWeight.value = ProfileWeight(usable)
    }

    /**
     * App start: use the weight saved on this device by the last session
     * until a fresh profile loads. Ignored when a profile has already
     * loaded (it's newer), and when [reset] has run since the caller read
     * [resetCount] (the user signed out while the saved weight was being
     * read, so it belongs to an account that is gone).
     */
    fun restoreSaved(saved: ProfileWeight, expectedResetCount: Int) {
        if (profileLoaded || resetCount != expectedResetCount) return
        val usable = saved.weightKg?.takeIf(::isUsableWeight)
        _bodyWeightKg.value = usable ?: DEFAULT_BODY_WEIGHT_KG
        _weightIsFallback.value = usable == null
        profileLoaded = true
        _loadedProfileWeight.value = ProfileWeight(usable)
    }

    /** Back to the defaults (sign-out): no profile loaded until the next login. */
    fun reset() {
        _bodyWeightKg.value = DEFAULT_BODY_WEIGHT_KG
        _weightIsFallback.value = true
        _heightCm.value = null
        profileLoaded = false
        _loadedProfileWeight.value = null
        resetCount++
    }

    /** A zero, negative or NaN weight would break every calorie number. */
    private fun isUsableWeight(kg: Double): Boolean = kg.isFinite() && kg > 0.0

    companion object {
        const val DEFAULT_BODY_WEIGHT_KG: Double = 70.0
    }
}
