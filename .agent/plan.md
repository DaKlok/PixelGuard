# Project Plan

Verify the recent UI/UX improvements and bug fixes. This includes checking the PIN unlock functionality, the improved settings page, the enhanced app list UI with animations, and the sorting of locked apps to the top.

## Project Brief

# Project Brief: PixelGuard (Updated)

## Features
1. **App Selection & Locking**: A core toggle interface allowing users to select and lock specific installed applications on their device. Selected (locked) apps should appear at the top of the list.
2. **Authentication (Biometric & PIN)**: Integration with system biometric prompt. Add custom PIN setup (default on first launch) and an option to unlock apps with a custom PIN.
3. **Settings Menu**: A settings screen to configure Theme (System/Light/Dark), toggle Material 3 Expressive (Dynamic Colors), toggle PIN/Biometrics, and a donation link to Ko-fi (https://ko-fi.com/daklok).
4. **Material 3 Expressive Design & Animations**: A vibrant, minimalist user interface utilizing Material You dynamic theming and fluid UI transitions. 

## High-Level Tech Stack
* **Language**: Kotlin
* **UI Framework**: Jetpack Compose (Edge-to-Edge display, Material 3 components)
* **Concurrency**: Kotlin Coroutines & Flow
* **Authentication**: AndroidX Biometric Library
* **State/Preferences**: AndroidX DataStore (Preferences)

## Implementation Steps

### Task_1_VerifyFeaturesAndUI: Run the app and perform final verification. Instruct critic_agent to verify the PIN unlock functionality, the settings page (Theme, Dynamic Color, auth toggle, Ko-fi link), the main app list's sorting and animations, and overall application stability (no crashes).
- **Status:** IN_PROGRESS
- **Acceptance Criteria:**
  - build pass
  - app does not crash
  - critic_agent confirms PIN auth works as expected
  - critic_agent confirms settings page is fully functional
  - critic_agent confirms locked apps are sorted to the top with animations
- **StartTime:** 2026-03-15 22:56:16 CET

