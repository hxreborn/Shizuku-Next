package moe.shizuku.manager.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.manage.LabSwitch
import moe.shizuku.manager.manage.SwitchIcon

/**
 * One whole-feature switch as a home screen widget.
 *
 * The subclasses at the bottom are only which switch they are: the card, the reading, the click,
 * and the redraw when the matching tile changes something are here once, so twelve widgets cannot
 * drift into twelve behaviours.
 *
 * The state is read when the widget is drawn, never kept, so what it shows is the feature's state
 * rather than the last thing this app did with it. Off is drawn as the icon dimmed and the word for
 * it; whatever the feature keeps is untouched, and comes back when it is switched on again.
 *
 * Everything is answered asynchronously. A widget provider that did its work on the main thread
 * would be a launcher waiting on a shell, and [goAsync] is how a broadcast says it is not finished
 * yet without holding the thread it arrived on.
 */
abstract class LabWidgetProvider(private val switch: LabSwitch) : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        draw(context, appWidgetIds)
    }

    /** A resize is a redraw: the card and the icon-only tile are two layouts, not one squeezed. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        draw(context, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> toggle(context)
            LabSwitch.ACTION_CHANGED ->
                if (intent.getStringExtra(LabSwitch.EXTRA_SWITCH) == switch.name) {
                    draw(context, widgetIds(context))
                } else {
                    super.onReceive(context, intent)
                }

            else -> super.onReceive(context, intent)
        }
    }

    /** Switches the feature, then draws whatever it ended up as. */
    private fun toggle(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // A state that cannot be read is not one to invert: the click does nothing, and
                // the card keeps saying that it does not know.
                val current = readState()
                if (current != null) {
                    switch.apply(context, !current)

                    val state = readState()
                    drawNow(context, widgetIds(context), state)
                    switch.announce(context, state)
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** Reads the state off the main thread, then draws every instance of this widget. */
    private fun draw(context: Context, ids: IntArray) {
        if (ids.isEmpty()) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                drawNow(context, ids, readState())
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * The state, waited for a moment when it cannot be read yet.
     *
     * A process that has just been started - after an update, or after the launcher reclaimed it -
     * has no binder in it for the first moment, and the server hands one over as soon as it sees
     * the process. Saying Unavailable during that window would be true for a second and wrong for
     * the rest of the day.
     */
    private fun readState(): Boolean? {
        repeat(3) { attempt ->
            switch.isOn()?.let { return it }
            if (attempt < 2) Thread.sleep(500)
        }
        return null
    }

    private fun drawNow(context: Context, ids: IntArray, on: Boolean?) {
        if (ids.isEmpty()) return

        val manager = AppWidgetManager.getInstance(context)
        for (id in ids) {
            manager.updateAppWidget(id, views(context, on, compact = isCompact(context, id)))
        }
    }

    /**
     * Whether this instance has been shrunk past the size the row needs.
     *
     * A launcher reports the bounds an instance may occupy; when the narrow one no longer fits the
     * title and the state beside the icon, the card is the wrong shape and the small layout - the
     * mark alone - is the right one. Asked of the instance rather than of the screen, because two
     * instances of the same switch can be different sizes.
     */
    private fun isCompact(context: Context, id: Int): Boolean {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
        return isCompact(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH))
    }

    private fun views(context: Context, on: Boolean?, compact: Boolean): RemoteViews {
        val views = RemoteViews(
            context.packageName,
            if (compact) R.layout.widget_lab_switch_compact else R.layout.widget_lab_switch
        )
        val look = LabSwitch.Look.of(on)

        if (!compact) {
            views.setTextViewText(R.id.widget_title, context.getString(switch.label))
            views.setTextViewText(R.id.widget_state, context.getString(look.label))
        }

        // The very icon the Labs screen draws, at the size the layout reserves for it.
        val iconDp = if (compact) 28 else 26
        val size = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            iconDp.toFloat(),
            context.resources.displayMetrics
        ).toInt().coerceAtLeast(1)
        views.setImageViewBitmap(R.id.widget_icon, SwitchIcon.bitmap(switch.image, size))

        // Drawn rather than described: the same icon, dimmed when the feature is off, so the two
        // states are told apart at a glance from across the room.
        //
        // The colour is the layout's job, not this one's. A tint applied here is resolved when the
        // widget is drawn and then stored in the RemoteViews, so switching the system theme left
        // a dark icon on a card that had just turned dark with it - the shield vanished. The
        // layout carries `android:tint="@color/widget_icon_tint"`, which the launcher resolves
        // again on every inflation, and only the alpha is set from here.
        views.setFloat(R.id.widget_icon, "setAlpha", if (look == LabSwitch.Look.ON) 1f else 0.42f)

        views.setOnClickPendingIntent(R.id.widget_root, toggleIntent(context))
        return views
    }

    private fun toggleIntent(context: Context): PendingIntent {
        val intent = Intent(context, this::class.java).setAction(ACTION_TOGGLE)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun widgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, this::class.java))

    companion object {
        /** A click, from any instance. */
        const val ACTION_TOGGLE = "moe.shizuku.manager.action.LAB_WIDGET_TOGGLE"

        /**
         * Below this the row cannot hold the icon and its name: a one-cell widget is about 70dp
         * wide, and two cells are about 140dp, so the line sits between them.
         *
         * The height is not asked: a one-row widget is about 40dp tall whether it is one cell wide
         * or two, so it says nothing about which layout fits.
         */
        internal const val COMPACT_WIDTH_DP = 100

        /**
         * Whether a widget [widthDp] wide is the small, icon-only one.
         *
         * A launcher that has not reported a size answers zero, which is not a one-cell widget:
         * until it speaks, the card is the shape to draw.
         */
        internal fun isCompact(widthDp: Int): Boolean = widthDp in 1 until COMPACT_WIDTH_DP

    }
}

// One class per widget because an AppWidgetProvider is a component too: the receiver the launcher
// binds is the one the user placed, and it has to know which switch it is.

class ServiceWidgetProvider : LabWidgetProvider(LabSwitch.SERVICE)
class FirewallWidgetProvider : LabWidgetProvider(LabSwitch.FIREWALL)
class AutoStartWidgetProvider : LabWidgetProvider(LabSwitch.AUTOSTART)
class HidingWidgetProvider : LabWidgetProvider(LabSwitch.HIDING)
class DeveloperOptionsWidgetProvider : LabWidgetProvider(LabSwitch.DEVELOPER_OPTIONS)
class UsbDebuggingWidgetProvider : LabWidgetProvider(LabSwitch.USB_DEBUGGING)
class WirelessDebuggingWidgetProvider : LabWidgetProvider(LabSwitch.WIRELESS_DEBUGGING)
class HideDeveloperOptionsWidgetProvider : LabWidgetProvider(LabSwitch.HIDE_DEVELOPER_OPTIONS)
class HideUsbWidgetProvider : LabWidgetProvider(LabSwitch.HIDE_USB_DEBUGGING)
class HideWirelessWidgetProvider : LabWidgetProvider(LabSwitch.HIDE_WIRELESS_DEBUGGING)
class HideAccessibilityWidgetProvider : LabWidgetProvider(LabSwitch.HIDE_ACCESSIBILITY)
class HidePrivateDnsWidgetProvider : LabWidgetProvider(LabSwitch.HIDE_PRIVATE_DNS)
class HideVpnWidgetProvider : LabWidgetProvider(LabSwitch.HIDE_VPN)
class ForceDarkWidgetProvider : LabWidgetProvider(LabSwitch.FORCE_DARK)
class WatchdogWidgetProvider : LabWidgetProvider(LabSwitch.WATCHDOG)
