package com.daklok.pixelguard

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daklok.pixelguard.data.AppLockPreferences
import com.daklok.pixelguard.ui.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class PinState {
    object Loading : PinState()
    object NotSet : PinState()
    data class Set(val pin: String) : PinState()
}

sealed class PinChangeState {
    object Idle : PinChangeState()
    object Success : PinChangeState()
    object Failure : PinChangeState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AppLockPreferences(application)
    private val packageManager = application.packageManager

    private val _installedApps = MutableStateFlow<List<AppItem>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _pinChangeState = MutableStateFlow<PinChangeState>(PinChangeState.Idle)

    val searchQuery: StateFlow<String> = _searchQuery
    val pinChangeState: StateFlow<PinChangeState> = _pinChangeState
    
    val themeMode: StateFlow<String> = preferences.themeMode.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "System"
    )
    val dynamicColor: StateFlow<Boolean> = preferences.dynamicColor.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )
    val unlockMethod: StateFlow<String> = preferences.unlockMethod.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "PIN"
    )
    
    val pinState: StateFlow<PinState> = preferences.customPin.map { pin ->
        if (pin == null) PinState.NotSet else PinState.Set(pin)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PinState.Loading)

    val appsList: StateFlow<List<AppItem>> = combine(
        _installedApps,
        preferences.lockedApps,
        _searchQuery
    ) { apps, lockedPackages, query ->
        apps.map { app ->
            app.copy(isLocked = lockedPackages.contains(app.packageName))
        }.filter { app ->
            if (query.isEmpty()) {
                true
            } else {
                app.label.contains(query, ignoreCase = true)
            }
        }.sortedWith(compareByDescending<AppItem> { it.isLocked }.thenBy { it.label })
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                
                resolveInfoList.mapNotNull { resolveInfo ->
                    val packageName = resolveInfo.activityInfo.packageName
                    if (packageName == getApplication<Application>().packageName) {
                        return@mapNotNull null
                    }
                    
                    val label = resolveInfo.loadLabel(packageManager).toString()
                    val icon = resolveInfo.loadIcon(packageManager).toBitmap().asImageBitmap()
                    
                    AppItem(
                        packageName = packageName,
                        label = label,
                        icon = icon,
                        isLocked = false
                    )
                }.distinctBy { it.packageName }
            }
            _installedApps.value = apps
        }
    }

    fun toggleAppLock(packageName: String, isLocked: Boolean) {
        viewModelScope.launch {
            if (isLocked) {
                preferences.addLockedApp(packageName)
            } else {
                preferences.removeLockedApp(packageName)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun setThemeMode(mode: String) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }
    
    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferences.setDynamicColor(enabled) }
    }
    
    fun setUnlockMethod(method: String) {
        viewModelScope.launch { preferences.setUnlockMethod(method) }
    }
    
    fun savePin(pin: String) {
        viewModelScope.launch { preferences.setCustomPin(pin.trim()) }
    }

    fun changePin(currentPin: String, newPin: String) {
        viewModelScope.launch {
            val savedPin = preferences.customPin.first()
            if (savedPin == currentPin.trim()) {
                preferences.setCustomPin(newPin.trim())
                _pinChangeState.value = PinChangeState.Success
            } else {
                _pinChangeState.value = PinChangeState.Failure
            }
        }
    }
    
    fun resetPinChangeState() {
        _pinChangeState.value = PinChangeState.Idle
    }
}
