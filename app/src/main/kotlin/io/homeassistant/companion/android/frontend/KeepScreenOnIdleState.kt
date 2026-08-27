package io.homeassistant.companion.android.frontend

import android.content.Context
import android.content.res.Resources
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import io.homeassistant.companion.android.common.data.prefs.KeepScreenOnIdleMode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/** Idle delay used when the system screen off timeout cannot be read. */
private const val DEFAULT_IDLE_TIMEOUT_MILLIS = 60_000L

/** Lower bound for the idle delay, guarding against a zero or negative system timeout. */
private const val MIN_IDLE_TIMEOUT_MILLIS = 5_000L

/** Dim brightness used when the system dim level cannot be read, matching the AOSP default. */
private const val DEFAULT_DIM_BRIGHTNESS = 0.05f

/**
 * Screen state for the "When idle while keeping screen on" preference, created by
 * [rememberKeepScreenOnIdleState].
 *
 * @param inputModifier Applied to the container of the dashboard so every gesture inside it
 * resets the idle timer. When waking from [KeepScreenOnIdleMode.SCREEN_OFF], the waking tap
 * itself is inhibited, so it cannot interfere with dashboard controls. Waking from
 * [KeepScreenOnIdleMode.DIM] lets the tap pass through, matching how the system's own dim state
 * delivers touches.
 * @param showBlackScrim Whether a black scrim should cover the dashboard: how dark the
 * [KeepScreenOnIdleMode.SCREEN_OFF] request ends up depends on the display (OLED panels go fully
 * dark, while LCD backlights keep a faint glow), so the scrim keeps the content unreadable
 * either way.
 */
internal class KeepScreenOnIdleState(val inputModifier: Modifier, val showBlackScrim: Boolean)

/**
 * Tracks user inactivity and lowers the window brightness while the dashboard is idle.
 *
 * Active only when the "Keep screen on" preference is [enabled] and [mode] is not
 * [KeepScreenOnIdleMode.NONE]: the screen is guaranteed to stay on, so this replaces the
 * dimming the system would have offered if it were allowed to time out. When no touch arrives
 * for the duration of the screen off timeout, the window brightness is lowered to the system dim
 * level ([KeepScreenOnIdleMode.DIM]) or to [WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF]
 * ([KeepScreenOnIdleMode.SCREEN_OFF]). Any tap removes the override, making the brightness
 * return to the normal level decided by the system, manual or automatic.
 */
@Composable
internal fun rememberKeepScreenOnIdleState(enabled: Boolean, mode: KeepScreenOnIdleMode): KeepScreenOnIdleState {
    val active = enabled && mode != KeepScreenOnIdleMode.NONE

    var interactionCount by remember { mutableIntStateOf(0) }
    var isIdle by remember { mutableStateOf(false) }

    val idleTimeout by rememberSystemScreenOffTimeout()

    LaunchedEffect(active, interactionCount, idleTimeout) {
        isIdle = false
        if (active) {
            delay(idleTimeout)
            isIdle = true
        }
    }

    IdleBrightnessEffect(idle = active && isIdle, mode = mode)

    // Read through rememberUpdatedState so the pointer loop below sees the current values without
    // restarting, which would drop gestures that are in progress.
    val currentActive by rememberUpdatedState(active)
    val currentSwallowWake by rememberUpdatedState(active && isIdle && mode == KeepScreenOnIdleMode.SCREEN_OFF)

    val inputModifier = if (active) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                // The Initial pass sees every gesture before the dashboard content does.
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (!currentActive) return@awaitEachGesture
                val swallow = currentSwallowWake
                interactionCount++
                if (swallow) {
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            }
        }
    } else {
        Modifier
    }

    return KeepScreenOnIdleState(
        inputModifier = inputModifier,
        showBlackScrim = active && isIdle && mode == KeepScreenOnIdleMode.SCREEN_OFF,
    )
}

/**
 * Observes the system screen off timeout setting (Display > Screen timeout) and exposes it as
 * the idle delay. That setting normally decides how long after the last touch the screen turns
 * off, but it has no effect while "Keep screen on" is enabled, because the screen never times
 * out. This feature gives it its job back: the same period of inactivity that would have turned
 * the screen off now starts the idle dimming instead, so the delay the user picked keeps its
 * meaning.
 */
@Composable
private fun rememberSystemScreenOffTimeout(): State<Duration> {
    val context = LocalContext.current
    return produceState(initialValue = readSystemScreenOffTimeout(context), context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                value = readSystemScreenOffTimeout(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_OFF_TIMEOUT),
            false,
            observer,
        )
        awaitDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
}

private fun readSystemScreenOffTimeout(context: Context): Duration = Settings.System.getLong(
    context.contentResolver,
    Settings.System.SCREEN_OFF_TIMEOUT,
    DEFAULT_IDLE_TIMEOUT_MILLIS,
).coerceAtLeast(MIN_IDLE_TIMEOUT_MILLIS).milliseconds

/**
 * Overrides the hosting window's brightness while [idle], with the value selected by [mode], and
 * removes the override when activity resumes or the frontend leaves composition, handing
 * brightness control back to the system.
 */
@Composable
private fun IdleBrightnessEffect(idle: Boolean, mode: KeepScreenOnIdleMode) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity, idle, mode) {
        if (!idle) {
            onDispose {}
        } else {
            val window = activity.window
            val previous = window.attributes.screenBrightness
            window.attributes = window.attributes.also {
                it.screenBrightness = when (mode) {
                    KeepScreenOnIdleMode.DIM -> systemDimBrightness()
                    KeepScreenOnIdleMode.SCREEN_OFF -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
                    KeepScreenOnIdleMode.NONE -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
            onDispose {
                window.attributes = window.attributes.also { it.screenBrightness = previous }
            }
        }
    }
}

/**
 * Returns the brightness the system itself uses for its screen dim state, so dimming matches the
 * stock look of the device instead of an app-chosen value.
 *
 * The value comes from the framework's `config_screenBrightnessDimFloat` resource, falling back
 * to the legacy integer `config_screenBrightnessDim` on older builds and to
 * [DEFAULT_DIM_BRIGHTNESS] when neither can be read.
 */
private fun systemDimBrightness(): Float {
    val resources = Resources.getSystem()

    val floatId = resources.getIdentifier("config_screenBrightnessDimFloat", "dimen", "android")
    if (floatId != 0) {
        runCatching {
            val value = TypedValue()
            resources.getValue(floatId, value, true)
            return value.float
        }
    }

    val intId = resources.getIdentifier("config_screenBrightnessDim", "integer", "android")
    if (intId != 0) {
        runCatching {
            return resources.getInteger(intId) / 255f
        }
    }

    return DEFAULT_DIM_BRIGHTNESS
}
