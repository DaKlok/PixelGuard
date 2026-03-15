package com.daklok.pixelguard.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daklok.pixelguard.data.AppLockPreferences
import com.daklok.pixelguard.ui.theme.PixelGuardTheme

class LockActivity : FragmentActivity() {

    private var hasPrompted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToHome()
            }
        })

        val packageName = intent.getStringExtra("PACKAGE_NAME") ?: "Unknown App"
        val prefs = AppLockPreferences(applicationContext)

        setContent {
            val themeMode by prefs.themeMode.collectAsStateWithLifecycle(initialValue = "System")
            val dynamicColor by prefs.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
            val unlockMethod by prefs.unlockMethod.collectAsStateWithLifecycle(initialValue = "PIN")
            val savedPin by prefs.customPin.collectAsStateWithLifecycle(initialValue = "")
            var usePinInstead by remember { mutableStateOf(false) }

            PixelGuardTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (unlockMethod == "PIN" || usePinInstead) {
                        PinUnlockScreen(
                            packageName = packageName,
                            savedPin = savedPin ?: "",
                            onUnlock = { handleSuccessfulUnlock(packageName) },
                            onCancel = { navigateToHome() }
                        )
                    } else {
                        BiometricLockScreen(
                            packageName = packageName,
                            onAuthenticate = { showBiometricPrompt(packageName) },
                            onUsePin = { usePinInstead = true },
                            onCancel = { navigateToHome() }
                        )
                    }
                }
            }

            LaunchedEffect(unlockMethod) {
                if (!hasPrompted && unlockMethod == "BIOMETRIC") {
                    hasPrompted = true
                    showBiometricPrompt(packageName)
                }
            }
        }
    }

    private fun handleSuccessfulUnlock(packageName: String) {
        Log.d("LockActivity", "Broadcasting unlock for: $packageName")
        val intent = Intent("com.daklok.pixelguard.UNLOCK_APP").apply {
            putExtra("PACKAGE_NAME", packageName)
            setPackage(applicationContext.packageName)
        }
        sendBroadcast(intent)
        finish()
        overridePendingTransition(0, 0)
    }

    private fun showBiometricPrompt(packageName: String) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        navigateToHome()
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    handleSuccessfulUnlock(packageName)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("App Locked")
            .setSubtitle("Authenticate to access this app")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun navigateToHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinUnlockScreen(
    packageName: String,
    savedPin: String,
    onUnlock: () -> Unit,
    onCancel: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .windowInsetsPadding(WindowInsets.systemBars),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = "Locked",
            modifier = Modifier.size(72.dp),
            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "App Locked",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Enter your custom PIN to continue",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = enteredPin,
            onValueChange = { 
                if (it.length <= 8) {
                    enteredPin = it.trim()
                    isError = false
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            isError = isError,
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                val cleanEntered = enteredPin.trim()
                val cleanSaved = savedPin.trim()
                if (cleanEntered == cleanSaved && cleanSaved.isNotEmpty()) {
                    onUnlock()
                } else {
                    isError = true
                    enteredPin = ""
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Unlock")
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}

@Composable
fun BiometricLockScreen(
    packageName: String,
    onAuthenticate: () -> Unit,
    onUsePin: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .windowInsetsPadding(WindowInsets.systemBars),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = "Locked",
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "App Locked",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Use your biometrics to unlock",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onAuthenticate,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Unlock with Biometrics")
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = onUsePin,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Use PIN")
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}
