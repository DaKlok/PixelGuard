package com.daklok.pixelguard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.daklok.pixelguard.ui.AppItem
import com.daklok.pixelguard.ui.theme.PixelGuardTheme
import com.daklok.pixelguard.utils.AccessibilityUtils

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
            val pinState by viewModel.pinState.collectAsStateWithLifecycle()
            
            PixelGuardTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                if (pinState is PinState.Loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                } else {
                    val navController = rememberNavController()
                    val startDestination = if (pinState is PinState.NotSet) "pin_setup" else "main"

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        NavHost(
                            navController = navController, 
                            startDestination = startDestination,
                            enterTransition = { fadeIn(animationSpec = tween(350)) },
                            exitTransition = { fadeOut(animationSpec = tween(350)) }
                        ) {
                            composable("pin_setup") {
                                val context = LocalContext.current
                                var showBiometricDialog by remember { mutableStateOf(false) }
                                var pendingPin by remember { mutableStateOf<String?>(null) }

                                PinSetupScreen(onPinSaved = { pin ->
                                    val biometricManager = BiometricManager.from(context)
                                    val canAuthenticate = biometricManager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
                                    
                                    if (canAuthenticate) {
                                        pendingPin = pin
                                        showBiometricDialog = true
                                    } else {
                                        viewModel.savePin(pin)
                                        navController.navigate("main") { popUpTo("pin_setup") { inclusive = true } }
                                    }
                                })

                                if (showBiometricDialog) {
                                    AlertDialog(
                                        onDismissRequest = { 
                                            viewModel.savePin(pendingPin!!)
                                            navController.navigate("main") { popUpTo("pin_setup") { inclusive = true } }
                                        },
                                        title = { Text("Use Biometrics?") },
                                        text = { Text("Would you like to use biometrics (Fingerprint/Face) for faster unlocking? You can still use your PIN as a backup.") },
                                        confirmButton = {
                                            TextButton(onClick = {
                                                viewModel.savePin(pendingPin!!)
                                                viewModel.setUnlockMethod("BIOMETRIC")
                                                navController.navigate("main") { popUpTo("pin_setup") { inclusive = true } }
                                            }) { Text("Yes") }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = {
                                                viewModel.savePin(pendingPin!!)
                                                navController.navigate("main") { popUpTo("pin_setup") { inclusive = true } }
                                            }) { Text("No") }
                                        }
                                    )
                                }
                            }
                            composable("main") {
                                MainScreen(
                                    viewModel = viewModel,
                                    onNavigateToSettings = { navController.navigate("settings") }
                                )
                            }
                            composable(
                                "settings",
                                enterTransition = {
                                    slideIntoContainer(
                                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                        animationSpec = tween(350)
                                    ) + fadeIn(animationSpec = tween(350))
                                },
                                exitTransition = {
                                    slideOutOfContainer(
                                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                                        animationSpec = tween(350)
                                    ) + fadeOut(animationSpec = tween(350))
                                }
                            ) {
                                SettingsScreen(viewModel = viewModel, navController = navController)
                            }
                            composable(
                                "change_pin",
                                enterTransition = {
                                    slideIntoContainer(
                                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                        animationSpec = tween(350)
                                    ) + fadeIn(animationSpec = tween(350))
                                },
                                exitTransition = {
                                    slideOutOfContainer(
                                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                                        animationSpec = tween(350)
                                    ) + fadeOut(animationSpec = tween(350))
                                }
                            ) {
                                ChangePinScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, onNavigateToSettings: () -> Unit) {
    val appsList by viewModel.appsList.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isAccessibilityEnabled by remember {
        mutableStateOf(AccessibilityUtils.isAccessibilityServiceEnabled(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = AccessibilityUtils.isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PixelGuard",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::updateSearchQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    placeholder = { Text("Search apps") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search"
                        )
                    },
                    shape = RoundedCornerShape(32.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    singleLine = true
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            if (!isAccessibilityEnabled) {
                AccessibilityBanner(onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                })
                Spacer(modifier = Modifier.height(16.dp))
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(appsList, key = { it.packageName }) { app ->
                    AppListItem(
                        app = app,
                        onToggle = { isLocked ->
                            viewModel.toggleAppLock(app.packageName, isLocked)
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
fun AppListItem(app: AppItem, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val cardColor by animateColorAsState(
        targetValue = if (app.isLocked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) 
                      else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        label = "cardColor"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        onClick = { onToggle(!app.isLocked) }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(48.dp).clip(CircleShape))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = app.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = app.isLocked,
                onCheckedChange = onToggle,
                thumbContent = {
                    Icon(
                        imageVector = if (app.isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}

@Composable
fun AccessibilityBanner(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Service Disabled", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Tap to enable Accessibility to protect apps.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSetupScreen(onPinSaved: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Rounded.Lock, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(24.dp))
            Text(if (isConfirming) "Confirm your PIN" else "Set a custom PIN", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Used to unlock apps when biometrics fail.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(
                value = if (isConfirming) confirmPin else pin,
                onValueChange = { if (it.length <= 8) { if (isConfirming) confirmPin = it.trim() else pin = it.trim() } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    if (isConfirming) {
                        if (pin.trim() == confirmPin.trim()) onPinSaved(pin.trim())
                        else { isConfirming = false; pin = ""; confirmPin = "" }
                    } else isConfirming = true
                },
                enabled = (if (isConfirming) confirmPin.length else pin.length) >= 4,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(if (isConfirming) "Confirm" else "Next")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, navController: NavController) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val unlockMethod by viewModel.unlockMethod.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Appearance", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(8.dp))
            ListItem(
                headlineContent = { Text("Theme") },
                leadingContent = { Icon(Icons.Rounded.Palette, null) },
                trailingContent = {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expanded = true }) { Text(themeMode) }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf("System", "Light", "Dark").forEach { mode ->
                                DropdownMenuItem(text = { Text(mode) }, onClick = { viewModel.setThemeMode(mode); expanded = false })
                            }
                        }
                    }
                }
            )
            ListItem(
                headlineContent = { Text("Dynamic Color") },
                leadingContent = { Icon(Icons.Rounded.InvertColors, null) },
                trailingContent = { Switch(checked = dynamicColor, onCheckedChange = { viewModel.setDynamicColor(it) }) }
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text("Security", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(8.dp))
            ListItem(
                headlineContent = { Text("Unlock Method") },
                leadingContent = { Icon(Icons.Rounded.Lock, null) },
                trailingContent = {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expanded = true }) { Text(unlockMethod) }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf("BIOMETRIC", "PIN").forEach { method ->
                                DropdownMenuItem(text = { Text(method) }, onClick = { viewModel.setUnlockMethod(method); expanded = false })
                            }
                        }
                    }
                }
            )
            ListItem(
                headlineContent = { Text("Change PIN") },
                leadingContent = { Icon(Icons.Rounded.Key, null) },
                modifier = Modifier.clickable { navController.navigate("change_pin") }
            )
            Spacer(modifier = Modifier.weight(1f))
            Card(
                onClick = { uriHandler.openUri("https://ko-fi.com/daklok") },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp, start = 8.dp, end = 8.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Rounded.Favorite, "Support", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Support on Ko-fi", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePinScreen(viewModel: MainViewModel, onNavigateBack: () -> Unit) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmNewPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val pinChangeState by viewModel.pinChangeState.collectAsStateWithLifecycle()

    LaunchedEffect(pinChangeState) {
        if (pinChangeState is PinChangeState.Success) { viewModel.resetPinChangeState(); onNavigateBack() }
        else if (pinChangeState is PinChangeState.Failure) { errorMessage = "Current PIN is incorrect."; viewModel.resetPinChangeState() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Change PIN") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(value = currentPin, onValueChange = { currentPin = it.trim() }, label = { Text("Current PIN") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = newPin, onValueChange = { newPin = it.trim() }, label = { Text("New PIN") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = confirmNewPin, onValueChange = { confirmNewPin = it.trim() }, label = { Text("Confirm New PIN") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            errorMessage?.let { Spacer(modifier = Modifier.height(16.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    if (newPin != confirmNewPin) errorMessage = "New PINs do not match."
                    else if (newPin.length < 4) errorMessage = "PIN must be at least 4 digits."
                    else viewModel.changePin(currentPin.trim(), newPin.trim())
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Change PIN") }
        }
    }
}
