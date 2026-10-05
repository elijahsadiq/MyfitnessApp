package com.elijah.fitness.tracking

import java.time.Instant

/**
 * One accepted GPS fix within a tracking session. Pure Kotlin — the Android
 * `Location` object is deliberately kept at the engine boundary so this
 * type can be built, replayed, and asserted on in unit tests.
 *
 * @property seq zero-based position within the session; monotonic and gap-less.
 * @property latitude WGS 84 degrees, −90..90.
 * @property longitude WGS 84 degrees, −180..180.
 * @property elapsedMs ms since the tracking session's `startedAt`. Monotonic;
 *   pauses hold this value steady, which is why "elapsed" is session-time
 *   rather than wall-clock-time.
 * @property accuracyM horizontal accuracy in metres as reported by the OS,
 *   or `null` if the source didn't provide one. Points above
 *   [TrackingConstants.ACCURACY_THRESHOLD_M] are filtered **before** they
 *   reach this type, so a `TrackPoint` is always "accepted".
 * @property recordedAt wall-clock timestamp at the moment of the fix.
 */
data class TrackPoint(
    val seq: Int,
    val latitude: Double,
    val longitude: Double,
    val elapsedMs: Long,
    val accuracyM: Double?,
    val recordedAt: Instant,
)
