package com.slandbar.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.slandbar.app.BuildConfig
import com.slandbar.app.MainActivity
import com.slandbar.app.R
import com.slandbar.app.accessibility.SlandAccessibilityService
import com.slandbar.app.core.BillingManager
import com.slandbar.app.core.OverlayManager
import com.slandbar.app.core.SlandPrefs
import com.slandbar.app.core.model.BarSize
import com.slandbar.app.core.model.GestureType
import com.slandbar.app.core.model.HapticProfile
import com.slandbar.app.core.model.Settings
import com.slandbar.app.core.model.ShortcutAction
import com.slandbar.app.core.model.ShortcutItem
import com.slandbar.app.core.model.ThemeMode
import com.slandbar.app.core.model.WidgetId
import com.slandbar.app.overlay.FloatingBarUi
import kotlinx.coroutines.launch
import java.util.UUID

/** Raiz da tela do app (configurações + bilheteria + tema). */
@Composable
fun SlandApp(activity: MainActivity) {
    val context = LocalContext.current
    val prefs = remember { SlandPrefs(context) }
    val scope = rememberCoroutineScope()
    val settings by prefs.settings.collectAsState(initial = Settings())

    val billing = remember {
        BillingManager(context) { premium ->
            scope.launch { prefs.setPremium(premium) }
        }
    }
    DisposableEffect(Unit) {
        billing.start()
        onDispose { billing.endConnection() }
    }
    val billingPremium by billing.premium.collectAsState(initial = false)
    val premium = settings.premium || billingPremium || BuildConfig.DEBUG

    val dark = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkThemeCompat()
    }

    SlandTheme(
        dark = dark,
        useDynamic = settings.useDynamicColors,
        accent = parseAccentColor(settings.accentHex)
    ) {
        SettingsScreen(
            prefs = prefs,
            settings = settings,
            premium = premium,
            onBuyPremium = { billing.launchPurchase(activity) },
            onRestorePremium = { billing.restore() }
        )
    }
}

@Composable
private fun isSystemInDarkThemeCompat(): Boolean =
    androidx.compose.foundation.isSystemInDarkTheme()

private const val FREE_SHORTCUT_LIMIT = 6

