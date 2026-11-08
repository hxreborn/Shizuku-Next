package moe.shizuku.manager.manage

import android.content.Context

/**
 * The whole autostart list as one switch: on means its apps are denied the op, off means every one
 * of them is allowed again and the list is kept for when it goes back on.
 *
 * This is the gate the autostart screen's own switch has, moved out of the screen so a tile and a
 * widget can work the same switch. The rule is the screen's, unchanged: off only reads as off once
 * every app on the list was actually allowed again, because reporting a list as disarmed while the
 * platform is still denying some of it is a switch that lies (issue #101). One that could not be
 * put back leaves the gate on, and the caller is told.
 */
object AutoStartGate {

    /** The key the gate's own record is kept under, which is the feature's name. */
    private const val KEY = "AUTOSTART"

    /** The op behind autostart: the blunter half of RUN_IN_BACKGROUND. */
    const val OP = "RUN_ANY_IN_BACKGROUND"

    fun isOn(): Boolean = BlockedGates.isEnabled(KEY)

    /**
     * Arms or disarms the list, answering whether it ended up in the state that was asked for.
     *
     * Called off the main thread: every app on the list is one shell call.
     */
    fun setOn(context: Context, on: Boolean): Boolean {
        if (on) {
            BlockedGates.kept(KEY).forEach { app ->
                PackageTools.setOpBlocked(context, app, OP, true)
            }
            BlockedGates.setEnabled(KEY, true)
            return true
        }

        val denied = PackageTools.readOpDeniedInstalled(OP)
        val stayed = denied.filterNot { app -> PackageTools.setOpBlocked(context, app, OP, false) }
        return if (stayed.isEmpty()) {
            BlockedGates.setKept(KEY, denied)
            BlockedGates.setEnabled(KEY, false)
            true
        } else {
            // Some app the platform would not let go of: the list is still in force, and saying
            // otherwise would be the same lie as before.
            BlockedGates.setEnabled(KEY, true)
            false
        }
    }
}
