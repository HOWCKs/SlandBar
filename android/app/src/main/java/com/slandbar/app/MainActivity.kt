package com.slandbar.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.slandbar.app.ui.SlandApp

/**
 * Tela principal: configurações e personalização da SlandBar.
 * Também recebe pedidos de permissão vindos da barra flutuante.
 */
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_REQUEST_PERMISSION = "request_permission"
        const val REQUEST_CAMERA = "camera"
        const val REQUEST_ACCESSIBILITY = "accessibility"
    }

    private var pendingRequest by mutableStateOf<String?>(null)

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingRequest = intent.getStringExtra(EXTRA_REQUEST_PERMISSION)
        setContent {
            SlandApp(this)
        }
        handlePendingRequest(pendingRequest)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRequest = intent.getStringExtra(EXTRA_REQUEST_PERMISSION)
        handlePendingRequest(pendingRequest)
    }

    /** Chamado pelo executor de ações quando um atalho precisa de permissão. */
    fun requestCameraPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /** Abre as configurações de acessibilidade para o usuário ativar o serviço. */
    fun openAccessibilitySettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun handlePendingRequest(request: String?) {
        when (request) {
            REQUEST_CAMERA -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    requestCameraPermission()
                }
                pendingRequest = null
            }
            REQUEST_ACCESSIBILITY -> {
                openAccessibilitySettings()
                pendingRequest = null
            }
        }
    }
}
