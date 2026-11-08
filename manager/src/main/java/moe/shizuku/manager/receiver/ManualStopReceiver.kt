package moe.shizuku.manager.receiver

import android.content.Context
import android.content.Intent
import moe.shizuku.manager.start.stopShizuku

class ManualStopReceiver : AuthenticatedReceiver() {
    override fun onAuthenticated(context: Context, intent: Intent) {
        // The runtime package: see [ManualStartReceiver] for why the build-time one is wrong here.
        val applicationId = context.packageName
        if (intent.action != "${applicationId}.STOP") return

        // The same stop a tile or a widget makes: the rules about the watchdog and the queued
        // start live in one place, so a stop from outside the app cannot mean something else.
        stopShizuku(context)
    }
}
