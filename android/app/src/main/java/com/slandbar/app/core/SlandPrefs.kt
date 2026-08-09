package com.slandbar.app.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.slandbar.app.core.model.BarSize
import com.slandbar.app.core.model.HapticProfile
import com.slandbar.app.core.model.Settings
import com.slandbar.app.core.model.ShortcutAction
import com.slandbar.app.core.model.ShortcutItem
import com.slandbar.app.core.model.ThemeMode
import com.slandbar.app.core.model.WidgetId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "slandbar_settings")

/**
 * Camada única de persistência do app (Jetpack DataStore + Gson).
 * Toda configuração do usuário vive aqui e é observada em tempo real
 * pela tela de configurações e pelo serviço de overlay.
 */
class SlandPrefs(private val context: Context) {

    private val gson = Gson()
    private val shortcutsType = object : TypeToken<List<ShortcutItem>>() {}.type
    private val widgetsType = object : TypeToken<List<WidgetId>>() {}.type

    private object Keys {
        val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val AUTO_START = booleanPreferencesKey("auto_start")
        val AUTO_HIDE = booleanPreferencesKey("auto_hide")
        val AUTO_HIDE_SECONDS = intPreferencesKey("auto_hide_seconds")
        val BAR_SIZE = stringPreferencesKey("bar_size")
        val OPACITY = floatPreferencesKey("opacity")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val ACCENT_HEX = stringPreferencesKey("accent_hex")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val HAPTIC_PROFILE = stringPreferencesKey("haptic_profile")
        val GESTURE_TAP = stringPreferencesKey("gesture_tap")
        val GESTURE_DOUBLE = stringPreferencesKey("gesture_double")
        val GESTURE_LONG = stringPreferencesKey("gesture_long")
        val GESTURE_SWIPE = stringPreferencesKey("gesture_swipe")
        val SHORTCUTS_JSON = stringPreferencesKey("shortcuts_json")
        val WIDGETS_JSON = stringPreferencesKey("widgets_json")
        val PREMIUM = booleanPreferencesKey("premium")
        val BAR_X = intPreferencesKey("bar_x")
        val BAR_Y = intPreferencesKey("bar_y")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            overlayEnabled = p[Keys.OVERLAY_ENABLED] ?: false,
            autoStart = p[Keys.AUTO_START] ?: false,
            autoHide = p[Keys.AUTO_HIDE] ?: true,
            autoHideSeconds = p[Keys.AUTO_HIDE_SECONDS] ?: 12,
            barSize = BarSize.fromId(p[Keys.BAR_SIZE] ?: "MEDIUM"),
            opacity = p[Keys.OPACITY] ?: 1f,
            themeMode = ThemeMode.fromId(p[Keys.THEME_MODE] ?: "system"),
            useDynamicColors = p[Keys.DYNAMIC_COLORS] ?: true,
            accentHex = p[Keys.ACCENT_HEX],
            hapticsEnabled = p[Keys.HAPTICS_ENABLED] ?: true,
            hapticProfile = HapticProfile.fromId(p[Keys.HAPTIC_PROFILE] ?: "soft"),
            gestureTap = ShortcutAction.fromId(p[Keys.GESTURE_TAP] ?: "expand"),
            gestureDoubleTap = ShortcutAction.fromId(p[Keys.GESTURE_DOUBLE] ?: "none"),
            gestureLongPress = ShortcutAction.fromId(p[Keys.GESTURE_LONG] ?: "flashlight"),
            gestureSwipeUp = ShortcutAction.fromId(p[Keys.GESTURE_SWIPE] ?: "expand"),
            shortcuts = parseShortcuts(p[Keys.SHORTCUTS_JSON]),
            widgets = parseWidgets(p[Keys.WIDGETS_JSON]),
            premium = p[Keys.PREMIUM] ?: false
        )
    }

    suspend fun setOverlayEnabled(value: Boolean) = edit { it[Keys.OVERLAY_ENABLED] = value }
    suspend fun setAutoStart(value: Boolean) = edit { it[Keys.AUTO_START] = value }
    suspend fun setAutoHide(value: Boolean) = edit { it[Keys.AUTO_HIDE] = value }
    suspend fun setAutoHideSeconds(value: Int) = edit { it[Keys.AUTO_HIDE_SECONDS] = value }
    suspend fun setBarSize(value: BarSize) = edit { it[Keys.BAR_SIZE] = value.name }
    suspend fun setOpacity(value: Float) = edit { it[Keys.OPACITY] = value }
    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.THEME_MODE] = value.id }
    suspend fun setDynamicColors(value: Boolean) = edit { it[Keys.DYNAMIC_COLORS] = value }
    suspend fun setAccentHex(value: String?) = edit { it[Keys.ACCENT_HEX] = value }
    suspend fun setHapticsEnabled(value: Boolean) = edit { it[Keys.HAPTICS_ENABLED] = value }
    suspend fun setHapticProfile(value: HapticProfile) = edit { it[Keys.HAPTIC_PROFILE] = value.id }
    suspend fun setGesture(gesture: com.slandbar.app.core.model.GestureType, action: ShortcutAction) = edit {
        val key = when (gesture) {
            com.slandbar.app.core.model.GestureType.TAP -> Keys.GESTURE_TAP
            com.slandbar.app.core.model.GestureType.DOUBLE_TAP -> Keys.GESTURE_DOUBLE
            com.slandbar.app.core.model.GestureType.LONG_PRESS -> Keys.GESTURE_LONG
            com.slandbar.app.core.model.GestureType.SWIPE_UP -> Keys.GESTURE_SWIPE
        }
        it[key] = action.id
    }

    suspend fun setShortcuts(list: List<ShortcutItem>) = edit {
        it[Keys.SHORTCUTS_JSON] = gson.toJson(list)
    }

    suspend fun setWidgets(list: List<WidgetId>) = edit {
        it[Keys.WIDGETS_JSON] = gson.toJson(list)
    }

    suspend fun setPremium(value: Boolean) = edit { it[Keys.PREMIUM] = value }

    /** Posição da barra (gravada para restaurar após reiniciar o serviço). */
    suspend fun setBarPosition(x: Int, y: Int) = edit {
        it[Keys.BAR_X] = x
        it[Keys.BAR_Y] = y
    }

    suspend fun barPosition(): Pair<Int, Int> {
        val d = context.dataStore.data.first()
        return (d[Keys.BAR_X] ?: 0) to (d[Keys.BAR_Y] ?: 0)
    }

    private fun parseShortcuts(json: String?): List<ShortcutItem> =
        if (json.isNullOrBlank()) emptyList()
        else try {
            gson.fromJson<List<ShortcutItem>>(json, shortcutsType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

    private fun parseWidgets(json: String?): List<WidgetId> =
        if (json.isNullOrBlank()) emptyList()
        else try {
            gson.fromJson<List<WidgetId>>(json, widgetsType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
}