/** Tela principal: todas as configurações e personalizações. */
@Composable
private fun SettingsScreen(
    prefs: SlandPrefs,
    settings: Settings,
    premium: Boolean,
    onBuyPremium: () -> Unit,
    onRestorePremium: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showAddShortcut by remember { mutableStateOf(false) }
    var showPremiumDialog by remember { mutableStateOf(false) }

    // ---- Permissões (estado ao vivo) ----
    var canOverlay by remember { mutableStateOf(AndroidSettings.canDrawOverlays(context)) }
    var canWriteSettings by remember { mutableStateOf(AndroidSettings.System.canWrite(context)) }
    var notifGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canOverlay = AndroidSettings.canDrawOverlays(context)
                canWriteSettings = AndroidSettings.System.canWrite(context)
                notifGranted = Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
                cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun toggleOverlay(on: Boolean) {
        scope.launch {
            prefs.setOverlayEnabled(on)
            if (on) {
                if (AndroidSettings.canDrawOverlays(context)) OverlayManager.start(context)
            } else {
                OverlayManager.stop(context)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- Cabeçalho ----
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "SlandBar",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Barra flutuante de atalhos e widgets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ---- Permissões ----
        if (!canOverlay) {
            item {
                PermissionCard(
                    icon = Icons.Filled.PhoneAndroid,
                    title = "Permitir sobreposição",
                    description = "Necessário para desenhar a barra por cima de outros apps.",
                    action = "Abrir configurações",
                    onAction = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    }
                )
            }
        }
        if (!notifGranted) {
            item {
                PermissionCard(
                    icon = Icons.Filled.Info,
                    title = "Notificações",
                    description = "Uma notificação discreta mantém a barra ativa em segundo plano.",
                    action = "Permitir",
                    onAction = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                )
            }
        }
        if (!SlandAccessibilityService.enabled) {
            item {
                PermissionCard(
                    icon = Icons.Filled.CheckCircle,
                    title = "Serviço de acessibilidade",
                    description = "Opcional: captura de tela, menu de energia e recolher com o teclado. Nenhum dado é coletado.",
                    action = "Ativar",
                    onAction = onOpenAccessibility
                )
            }
        }
        if (!canWriteSettings) {
            item {
                PermissionCard(
                    icon = Icons.Filled.Info,
                    title = "Ajustar brilho",
                    description = "Opcional: controlar o brilho da tela pelo painel.",
                    action = "Abrir configurações",
                    onAction = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    AndroidSettings.ACTION_MANAGE_WRITE_SETTINGS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    }
                )
            }
        }
        if (!cameraGranted) {
            item {
                PermissionCard(
                    icon = Icons.Filled.Star,
                    title = "Lanterna",
                    description = "Opcional: alternar o flash da câmera pelos atalhos.",
                    action = "Permitir",
                    onAction = { cameraLauncher.launch(Manifest.permission.CAMERA) }
                )
            }
        }

        // ---- Barra flutuante ----
        item { SectionTitle("Barra flutuante") }
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Ligar barra flutuante", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (OverlayManager.isRunning()) "Ativa ✓" else "Toque para ativar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = settings.overlayEnabled, onCheckedChange = ::toggleOverlay)
                }
            }
        }

        // ---- Geral ----
        item { SectionTitle("Geral") }
        item {
            SwitchRow(
                title = "Iniciar com o aparelho",
                subtitle = "Reativa a barra após reiniciar o celular",
                checked = settings.autoStart,
                onChecked = { scope.launch { prefs.setAutoStart(it) } }
            )
        }
        item {
            SwitchRow(
                title = "Auto-ocultar",
                subtitle = "Esconde a barra após um tempo sem uso",
                checked = settings.autoHide,
                onChecked = { scope.launch { prefs.setAutoHide(it) } }
            )
        }
        if (settings.autoHide) {
            item {
                SliderRow(
                    title = "Segundos até ocultar",
                    value = settings.autoHideSeconds.toFloat(),
                    range = 3f..30f,
                    display = "${settings.autoHideSeconds}s",
                    onValue = { scope.launch { prefs.setAutoHideSeconds(it.toInt()) } }
                )
            }
        }
        item {
            SwitchRow(
                title = "Vibração ao tocar",
                subtitle = "Feedback háptico nos gestos da barra",
                checked = settings.hapticsEnabled,
                onChecked = { scope.launch { prefs.setHapticsEnabled(it) } }
            )
        }
        if (settings.hapticsEnabled) {
            item {
                ChipRow(
                    title = "Perfil de vibração",
                    options = HapticProfile.entries.map {
                        it.id to when (it) {
                            HapticProfile.SOFT -> "Suave"
                            HapticProfile.MECHANICAL -> "Mecânico"
                            HapticProfile.DEEP -> "Fundo profundo"
                        }
                    },
                    selected = settings.hapticProfile.id,
                    premiumLocked = !premium && settings.hapticProfile != HapticProfile.SOFT,
                    onSelect = { id ->
                        if (!premium && id != HapticProfile.SOFT.id) {
                            showPremiumDialog = true
                        } else {
                            scope.launch { prefs.setHapticProfile(HapticProfile.fromId(id)) }
                        }
                    }
                )
            }
        }

        // ---- Aparência ----
        item { SectionTitle("Aparência") }
        item {
            ChipRow(
                title = "Tamanho da barra",
                options = BarSize.entries.map {
                    it.name to when (it) {
                        BarSize.SMALL -> "Pequena"
                        BarSize.MEDIUM -> "Média"
                        BarSize.LARGE -> "Grande"
                    }
                },
                selected = settings.barSize.name,
                onSelect = { scope.launch { prefs.setBarSize(BarSize.valueOf(it)) } }
            )
        }
        item {
            SliderRow(
                title = "Opacidade",
                value = settings.opacity,
                range = 0.4f..1f,
                display = "${(settings.opacity * 100).toInt()}%",
                onValue = { scope.launch { prefs.setOpacity(it) } }
            )
        }
        item {
            ChipRow(
                title = "Tema",
                options = ThemeMode.entries.map {
                    it.id to when (it) {
                        ThemeMode.SYSTEM -> "Sistema"
                        ThemeMode.LIGHT -> "Claro"
                        ThemeMode.DARK -> "Escuro"
                    }
                },
                selected = settings.themeMode.id,
                onSelect = { scope.launch { prefs.setThemeMode(ThemeMode.fromId(it)) } }
            )
        }
        item {
            SwitchRow(
                title = "Cores dinâmicas (Material You)",
                subtitle = "Acompanha o papel de parede do seu aparelho",
                checked = settings.useDynamicColors,
                onChecked = { scope.launch { prefs.setDynamicColors(it) } }
            )
        }
        item {
            AccentRow(
                selected = settings.accentHex,
                premium = premium,
                onSelect = { scope.launch { prefs.setAccentHex(it) } },
                onLocked = { showPremiumDialog = true }
            )
        }

        // ---- Gestos ----
        item { SectionTitle("Gestos da barra") }
        GestureType.entries.forEach { gesture ->
            item {
                val current = when (gesture) {
                    GestureType.TAP -> settings.gestureTap
                    GestureType.DOUBLE_TAP -> settings.gestureDoubleTap
                    GestureType.LONG_PRESS -> settings.gestureLongPress
                    GestureType.SWIPE_UP -> settings.gestureSwipeUp
                }
                GestureRow(
                    gesture = gesture,
                    current = current,
                    onSelect = { scope.launch { prefs.setGesture(gesture, it) } }
                )
            }
        }

        // ---- Atalhos ----
        item { SectionTitle("Meus atalhos") }
        if (settings.shortcuts.isEmpty()) {
            item {
                Text(
                    "Nenhum atalho ainda. Toque em “+” para adicionar apps ou ações rápidas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        settings.shortcuts.forEach { item ->
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LauncherIcon(item)
                        Spacer(Modifier.width(12.dp))
                        Text(item.label, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            scope.launch {
                                prefs.setShortcuts(settings.shortcuts.filterNot { it.id == item.id })
                            }
                        }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Remover",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    if (!premium && settings.shortcuts.size >= FREE_SHORTCUT_LIMIT) {
                        showPremiumDialog = true
                    } else {
                        showAddShortcut = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (premium) "Adicionar atalho" else "Adicionar atalho (grátis até $FREE_SHORTCUT_LIMIT)")
            }
        }

        // ---- Widgets ----
        item { SectionTitle("Widgets do painel") }
        WidgetId.entries.forEach { widget ->
            item {
                val locked = !premium && (widget == WidgetId.MEDIA || widget == WidgetId.VOLUME)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = widget in settings.widgets,
                            enabled = !locked,
                            onCheckedChange = { checked ->
                                val newList = if (checked) {
                                    settings.widgets + widget
                                } else {
                                    settings.widgets.filterNot { it == widget }
                                }
                                scope.launch { prefs.setWidgets(newList) }
                            }
                        )
                        Text(widgetLabel(widget), modifier = Modifier.weight(1f))
                        if (locked) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = "Premium",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { showPremiumDialog = true }
                            )
                        } else if (!premium) {
                            Text(
                                "GRÁTIS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // ---- Premium ----
        item {
            PremiumCard(
                premium = premium,
                onBuy = onBuyPremium,
                onRestore = onRestorePremium
            )
        }

        // ---- Sobre ----
        item { SectionTitle("Sobre") }
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("SlandBar v${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Feito com Jetpack Compose. 100% local — nenhum dado sai do seu aparelho.\n" +
                            "Atalhos, widgets e personalização sem anúncios no uso normal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // ---- Diálogo: adicionar atalho ----
    if (showAddShortcut) {
        AddShortcutDialog(
            onAdd = { item ->
                scope.launch { prefs.setShortcuts(settings.shortcuts + item) }
                showAddShortcut = false
            },
            onDismiss = { showAddShortcut = false }
        )
    }

    // ---- Diálogo: Premium ----
    if (showPremiumDialog) {
        AlertDialog(
            onDismissRequest = { showPremiumDialog = false },
            icon = { Icon(Icons.Filled.Star, contentDescription = null) },
            title = { Text("SlandBar Premium") },
            text = {
                Text("Recurso disponível no Premium: desbloqueio único, sem assinatura. " +
                    "Atalhos ilimitados, widget de música, temas e mais.")
            },
            confirmButton = {
                Button(onClick = {
                    showPremiumDialog = false
                    onBuyPremium()
                }) { Text("Ver Premium") }
            },
            dismissButton = {
                TextButton(onClick = { showPremiumDialog = false }) { Text("Agora não") }
            }
        )
    }
}

// ------------------------------------------------------------------ Widgets de UI

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onValue: (Float) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    display,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(value = value, onValueChange = onValue, valueRange = range)
        }
    }
}

