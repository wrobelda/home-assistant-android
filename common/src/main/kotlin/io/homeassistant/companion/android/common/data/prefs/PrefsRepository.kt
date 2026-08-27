package io.homeassistant.companion.android.common.data.prefs

import android.content.pm.ActivityInfo
import android.os.Parcelable
import io.homeassistant.companion.android.common.data.integration.ControlsAuthRequiredSetting
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.common.util.HAGesture
import kotlinx.coroutines.flow.Flow
import kotlinx.parcelize.Parcelize

/**
 * Screen orientation preference applied to the dashboard host activity.
 *
 * The [storageValue]s match the entries declared in the `pref_screen_orientation_option_values`
 * string-array used by the settings ListPreference, so values written by the legacy settings UI
 * still resolve to a typed enum here.
 */
enum class ScreenOrientation(val storageValue: String, val activityInfo: Int) {
    SYSTEM("system", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
    PORTRAIT("portrait", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    LANDSCAPE("landscape", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
    ;

    companion object {
        /** Returns the matching entry or [SYSTEM] when [value] is null or unknown. */
        fun fromStorageValue(value: String?): ScreenOrientation =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

/**
 * Idle behavior applied while the "Keep screen on" preference is enabled.
 *
 * The [storageValue]s match the entries declared in the `pref_keep_screen_on_idle_option_values`
 * string-array used by the settings ListPreference, so values written by the legacy settings UI
 * still resolve to a typed enum here.
 */
enum class KeepScreenOnIdleMode(val storageValue: String) {
    /** Keep the screen at its regular brightness while idle. */
    NONE("none"),

    /** Dim the screen to the system's dim level after a period of inactivity. */
    DIM("dim"),

    /**
     * Request the minimum screen brightness, behind a black scrim, after a period of inactivity.
     * The Android platform has no explicit API allowing apps to turn the display completely off
     * without having the system enter suspend mode, so `BRIGHTNESS_OVERRIDE_OFF` - defined as
     * setting the "lowest value" possible - is the best solution available. How dark the screen
     * ends up depends on the display: OLED panels go fully dark, while LCD backlights keep a
     * faint glow.
     */
    SCREEN_OFF("screen_off"),
    ;

    companion object {
        /** Returns the matching entry or [NONE] when [value] is null or unknown. */
        fun fromStorageValue(value: String?): KeepScreenOnIdleMode =
            entries.firstOrNull { it.storageValue == value } ?: NONE
    }
}

enum class NightModeTheme(val storageValue: String) {
    LIGHT("light"),
    DARK("dark"),

    @Deprecated("Kept for backwards compatibility see https://github.com/home-assistant/android/pull/2923")
    ANDROID("android"),
    SYSTEM("system"),
    ;

    companion object {
        fun fromStorageValue(value: String?): NightModeTheme? = entries.firstOrNull { it.storageValue == value }
    }
}

@Parcelize
data class AutoFavorite(val serverId: Int, val entityId: String) : Parcelable

/**
 * Holds the current zoom configuration for the WebView.
 *
 * @param zoomLevel Zoom level percentage (e.g. 100 for no zoom, 150 for 150%).
 * @param pinchToZoomEnabled Whether the user has enabled pinch-to-zoom.
 */
data class ZoomSettings(val zoomLevel: Int = DEFAULT_ZOOM_LEVEL, val pinchToZoomEnabled: Boolean = false)

private const val DEFAULT_ZOOM_LEVEL = 100

interface PrefsRepository {
    suspend fun getAppVersion(): String?

    suspend fun saveAppVersion(ver: String)

    suspend fun getCurrentNightModeTheme(): NightModeTheme?

    suspend fun saveNightModeTheme(nightModeTheme: NightModeTheme)

    suspend fun getCurrentLang(): String?

    suspend fun saveLang(lang: String)

    suspend fun getLocales(): String?

    suspend fun saveLocales(lang: String)

    suspend fun getControlsAuthRequired(): ControlsAuthRequiredSetting

    suspend fun setControlsAuthRequired(setting: ControlsAuthRequiredSetting)

    suspend fun getControlsEnableStructure(): Boolean

    suspend fun setControlsEnableStructure(enabled: Boolean)

    suspend fun getControlsAuthEntities(): List<String>

    suspend fun setControlsAuthEntities(entities: List<String>)

    suspend fun getControlsPanelServer(): Int?

    suspend fun setControlsPanelServer(serverId: Int)

    suspend fun getControlsPanelPath(): String?

    suspend fun setControlsPanelPath(path: String?)

    suspend fun isFullScreenEnabled(): Boolean

    suspend fun setFullScreenEnabled(enabled: Boolean)

    /** Emits the current fullscreen preference immediately on collection, then on every change. */
    suspend fun fullScreenEnabledFlow(): Flow<Boolean>

    suspend fun isKeepScreenOnEnabled(): Boolean

    suspend fun setKeepScreenOnEnabled(enabled: Boolean)

    /** Emits the current "Keep screen on" preference immediately on collection, then on every change. */
    suspend fun keepScreenOnFlow(): Flow<Boolean>

    /**
     * Returns the idle behavior used while "Keep screen on" is enabled. Falls back to
     * [KeepScreenOnIdleMode.NONE] when no value is stored or the stored value cannot be resolved.
     */
    suspend fun getKeepScreenOnIdleMode(): KeepScreenOnIdleMode

    suspend fun setKeepScreenOnIdleMode(mode: KeepScreenOnIdleMode)

    /** Emits the current [KeepScreenOnIdleMode] preference immediately on collection, then on every change. */
    suspend fun keepScreenOnIdleModeFlow(): Flow<KeepScreenOnIdleMode>

    /**
     * Returns the user's current screen orientation preference. Falls back to
     * [ScreenOrientation.SYSTEM] when no value is stored or the stored value cannot be resolved.
     */
    suspend fun getScreenOrientation(): ScreenOrientation

    suspend fun setScreenOrientation(orientation: ScreenOrientation)

    /** Emits the current [ScreenOrientation] preference immediately on collection, then on every change. */
    suspend fun screenOrientationFlow(): Flow<ScreenOrientation>

    suspend fun getPageZoomLevel(): Int

    suspend fun setPageZoomLevel(level: Int?)

    suspend fun isPinchToZoomEnabled(): Boolean

    suspend fun setPinchToZoomEnabled(enabled: Boolean)

    /** Emits the current [ZoomSettings] immediately on collection, then on every change. */
    suspend fun zoomSettingsFlow(): Flow<ZoomSettings>

    suspend fun isAutoPlayVideoEnabled(): Boolean

    /** Emits the current "Autoplay video" preference immediately on collection, then on every change. */
    suspend fun autoPlayVideoFlow(): Flow<Boolean>

    suspend fun setAutoPlayVideo(enabled: Boolean)

    suspend fun isAlwaysShowFirstViewOnAppStartEnabled(): Boolean

    suspend fun setAlwaysShowFirstViewOnAppStart(enabled: Boolean)

    suspend fun isWebViewDebugEnabled(): Boolean

    suspend fun setWebViewDebugEnabled(enabled: Boolean)

    suspend fun isCrashReporting(): Boolean

    suspend fun setCrashReporting(crashReportingEnabled: Boolean)

    suspend fun saveKeyAlias(alias: String)

    suspend fun getKeyAlias(): String?

    suspend fun getIgnoredSuggestions(): List<String>

    suspend fun setIgnoredSuggestions(ignored: List<String>)

    suspend fun getAutoFavorites(): List<AutoFavorite>

    suspend fun setAutoFavorites(favorites: List<AutoFavorite>)

    suspend fun addAutoFavorite(favorite: AutoFavorite)

    suspend fun isLocationHistoryEnabled(): Boolean

    suspend fun setLocationHistoryEnabled(enabled: Boolean)

    suspend fun getImprovPermissionDisplayedCount(): Int

    suspend fun addImprovPermissionDisplayedCount()

    suspend fun getGestureAction(gesture: HAGesture): GestureAction

    suspend fun setGestureAction(gesture: HAGesture, action: GestureAction)

    suspend fun isChangeLogPopupEnabled(): Boolean

    suspend fun setChangeLogPopupEnabled(enabled: Boolean)

    /**
     * Returns `true` when the app was updated since the changelog was last marked seen with
     * [markChangelogSeen].
     *
     * A fresh install is not an update: [currentVersionCode] is stored as the baseline to compare
     * future updates against, and `false` is returned.
     */
    suspend fun wasAppUpdatedSinceChangelogSeen(currentVersionCode: Int): Boolean

    /** Stores [currentVersionCode] as the last app version whose changelog was seen. */
    suspend fun markChangelogSeen(currentVersionCode: Int)

    /** Clean up any app-level preferences that might reference servers */
    suspend fun removeServer(serverId: Int)

    suspend fun showPrivacyHint(): Boolean

    suspend fun setShowPrivacyHint(showPrivacyHint: Boolean)

    suspend fun isWakeWordEnabled(): Boolean

    suspend fun setWakeWordEnabled(enabled: Boolean)

    suspend fun getSelectedWakeWord(): String?

    suspend fun setSelectedWakeWord(wakeWord: String)

    suspend fun addAllowedTag(tag: String)

    suspend fun getAllowedTags(): Set<String>

    suspend fun clearAllowedTags()
}
