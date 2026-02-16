package com.confidant.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.confidant.ai.data.PreferencesManager
import com.confidant.ai.model.ModelDownloadManager
import com.confidant.ai.service.NotificationCaptureService
import com.confidant.ai.service.ThermalMonitoringService
import com.confidant.ai.setup.OnboardingScreen
import com.confidant.ai.ui.screens.*
import com.confidant.ai.ui.theme.ConfidantTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    
    private lateinit var modelDownloadManager: ModelDownloadManager
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        
        super.onCreate(savedInstanceState)
        
        // Initialize model download manager
        modelDownloadManager = ModelDownloadManager.getInstance(this)
        
        // Check if setup is complete using singleton PreferencesManager
        var isSetupComplete = false
        runBlocking {
            isSetupComplete = PreferencesManager.getInstance(this@MainActivity).setupCompleteFlow.first()
        }
        
        // NO AUTOMATIC DOWNLOAD - User must manually trigger download
        
        // Request necessary permissions
        requestNecessaryPermissions()
        
        // Start services if setup complete
        if (isSetupComplete) {
            startCoreServices()
        }
        
        setContent {
            ConfidantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    NavHost(
                        navController = navController,
                        startDestination = if (isSetupComplete) "dashboard" else "onboarding"
                    ) {
                        composable("onboarding") {
                            OnboardingScreen(
                                onSetupComplete = {
                                    navController.navigate("dashboard") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                    startCoreServices()
                                }
                            )
                        }
                        
                        composable("dashboard") {
                            DashboardScreenRedesigned(
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToThermal = { navController.navigate("thermal") },
                                onNavigateToLogs = { navController.navigate("logs") },
                                onNavigateToIntegrations = { navController.navigate("integrations") }
                            )
                        }
                        
                        composable("settings") {
                            SettingsScreenRedesigned(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToIntegrations = { navController.navigate("integrations") },
                                onNavigateToDataViewer = { navController.navigate("dataviewer") }
                            )
                        }
                        
                        composable("integrations") {
                            IntegrationsScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        
                        composable("dataviewer") {
                            DataViewerScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        
                        composable("memory") {
                            MemoryScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        
                        composable("thermal") {
                            ThermalScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        
                        composable("logs") {
                            LogsScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
    
    private fun requestNecessaryPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Request storage permissions for persistent model storage (Android 9 and below)
        // Android 10+ uses MediaStore which doesn't require runtime permissions
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
    
    private fun startCoreServices() {
        // Start thermal monitoring
        ThermalMonitoringService.start(this)
        
        // Check and prompt for notification listener if not enabled
        if (!NotificationCaptureService.isEnabled(this)) {
            // Will be handled in UI
        }
        
        // DON'T start Telegram bot automatically - only start when user starts AI server
        android.util.Log.i("MainActivity", "Telegram bot will start when AI server is started")
    }
}