@Composable
private fun ChipRow(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    premiumLocked: Boolean = false,
    onSelect: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (premiumLocked) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Premium",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (id, label) ->
                    val isSelected = id == selected
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.clickable { onSelect(id) }
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

private val ACCENT_COLORS = listOf(
    "#6366F1", "#0EA5E9", "#10B981", "#F43F5E",
    "#F59E0B", "#8B5CF6", "#14B8A6", "#EC4899"
)

@Composable
private fun AccentRow(
    selected: String?,
    premium: Boolean,
    onSelect: (String?) -> Unit,
    onLocked: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cor de destaque", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (!premium) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Premium",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Opção "nenhuma" (segue Material You / padrão)
                ColorDot(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    selected = selected == null,
                    label = "Padrão",
                    onClick = {
                        if (premium) onSelect(null) else onLocked()
                    }
                )
                ACCENT_COLORS.forEach { hex ->
                    ColorDot(
                        color = parseAccentColor(hex) ?: Color.Gray,
                        selected = selected == hex,
                        label = hex,
                        onClick = {
                            if (premium) onSelect(hex) else onLocked()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
            .then(
                if (selected) {
                    Modifier.background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        CircleShape
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun GestureRow(
    gesture: GestureType,
    current: ShortcutAction,
    onSelect: (ShortcutAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(gestureLabel(gesture), fontWeight = FontWeight.SemiBold)
                    Text(
                        actionLabel(current),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                GESTURE_ACTIONS.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(actionLabel(action)) },
                        onClick = {
                            expanded = false
                            onSelect(action)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    action: String,
    onAction: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun PremiumCard(
    premium: Boolean,
    onBuy: () -> Unit,
    onRestore: () -> Unit
) {
    if (premium) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Premium ativo ✓",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Obrigado por apoiar o app!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
        return
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                    )
                )
            )
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "SlandBar Premium",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(10.dp))
            listOf(
                "Atalhos ilimitados no painel",
                "Widget de música com capa do álbum",
                "Temas e cores personalizadas",
                "Perfis de vibração avançados",
                "Controle de volume no painel"
            ).forEach { feature ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(feature, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Pagamento único, sem assinatura. O app continua gratuito com os recursos essenciais.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onBuy, modifier = Modifier.fillMaxWidth()) {
                Text("Desbloquear Premium — pagamento único")
            }
            TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                Text("Restaurar compra")
            }
        }
    }
}

@Composable
private fun AddShortcutDialog(
    onAdd: (ShortcutItem) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf("apps") }

    val apps = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }
    val quickActions = listOf(
        ShortcutAction.FLASHLIGHT to "Lanterna",
        ShortcutAction.SCREENSHOT to "Captura de tela",
        ShortcutAction.CAMERA to "Câmera",
        ShortcutAction.DND to "Não perturbe",
        ShortcutAction.ROTATION to "Rotação",
        ShortcutAction.RINGER to "Modo de som",
        ShortcutAction.POWER to "Menu de energia",
        ShortcutAction.NOTIFICATIONS to "Notificações",
        ShortcutAction.SETTINGS to "Configurações do app"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar atalho") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { tab = "apps" }) {
                        Text(
                            "Apps",
                            color = if (tab == "apps") {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    OutlinedButton(onClick = { tab = "actions" }) {
                        Text(
                            "Ações rápidas",
                            color = if (tab == "actions") {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (tab == "apps") {
                    LazyColumn(Modifier.height(320.dp)) {
                        items(apps) { (pkg, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAdd(ShortcutItem(UUID.randomUUID().toString(), label, ShortcutAction.APP, pkg)) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LauncherIcon(ShortcutItem("", label, ShortcutAction.APP, pkg))
                                Spacer(Modifier.width(12.dp))
                                Text(label, maxLines = 1)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(Modifier.height(320.dp)) {
                        items(quickActions) { (action, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAdd(
                                            ShortcutItem(
                                                UUID.randomUUID().toString(),
                                                label,
                                                action
                                            )
                                        )
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    FloatingBarUi.iconFor(action),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(label)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun LauncherIcon(item: ShortcutItem) {
    val context = LocalContext.current
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { (40 * density).toInt() }
    val bitmap = remember(item.packageName) {
        if (item.action == ShortcutAction.APP && item.packageName != null) {
            runCatching {
                val drawable: Drawable =
                    context.packageManager.getApplicationIcon(item.packageName)
                drawable.toBitmap(px, px).asImageBitmap()
            }.getOrNull()
        } else null
    }
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = item.label,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                FloatingBarUi.iconFor(item.action),
                contentDescription = item.label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ---------------------------------------------------------------- Helpers

private fun gestureLabel(gesture: GestureType): String = when (gesture) {
    GestureType.TAP -> "Toque simples"
    GestureType.DOUBLE_TAP -> "Toque duplo"
    GestureType.LONG_PRESS -> "Toque longo"
    GestureType.SWIPE_UP -> "Deslizar para cima"
}

private val GESTURE_ACTIONS = listOf(
    ShortcutAction.NONE,
    ShortcutAction.EXPAND,
    ShortcutAction.FLASHLIGHT,
    ShortcutAction.SCREENSHOT,
    ShortcutAction.CAMERA,
    ShortcutAction.DND,
    ShortcutAction.ROTATION,
    ShortcutAction.RINGER,
    ShortcutAction.POWER,
    ShortcutAction.NOTIFICATIONS,
    ShortcutAction.SETTINGS
)

private fun actionLabel(action: ShortcutAction): String = when (action) {
    ShortcutAction.NONE -> "Nenhum"
    ShortcutAction.EXPAND -> "Expandir painel"
    ShortcutAction.FLASHLIGHT -> "Lanterna"
    ShortcutAction.SCREENSHOT -> "Captura de tela"
    ShortcutAction.CAMERA -> "Câmera"
    ShortcutAction.DND -> "Não perturbe"
    ShortcutAction.ROTATION -> "Rotação"
    ShortcutAction.RINGER -> "Modo de som"
    ShortcutAction.POWER -> "Menu de energia"
    ShortcutAction.NOTIFICATIONS -> "Notificações"
    ShortcutAction.SETTINGS -> "Configurações"
    ShortcutAction.APP -> "Aplicativo"
}

private fun widgetLabel(widget: WidgetId): String = when (widget) {
    WidgetId.CLOCK -> "Relógio"
    WidgetId.TOGGLES -> "Controles rápidos"
    WidgetId.SHORTCUTS -> "Atalhos"
    WidgetId.MEDIA -> "Música (Premium)"
    WidgetId.TIMER -> "Cronômetro / Temporizador"
    WidgetId.BRIGHTNESS -> "Brilho"
    WidgetId.VOLUME -> "Volume (Premium)"
}
