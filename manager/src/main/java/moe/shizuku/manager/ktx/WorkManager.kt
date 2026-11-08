package moe.shizuku.manager.ktx

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import moe.shizuku.manager.utils.Diag

/**
 * Makes WorkManager usable in this process, and says whether it is.
 *
 * This app is direct boot aware, so a process can start before the first unlock. In such a
 * process the provider that normally sets WorkManager up was never installed, and it is not
 * installed later when the unlock comes: asking for WorkManager there throws for good rather than
 * for now. Every start that goes through the queue, and every cancel of one, asks here first.
 *
 * False means there is still none. A caller that queues work has to say so and stop; a caller that
 * is only tidying up has nothing to tidy and can carry on.
 */
fun Context.ensureWorkManager(): Boolean {
    if (runCatching { WorkManager.getInstance(this) }.isSuccess) {
        return true
    }

    return runCatching {
        // Only after the unlock: before it, the storage its database lives in cannot be read, so
        // setting it up would fail for a reason the next attempt would not have either.
        check(isUserUnlockedCompat()) { "the user has not unlocked yet" }
        WorkManager.initialize(this, Configuration.Builder().build())
        WorkManager.getInstance(this)
        true
    }.getOrElse {
        Diag.warn("WorkManager", "WorkManager is not available in this process", it)
        false
    }
}
