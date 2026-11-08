package moe.shizuku.manager.service

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * The claim that only one restart runs at a time, and the way out of one that was never released.
 *
 * A claim that is taken and then never given back stops every later restart in silence: each one
 * was told that another was already running, while none was. That happened on the device - the
 * pass that takes it threw before it reached the code that releases it, and the server stayed down
 * from the evening until the app was force stopped the next morning.
 *
 * So a claim older than [staleAfterMs] is not a restart in flight, it is an abandoned one, and the
 * next pass takes it over. Longer than the longest a restart can honestly take.
 */
class RestartClaim(private val staleAfterMs: Long) {

    private val taken = AtomicBoolean(false)
    private val takenAt = AtomicLong(0L)

    enum class Outcome {
        /** Nobody held it, and this caller does now. */
        TAKEN,

        /** Somebody holds it, and recently enough that it may really be running. */
        BUSY,

        /** It was held for longer than a restart can take, so this caller holds it now. */
        TAKEN_OVER_STALE,
    }

    /** Takes the claim for [now] (an elapsed-realtime stamp), or says why it could not. */
    fun take(now: Long): Outcome {
        if (taken.compareAndSet(false, true)) {
            takenAt.set(now)
            return Outcome.TAKEN
        }
        if (now - takenAt.get() < staleAfterMs) {
            return Outcome.BUSY
        }
        takenAt.set(now)
        return Outcome.TAKEN_OVER_STALE
    }

    /** Gives it back, whatever became of the restart. */
    fun release() {
        taken.set(false)
    }
}
