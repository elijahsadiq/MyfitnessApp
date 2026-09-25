package com.elijah.fitness.util

/**
 * The unit system the user prefers to enter height + weight in. Storage
 * on the server is always metric (kg / cm) — this enum only affects
 * the register form's input fields and the display units.
 */
enum class UnitSystem { METRIC, IMPERIAL }

/**
 * Pure Kotlin body-metrics utilities: unit conversion + BMI calculation.
 * No Android imports, so every branch is unit-testable on the JVM.
 *
 * ### Conversion constants
 * WHO / NIST-standard factors:
 *   1 kg = 2.20462262 lb
 *   1 in = 2.54 cm
 *
 * ### BMI
 * Formula: `weightKg / (heightCm / 100)^2`. The category cutoffs match
 * the WHO adult classifications (Underweight <18.5, Normal 18.5–24.9,
 * Overweight 25–29.9, Obese ≥30). These are not medical advice — see
 * the note in [BmiCategory].
 */
object BodyMetrics {

    // ---- Conversion factors --------------------------------------------------

    private const val KG_PER_LB: Double = 0.45359237
    private const val CM_PER_IN: Double = 2.54

    fun poundsToKilograms(lb: Double): Double = lb * KG_PER_LB
    fun kilogramsToPounds(kg: Double): Double = kg / KG_PER_LB

    fun inchesToCentimeters(inches: Double): Double = inches * CM_PER_IN
    fun centimetersToInches(cm: Double): Double = cm / CM_PER_IN

    // ---- BMI -----------------------------------------------------------------

    /**
     * Body Mass Index in `kg/m^2`. Returns `null` when either input is
     * missing, non-positive, or would produce a divide-by-zero.
     */
    fun bmi(weightKg: Double?, heightCm: Double?): Double? {
        if (weightKg == null || heightCm == null) return null
        if (weightKg <= 0.0 || heightCm <= 0.0) return null
        val heightM = heightCm / 100.0
        return weightKg / (heightM * heightM)
    }

    /**
     * Categorises a BMI value using the WHO adult ranges. Returns `null`
     * when [bmi] is `null` — so a caller can pipe the result of [bmi]
     * straight in without a second null-check.
     */
    fun bmiCategory(bmi: Double?): BmiCategory? {
        val v = bmi ?: return null
        return when {
            v < 18.5 -> BmiCategory.UNDERWEIGHT
            v < 25.0 -> BmiCategory.NORMAL
            v < 30.0 -> BmiCategory.OVERWEIGHT
            else -> BmiCategory.OBESE
        }
    }
}

/**
 * WHO adult BMI classifications. Kept as an enum (rather than raw
 * strings) so the UI layer switches on it exhaustively and future locales
 * add translations in one place. Not medical advice — the category is
 * only shown as a rough guidance next to the number.
 */
enum class BmiCategory { UNDERWEIGHT, NORMAL, OVERWEIGHT, OBESE }
