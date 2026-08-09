package com.slandbar.app.core.model

/** Ações executáveis pelos atalhos, gestos e widgets. */
enum class ShortcutAction(val id: String) {
    NONE("none"),
    EXPAND("expand"),
    APP("app"),
    FLASHLIGHT("flashlight"),
    SCREENSHOT("screenshot"),
    CAMERA("camera"),
    DND("dnd"),
    ROTATION("rotation"),
    RINGER("ringer"),
    POWER("power"),
    NOTIFICATIONS("notifications"),
    SETTINGS("settings");

    companion object {
        fun fromId(id: String): ShortcutAction =
            entries.firstOrNull { it.id == id } ?: NONE
    }
}

/** Um atalho configurável pelo usuário (app ou ação rápida). */
data class ShortcutItem(
    val id: String,
    val label: String,
    val action: ShortcutAction,
    val packageName: String? = null
)

enum class GestureType { TAP, DOUBLE_TAP, LONG_PRESS, SWIPE_UP }

enum class BarSize(val scale: Float) {
    SMALL(0.8f),
    MEDIUM(1.0f),
    LARGE(1.25f);

    companion object {
        fun fromId(id: String): BarSize = entries.firstOrNull { it.name == id } ?: MEDIUM
    }
}

enum class HapticProfile(val id: String) {
    SOFT("soft"),
    MECHANICAL("mechanical"),
    DEEP("deep");

    companion object {
        fun fromId(id: String): HapticProfile = entries.firstOrNull { it.id == id } ?: SOFT
    }
}

enum class ThemeMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromId(id: String): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/** Widgets disponíveis no painel expandido. */
enum class WidgetId(val id: String) {
    CLOCK("clock"),
    TOGGLES("toggles"),
    SHORTCUTS("shortcuts"),
    MEDIA("media"),
    TIMER("timer"),
    BRIGHTNESS("brightness"),
    VOLUME("volume");

    companion object {
        fun fromId(id: String): WidgetId = entries.firstOrNull { it.id == id } ?: CLOCK
    }
}

/** Estado agregado de todas as configurações do app. */
data class Settings(
    val overlayEnabled: Boolean = false,
    val autoStart: Boolean = false,
    val autoHide: Boolean = true,
    val autoHideSeconds: Int = 12,
    val barSize: BarSize = BarSize.MEDIUM,
    val opacity: Float = 1f,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColors: Boolean = true,
    val accentHex: String? = null,
    val hapticsEnabled: Boolean = true,
    val hapticProfile: HapticProfile = HapticProfile.SOFT,
    val gestureTap: ShortcutAction = ShortcutAction.EXPAND,
    val gestureDoubleTap: ShortcutAction = ShortcutAction.NONE,
    val gestureLongPress: ShortcutAction = ShortcutAction.FLASHLIGHT,
    val gestureSwipeUp: ShortcutAction = ShortcutAction.EXPAND,
    val shortcuts: List<ShortcutItem> = emptyList(),
    val widgets: List<WidgetId> = listOf(
        WidgetId.CLOCK,
        WidgetId.TOGGLES,
        WidgetId.SHORTCUTS,
        WidgetId.TIMER,
        WidgetId.BRIGHTNESS
    ),
    val premium: Boolean = false
)
