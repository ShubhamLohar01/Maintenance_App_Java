# Multilanguage Support (EN/HI/MR) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let any user, in any role, switch the app UI language between English, Hindi and Marathi from Settings — an instant, per-user (reset-on-logout) switch — and translate the worker-facing screens.

**Architecture:** A `LanguagePreferences` singleton stores the choice per userId and exposes it as a `StateFlow`. `MainActivity` wraps the app in `ProvideLocalizedContext`, which overrides `LocalContext`/`LocalConfiguration` so every `stringResource(...)` resolves the active language and recomposes on change (Approach A — no AppCompat). Auth login/logout load/reset the choice; Settings has the picker; worker screens move hardcoded text into `values/`, `values-hi/`, `values-mr/` string resources.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, Coroutines/Flow, JUnit5 + MockK + Turbine (unit tests). `minSdk 24`, `targetSdk 35`.

**Spec:** [`docs/superpowers/specs/2026-07-08-multilanguage-support-design.md`](../specs/2026-07-08-multilanguage-support-design.md)

**Commands (run from repo root `d:/Maintenance module/FactoryOps`):**
- Single unit test class: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.<TestClass>"`
- All unit tests: `./gradlew :app:testDebugUnitTest`
- Compile/build: `./gradlew :app:assembleDebug`

---

## File Structure

**New files:**
- `app/src/main/java/com/candorfoods/factoryops/domain/model/AppLanguage.kt` — the supported-languages enum + tag mapping. One responsibility: enumerate languages.
- `app/src/main/java/com/candorfoods/factoryops/data/local/LanguagePreferences.kt` — per-user persistence + live `StateFlow`. One responsibility: store/observe the choice.
- `app/src/main/java/com/candorfoods/factoryops/presentation/common/ProvideLocalizedContext.kt` — the Compose locale override. One responsibility: apply a language to the composition.
- `app/src/main/res/values-hi/strings.xml` — Hindi translations.
- `app/src/main/res/values-mr/strings.xml` — Marathi translations.
- `app/src/test/java/com/candorfoods/factoryops/AppLanguageTest.kt`
- `app/src/test/java/com/candorfoods/factoryops/LanguagePreferencesTest.kt`
- `app/src/test/java/com/candorfoods/factoryops/SettingsViewModelTest.kt`

**Modified files:**
- `app/src/main/java/com/candorfoods/factoryops/MainActivity.kt` — inject prefs, wrap root.
- `app/src/main/java/com/candorfoods/factoryops/data/repository/AuthRepositoryImpl.kt` — load on login, reset on logout.
- `app/src/main/java/com/candorfoods/factoryops/presentation/settings/SettingsViewModel.kt` — expose language + handler.
- `app/src/main/java/com/candorfoods/factoryops/presentation/settings/SettingsScreen.kt` — language card + string extraction.
- `app/src/main/res/values/strings.xml` — add English keys.
- `app/src/main/java/com/candorfoods/factoryops/presentation/login/LoginScreen.kt` — string extraction (exemplar).
- Remaining in-scope `presentation/**` screens (Part D) — string extraction.
- `app/src/main/java/com/candorfoods/factoryops/notification/NotificationHelper.kt` — localized notifications (Part E).

---

# Part A — Switch mechanism

## Task 1: `AppLanguage` enum

**Files:**
- Create: `app/src/main/java/com/candorfoods/factoryops/domain/model/AppLanguage.kt`
- Test: `app/src/test/java/com/candorfoods/factoryops/AppLanguageTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.candorfoods.factoryops

import com.candorfoods.factoryops.domain.model.AppLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppLanguageTest {

    @Test fun `fromTag maps known tags`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.HINDI, AppLanguage.fromTag("hi"))
        assertEquals(AppLanguage.MARATHI, AppLanguage.fromTag("mr"))
    }

    @Test fun `fromTag falls back to default for null or unknown`() {
        assertEquals(AppLanguage.DEFAULT, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.DEFAULT, AppLanguage.fromTag("fr"))
    }

    @Test fun `default is English`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.DEFAULT)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.AppLanguageTest"`
Expected: FAIL — compilation error, `AppLanguage` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.candorfoods.factoryops.domain.model

/**
 * The languages the app UI can switch between. [tag] is the ISO language tag used
 * both for the Android resource qualifier (values-hi / values-mr) and the [java.util.Locale].
 * [displayName] is always written in its own script so the option is recognizable
 * regardless of the active language.
 */
enum class AppLanguage(val tag: String, val displayName: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी"),
    MARATHI("mr", "मराठी");

    companion object {
        val DEFAULT = ENGLISH
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: DEFAULT
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.AppLanguageTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/domain/model/AppLanguage.kt app/src/test/java/com/candorfoods/factoryops/AppLanguageTest.kt
git commit -m "feat(i18n): add AppLanguage enum (en/hi/mr)"
```

---

## Task 2: `LanguagePreferences` (per-user persistence + StateFlow)

**Files:**
- Create: `app/src/main/java/com/candorfoods/factoryops/data/local/LanguagePreferences.kt`
- Test: `app/src/test/java/com/candorfoods/factoryops/LanguagePreferencesTest.kt`

- [ ] **Step 1: Write the failing test**

Mocks `SharedPreferences` with an in-memory map (no Robolectric in this project). Mirrors the MockK style already used across the test suite.

```kotlin
package com.candorfoods.factoryops

import android.content.Context
import android.content.SharedPreferences
import com.candorfoods.factoryops.data.local.LanguagePreferences
import com.candorfoods.factoryops.domain.model.AppLanguage
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LanguagePreferencesTest {

    private val store = mutableMapOf<String, String?>()
    private lateinit var prefs: LanguagePreferences

    @BeforeEach
    fun setUp() {
        store.clear()
        val editor = mockk<SharedPreferences.Editor>()
        val sharedPrefs = mockk<SharedPreferences>()
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns sharedPrefs
        every { sharedPrefs.getString(any(), any()) } answers { store[firstArg<String>()] ?: secondArg() }
        every { sharedPrefs.edit() } returns editor
        every { editor.putString(any(), any()) } answers { store[firstArg<String>()] = secondArg<String>(); editor }
        every { editor.apply() } just Runs
        prefs = LanguagePreferences(context)
    }

    @Test fun `defaults to English before any load`() {
        assertEquals(AppLanguage.ENGLISH, prefs.currentLanguage.value)
    }

    @Test fun `setLanguageForUser persists and publishes`() {
        prefs.setLanguageForUser("u1", AppLanguage.HINDI)
        assertEquals(AppLanguage.HINDI, prefs.currentLanguage.value)
        assertEquals("hi", store["lang_u1"])
    }

    @Test fun `loadForUser publishes stored choice`() {
        store["lang_u2"] = "mr"
        prefs.loadForUser("u2")
        assertEquals(AppLanguage.MARATHI, prefs.currentLanguage.value)
    }

    @Test fun `loadForUser defaults to English when nothing stored`() {
        prefs.loadForUser("nobody")
        assertEquals(AppLanguage.ENGLISH, prefs.currentLanguage.value)
    }

    @Test fun `resetToDefault reverts live flow but keeps stored value`() {
        prefs.setLanguageForUser("u1", AppLanguage.HINDI)
        prefs.resetToDefault()
        assertEquals(AppLanguage.ENGLISH, prefs.currentLanguage.value)
        assertEquals("hi", store["lang_u1"])            // retained on disk
        prefs.loadForUser("u1")
        assertEquals(AppLanguage.HINDI, prefs.currentLanguage.value)  // restored on next login
    }

    @Test fun `languageForUser reads without touching live flow`() {
        store["lang_u3"] = "hi"
        assertEquals(AppLanguage.HINDI, prefs.languageForUser("u3"))
        assertEquals(AppLanguage.ENGLISH, prefs.currentLanguage.value)  // flow unchanged
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.LanguagePreferencesTest"`
Expected: FAIL — `LanguagePreferences` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.candorfoods.factoryops.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import com.candorfoods.factoryops.domain.model.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the selected UI language **per user** (key `lang_<userId>`) in plain
 * SharedPreferences (the choice is not secret) and exposes the live value as a
 * [StateFlow] the app root observes. Follows the [OthersTagPreferences] pattern.
 *
 * Lifecycle: [loadForUser] on login, [resetToDefault] on logout, [setLanguageForUser]
 * from Settings. [languageForUser] is a side-effect-free read for background workers.
 */
@Singleton
class LanguagePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _current = MutableStateFlow(AppLanguage.DEFAULT)
    val currentLanguage: StateFlow<AppLanguage> = _current.asStateFlow()

    fun loadForUser(userId: String) { _current.value = languageForUser(userId) }

    fun setLanguageForUser(userId: String, lang: AppLanguage) {
        prefs.edit().putString(key(userId), lang.tag).apply()
        _current.value = lang
    }

    fun resetToDefault() { _current.value = AppLanguage.DEFAULT }

    fun languageForUser(userId: String): AppLanguage =
        AppLanguage.fromTag(prefs.getString(key(userId), null))

    private fun key(userId: String) = "$KEY_PREFIX$userId"

    companion object {
        private const val PREFS_NAME = "language_prefs"
        private const val KEY_PREFIX = "lang_"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.LanguagePreferencesTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/data/local/LanguagePreferences.kt app/src/test/java/com/candorfoods/factoryops/LanguagePreferencesTest.kt
git commit -m "feat(i18n): add per-user LanguagePreferences with live StateFlow"
```

---

## Task 3: `ProvideLocalizedContext` composable

No unit test — this is a Compose-only wrapper (the project has no Compose UI tests configured). It is exercised end-to-end by the manual verification in Task 10.

**Files:**
- Create: `app/src/main/java/com/candorfoods/factoryops/presentation/common/ProvideLocalizedContext.kt`

- [ ] **Step 1: Write the composable**

```kotlin
package com.candorfoods.factoryops.presentation.common

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.candorfoods.factoryops.domain.model.AppLanguage
import java.util.Locale

/**
 * Overrides [LocalContext] and [LocalConfiguration] with a locale-set context so every
 * `stringResource(...)` below resolves [language] and recomposes when it changes.
 * This is the whole language-switch mechanism on the Compose side — no activity recreate.
 */
@Composable
fun ProvideLocalizedContext(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val localized = remember(language, ctx) {
        val config = Configuration(ctx.resources.configuration).apply {
            setLocale(Locale(language.tag))
        }
        ctx.createConfigurationContext(config) to config
    }
    CompositionLocalProvider(
        LocalContext provides localized.first,
        LocalConfiguration provides localized.second,
    ) {
        content()
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/presentation/common/ProvideLocalizedContext.kt
git commit -m "feat(i18n): add ProvideLocalizedContext Compose locale override"
```

---

## Task 4: Wire the override into `MainActivity`

**Files:**
- Modify: `app/src/main/java/com/candorfoods/factoryops/MainActivity.kt`

- [ ] **Step 1: Replace the file contents**

```kotlin
package com.candorfoods.factoryops

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import com.candorfoods.factoryops.data.local.LanguagePreferences
import com.candorfoods.factoryops.navigation.AppNavGraph
import com.candorfoods.factoryops.presentation.common.ProvideLocalizedContext
import com.candorfoods.factoryops.presentation.common.theme.FactoryOpsTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var languagePreferences: LanguagePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val language by languagePreferences.currentLanguage.collectAsStateWithLifecycle()
            ProvideLocalizedContext(language) {
                FactoryOpsTheme {
                    AppNavGraph()
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/MainActivity.kt
git commit -m "feat(i18n): apply active language at the Compose root"
```

---

## Task 5: Load on login / reset on logout

Wires the two session transitions into the auth repository (the single source of truth for login/logout). The *decision* logic is already unit-tested in Task 2; this task is thin wiring verified by build + the end-to-end manual check in Task 10. (No unit test added here — `AuthRepositoryImpl` is not currently unit-tested and mocking Room + the login DTO for a two-call verification is high-brittleness, low-value.)

**Files:**
- Modify: `app/src/main/java/com/candorfoods/factoryops/data/repository/AuthRepositoryImpl.kt`

- [ ] **Step 1: Add the constructor dependency**

Change the constructor to inject `LanguagePreferences`:

```kotlin
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val db: AppDatabase,
    private val authPreferences: AuthPreferences,
    private val languagePreferences: LanguagePreferences,
) : AuthRepository {
```

Add the import near the other `data.local` imports:

```kotlin
import com.candorfoods.factoryops.data.local.LanguagePreferences
```

- [ ] **Step 2: Load the user's language after a successful login**

In `login(...)`, immediately before `Result.Success(entity.toDomain())`, add:

```kotlin
            languagePreferences.loadForUser(response.userId)
            Result.Success(entity.toDomain())
```

- [ ] **Step 3: Reset on logout**

Replace the body of `logout()`:

```kotlin
    override suspend fun logout() {
        db.userDao().clearAll()
        authPreferences.clear()
        languagePreferences.resetToDefault()
    }
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (Hilt provides `LanguagePreferences` via its `@Inject` constructor — no module change needed.)

- [ ] **Step 5: Run the full unit suite (nothing regressed)**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (existing tests still green).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/data/repository/AuthRepositoryImpl.kt
git commit -m "feat(i18n): load language on login, reset to English on logout"
```

---

# Part B — Settings picker

## Task 6: `SettingsViewModel` — expose language + handler

**Files:**
- Modify: `app/src/main/java/com/candorfoods/factoryops/presentation/settings/SettingsViewModel.kt`
- Test: `app/src/test/java/com/candorfoods/factoryops/SettingsViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.candorfoods.factoryops

import com.candorfoods.factoryops.data.local.LanguagePreferences
import com.candorfoods.factoryops.domain.model.AppLanguage
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import com.candorfoods.factoryops.domain.usecase.auth.LogoutUseCase
import com.candorfoods.factoryops.presentation.settings.SettingsViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var getCurrentUser: GetCurrentUserUseCase
    private lateinit var logoutUseCase: LogoutUseCase
    private lateinit var languagePreferences: LanguagePreferences

    private val user = User(
        id = "user-001", name = "Rajan", username = "operator1",
        role = UserRole.OPERATOR, plantId = "PLANT-01",
        jwtToken = "t", tokenExpiresAt = Long.MAX_VALUE,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getCurrentUser = mockk()
        logoutUseCase = mockk(relaxed = true)
        languagePreferences = mockk(relaxed = true)
        every { languagePreferences.currentLanguage } returns MutableStateFlow(AppLanguage.ENGLISH)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test fun `onLanguageSelected persists choice for current user`() {
        every { getCurrentUser() } returns flowOf(user)
        val vm = SettingsViewModel(getCurrentUser, logoutUseCase, languagePreferences)
        vm.onLanguageSelected(AppLanguage.HINDI)
        verify { languagePreferences.setLanguageForUser("user-001", AppLanguage.HINDI) }
    }

    @Test fun `onLanguageSelected no-ops when no current user`() {
        every { getCurrentUser() } returns flowOf(null)
        val vm = SettingsViewModel(getCurrentUser, logoutUseCase, languagePreferences)
        vm.onLanguageSelected(AppLanguage.HINDI)
        verify(exactly = 0) { languagePreferences.setLanguageForUser(any(), any()) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.SettingsViewModelTest"`
Expected: FAIL — `SettingsViewModel` constructor has no `languagePreferences` param / no `onLanguageSelected`.

- [ ] **Step 3: Modify `SettingsViewModel`**

Add imports:

```kotlin
import com.candorfoods.factoryops.data.local.LanguagePreferences
import com.candorfoods.factoryops.domain.model.AppLanguage
```

Change the constructor and add the exposed flow + handler (rest of the class unchanged):

```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getCurrentUser: GetCurrentUserUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val languagePreferences: LanguagePreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val currentLanguage: StateFlow<AppLanguage> = languagePreferences.currentLanguage

    private val _uiEvent = MutableSharedFlow<SettingsUiEvent>()
    val uiEvent: SharedFlow<SettingsUiEvent> = _uiEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            getCurrentUser().collect { user -> _uiState.update { it.copy(currentUser = user) } }
        }
    }

    fun onLanguageSelected(language: AppLanguage) {
        val userId = _uiState.value.currentUser?.id ?: return
        languagePreferences.setLanguageForUser(userId, language)
    }

    fun onLogout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true) }
            logoutUseCase()
            _uiState.update { it.copy(isLoggingOut = false) }
            _uiEvent.emit(SettingsUiEvent.LoggedOut)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.candorfoods.factoryops.SettingsViewModelTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/presentation/settings/SettingsViewModel.kt app/src/test/java/com/candorfoods/factoryops/SettingsViewModelTest.kt
git commit -m "feat(i18n): expose current language + selection handler in SettingsViewModel"
```

---

# Part C — String resources + Login exemplar

## Task 7: Seed string resources (en / hi / mr)

Adds the English keys for the Login + Settings screens and the two new translation files. Keys already present in `values/strings.xml` (`label_username`, `label_password`, `action_login`, `action_logout`, `label_settings`, `confirm_logout_title`, `shift_a/b/c`, `notification_missed_stop_title`, `notification_missed_stop_body`) are reused — only new keys are added below, plus a `%1$s` arg added to `notification_missed_stop_title`.

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values-hi/strings.xml`
- Create: `app/src/main/res/values-mr/strings.xml`

- [ ] **Step 1: Add new English keys to `values/strings.xml`**

Insert these before the closing `</resources>`, and change the existing `notification_missed_stop_title` line to include the machine-name arg:

```xml
    <!-- change existing line to add the machine-name arg -->
    <string name="notification_missed_stop_title">Missed Stop — %1$s</string>

    <!-- new keys -->
    <string name="cd_show_password">Show password</string>
    <string name="cd_hide_password">Hide password</string>
    <string name="action_retry">Retry</string>
    <string name="settings_role">Role</string>
    <string name="settings_plant">Plant</string>
    <string name="settings_shift_schedule">Shift Schedule</string>
    <string name="settings_server">Server</string>
    <string name="settings_language">Language</string>
    <string name="logout_confirm_message">Any unsynced data will be lost. Are you sure?</string>
    <string name="notification_breakdown_title">%1$s breakdown — %2$s</string>
```

- [ ] **Step 2: Create `values-hi/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="label_username">उपयोगकर्ता नाम</string>
    <string name="label_password">पासवर्ड</string>
    <string name="action_login">लॉग इन करें</string>
    <string name="cd_show_password">पासवर्ड दिखाएँ</string>
    <string name="cd_hide_password">पासवर्ड छिपाएँ</string>
    <string name="action_logout">लॉग आउट करें</string>
    <string name="action_retry">पुनः प्रयास करें</string>
    <string name="label_settings">सेटिंग्स</string>
    <string name="settings_role">भूमिका</string>
    <string name="settings_plant">प्लांट</string>
    <string name="settings_shift_schedule">शिफ्ट अनुसूची</string>
    <string name="settings_server">सर्वर</string>
    <string name="settings_language">भाषा</string>
    <string name="shift_a">शिफ्ट A: 08:00 – 16:00 IST</string>
    <string name="shift_b">शिफ्ट B: 16:00 – 24:00 IST</string>
    <string name="shift_c">शिफ्ट C: 00:00 – 08:00 IST</string>
    <string name="confirm_logout_title">लॉग आउट</string>
    <string name="logout_confirm_message">कोई भी अनसिंक डेटा खो जाएगा। क्या आप निश्चित हैं?</string>
    <string name="notification_missed_stop_title">मिस्ड स्टॉप — %1$s</string>
    <string name="notification_missed_stop_body">आप %1$s को रोकना भूल गए। रोकने का समय पुष्टि करने के लिए टैप करें।</string>
    <string name="notification_breakdown_title">%1$s ब्रेकडाउन — %2$s</string>
</resources>
```

- [ ] **Step 3: Create `values-mr/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="label_username">वापरकर्तानाव</string>
    <string name="label_password">पासवर्ड</string>
    <string name="action_login">लॉग इन करा</string>
    <string name="cd_show_password">पासवर्ड दाखवा</string>
    <string name="cd_hide_password">पासवर्ड लपवा</string>
    <string name="action_logout">लॉग आउट करा</string>
    <string name="action_retry">पुन्हा प्रयत्न करा</string>
    <string name="label_settings">सेटिंग्ज</string>
    <string name="settings_role">भूमिका</string>
    <string name="settings_plant">प्लांट</string>
    <string name="settings_shift_schedule">शिफ्ट वेळापत्रक</string>
    <string name="settings_server">सर्व्हर</string>
    <string name="settings_language">भाषा</string>
    <string name="shift_a">शिफ्ट A: 08:00 – 16:00 IST</string>
    <string name="shift_b">शिफ्ट B: 16:00 – 24:00 IST</string>
    <string name="shift_c">शिफ्ट C: 00:00 – 08:00 IST</string>
    <string name="confirm_logout_title">लॉग आउट</string>
    <string name="logout_confirm_message">कोणताही सिंक न झालेला डेटा नष्ट होईल. तुम्हाला खात्री आहे का?</string>
    <string name="notification_missed_stop_title">मिस्ड स्टॉप — %1$s</string>
    <string name="notification_missed_stop_body">तुम्ही %1$s थांबवायला विसरलात. थांबण्याची वेळ निश्चित करण्यासाठी टॅप करा.</string>
    <string name="notification_breakdown_title">%1$s ब्रेकडाउन — %2$s</string>
</resources>
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (resource merge succeeds; matching `%1$s` positional args across locales).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-hi/strings.xml app/src/main/res/values-mr/strings.xml
git commit -m "feat(i18n): seed en/hi/mr string resources for login + settings"
```

---

## Task 8: Convert `LoginScreen` to `stringResource` (the exemplar)

Establishes the exact extraction pattern reused in Part D. Brand terms `FactoryOps` and `Candor Foods`, and the dynamic `uiState.error`, are NOT extracted.

**Files:**
- Modify: `app/src/main/java/com/candorfoods/factoryops/presentation/login/LoginScreen.kt`

- [ ] **Step 1: Add imports**

After the existing `androidx.compose.ui.unit.dp` import, add:

```kotlin
import androidx.compose.ui.res.stringResource
import com.candorfoods.factoryops.R
```

- [ ] **Step 2: Replace the hardcoded strings**

| Old | New |
|---|---|
| `label = { Text("Username") },` | `label = { Text(stringResource(R.string.label_username)) },` |
| `label = { Text("Password") },` | `label = { Text(stringResource(R.string.label_password)) },` |
| `contentDescription = if (passwordVisible) "Hide password" else "Show password"` | `contentDescription = if (passwordVisible) stringResource(R.string.cd_hide_password) else stringResource(R.string.cd_show_password)` |
| `text = "Login",` | `text = stringResource(R.string.action_login),` |

Leave `Text("FactoryOps", …)`, `Text("Candor Foods", …)` and `Text(uiState.error, …)` unchanged.

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/presentation/login/LoginScreen.kt
git commit -m "feat(i18n): localize LoginScreen strings"
```

---

## Task 9: Add the language card to `SettingsScreen` + localize its strings

**Files:**
- Modify: `app/src/main/java/com/candorfoods/factoryops/presentation/settings/SettingsScreen.kt`

- [ ] **Step 1: Add imports**

Add to the import block:

```kotlin
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.candorfoods.factoryops.R
import com.candorfoods.factoryops.domain.model.AppLanguage
```

- [ ] **Step 2: Collect the current language**

Directly after `val uiState by viewModel.uiState.collectAsStateWithLifecycle()` add:

```kotlin
    val currentLanguage by viewModel.currentLanguage.collectAsStateWithLifecycle()
```

- [ ] **Step 3: Localize the existing static strings**

Apply these replacements:

| Old | New |
|---|---|
| `title = "Logout",` (in `ConfirmDialog`) | `title = stringResource(R.string.confirm_logout_title),` |
| `message = "Any unsynced data will be lost. Are you sure?",` | `message = stringResource(R.string.logout_confirm_message),` |
| `confirmLabel = "Logout",` | `confirmLabel = stringResource(R.string.action_logout),` |
| `title = { Text("Settings") },` | `title = { Text(stringResource(R.string.label_settings)) },` |
| `Text("Role: ${user.role.name}", …)` | `Text("${stringResource(R.string.settings_role)}: ${user.role.name}", …)` |
| `Text("Plant: ${user.plantId}", …)` | `Text("${stringResource(R.string.settings_plant)}: ${user.plantId}", …)` |
| `Text("Shift Schedule", …)` | `Text(stringResource(R.string.settings_shift_schedule), …)` |
| `Text("Shift A: 08:00 – 16:00 IST", …)` | `Text(stringResource(R.string.shift_a), …)` |
| `Text("Shift B: 16:00 – 24:00 IST", …)` | `Text(stringResource(R.string.shift_b), …)` |
| `Text("Shift C: 00:00 – 08:00 IST", …)` | `Text(stringResource(R.string.shift_c), …)` |
| `Text("Server", …)` | `Text(stringResource(R.string.settings_server), …)` |
| `Text("Logout")` (Button label) | `Text(stringResource(R.string.action_logout))` |

Leave the debug-only lines `Text("Mode: Mock (debug build)", …)` and `Text("Phase 2: Coming Soon", …)` unchanged (developer info, out of scope).

- [ ] **Step 4: Add the Language card**

Insert this `Card` immediately before `Spacer(Modifier.weight(1f))`:

```kotlin
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)
                    AppLanguage.entries.forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = lang == currentLanguage,
                                    onClick = { viewModel.onLanguageSelected(lang) },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = lang == currentLanguage, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(lang.displayName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
```

Note: `Row` and `Alignment` — add imports if the IDE flags them missing:

```kotlin
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
```

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/presentation/settings/SettingsScreen.kt
git commit -m "feat(i18n): add language picker + localize SettingsScreen"
```

---

## Task 10: End-to-end manual verification (Login + Settings)

The project has no Compose UI test harness configured (androidTest deps exist but no Hilt test runner), so the switch is verified manually. This proves the whole mechanism before the bulk extraction in Part D.

- [ ] **Step 1: Build & install the debug app**

Run: `./gradlew :app:installDebug` (device/emulator connected), or `./gradlew :app:assembleDebug` and install the APK.
Expected: app launches at the Login screen in English.

- [ ] **Step 2: Verify per-user switch**

1. Log in as any user → open **Settings**.
2. Tap **हिन्दी** → Settings labels (Role/Plant/Shift Schedule/Server/Language/Logout) switch to Hindi instantly, no flash.
3. Tap **मराठी** → labels switch to Marathi.
4. Tap **Logout** → confirm → returns to Login. Log back in as the **same** user → Settings is back in the last-chosen language (per-user restore).
5. Log in as a **different** user → Settings shows English (that user has no stored choice).
6. Kill and relaunch the app; log in as the first user → language persists.

- [ ] **Step 3: Confirm login screen localization**

While logged out, the Login screen shows English (expected — no user context pre-login). After choosing Hindi and logging out then back in, the Login screen's own labels are English at the logged-out moment; this is the documented behavior.

- [ ] **Step 4: Commit (nothing to change if all pass)**

No code change. If a defect is found, fix it under the relevant task and re-run.

---

# Part D — Extract & translate the remaining worker screens

Each task below applies the **same Extraction Procedure** to one screen area. The per-screen string list and its Hindi/Marathi text are produced during execution by reading that screen — they cannot be pre-listed accurately without the current source in hand, and pre-guessing hundreds of translations would produce stale, unreviewable text. Do the screens in order; each is an independent commit.

## Extraction Procedure (apply to every Part D task)

1. Open the screen file(s). Identify every **user-facing literal**: `Text("…")`, `label = { Text("…") }`, `placeholder`/`supportingText`, `contentDescription`, dialog `title`/`message`/button labels, `Button`/chip text, snackbar/toast text.
2. **Skip:** brand terms (`FactoryOps`, `Candor Foods`); dynamic/runtime data (machine names, user names, server error text, `uiState.error`, values interpolated from domain models); debug-only developer text.
3. For each literal, add an English key to `app/src/main/res/values/strings.xml` using the screen's prefix (see each task). For strings with runtime values, use positional args (`getString`/`stringResource` with `%1$s`, `%2$d`, …). For count-driven wording use a `<plurals>` block.
4. Add the matching key to **both** `values-hi/strings.xml` and `values-mr/strings.xml` with drafted translations (flag domain terms for review).
5. In the screen, replace each literal with `stringResource(R.string.<key>)` (add imports `androidx.compose.ui.res.stringResource` and `com.candorfoods.factoryops.R`). For strings needed outside a `@Composable` (e.g. inside a plain function), pass a `Context`/resolved string in, or resolve at the call site — do not call `stringResource` off the composition.
6. If a label comes from an enum `.label` property or is built in a ViewModel/domain layer (outside composition), either (a) map it to a `stringResource` at the Compose call site via a `when`, or (b) leave it English and note it in the commit body as deferred. Prefer (a) when the mapping is small.
7. Run `./gradlew :app:assembleDebug` — resolve any "missing positional argument"/merge errors (arg counts must match across locales).
8. Manually toggle the language on that screen (via Settings) and confirm it flips.
9. Commit: `git add <screen files> app/src/main/res/values*/strings.xml && git commit -m "feat(i18n): localize <area> screens"`.

## Task 11: Home router

**Files:** Modify `app/src/main/java/com/candorfoods/factoryops/presentation/home/HomeRouterScreen.kt`. **Prefix:** `home_`. Apply the Extraction Procedure. Commit message: `feat(i18n): localize home screens`.

## Task 12: Dashboard

**Files:** Modify `app/src/main/java/com/candorfoods/factoryops/presentation/dashboard/DashboardScreen.kt`, `.../dashboard/ComingSoonScreen.kt`. **Prefix:** `dashboard_`. Apply the Extraction Procedure. Commit: `feat(i18n): localize dashboard screens`.

## Task 13: Breakdown flag

**Files:** Modify `app/src/main/java/com/candorfoods/factoryops/presentation/breakdown/BreakdownFlagScreen.kt` (and any sibling files in that package). **Prefix:** `breakdown_`. Note: severity enum labels — apply Procedure step 6a. Commit: `feat(i18n): localize breakdown flag screen`.

## Task 14: Checklist (largest area — do file-by-file, one commit per file)

**Files (each its own commit):**
- `app/src/main/java/com/candorfoods/factoryops/presentation/checklist/ChecklistHubScreen.kt`
- `.../checklist/ChecklistScreen.kt`
- `.../checklist/BreakdownFormScreen.kt`
- `.../checklist/BreakdownListScreen.kt`
- `.../checklist/PreventiveListScreen.kt`
- `.../checklist/PreventiveDetailScreen.kt`

**Prefix:** `checklist_`. Apply the Extraction Procedure to each file; commit per file, e.g. `feat(i18n): localize ChecklistHubScreen`. (This package is ~2,300 lines — splitting keeps each commit reviewable.)

## Task 15: Machine detail

**Files:** Modify `app/src/main/java/com/candorfoods/factoryops/presentation/machine/MachineDetailScreen.kt`. **Prefix:** `machine_`. Apply the Extraction Procedure. Commit: `feat(i18n): localize machine detail screen`.

## Task 16: Machine list (operator home)

**Files:** Modify `app/src/main/java/com/candorfoods/factoryops/presentation/machinelist/MachineListScreen.kt`. **Prefix:** `machinelist_`. Note the deferred enum/runtime strings called out in the superseded 2026-07-07 spec (`MachineStatusFilter.label`, `MachineLiveStatus.label`, `"● SHUT DOWN"`, `"by <name>"`, `"Running <h>h <m>m"`, `"just now"`, group headers) — apply Procedure step 6a where the mapping is small, otherwise defer and note in the commit body. Commit: `feat(i18n): localize machine list screen`.

## Task 17: Transfer

**Files:** Modify `app/src/main/java/com/candorfoods/factoryops/presentation/transfer/TransferFormScreen.kt`, `.../transfer/TransferRecordsScreen.kt`. **Prefix:** `transfer_`. Apply the Extraction Procedure (one commit per file if large). Commit: `feat(i18n): localize transfer screens`.

## Task 18: Technician

**Files (one commit per file):**
- `app/src/main/java/com/candorfoods/factoryops/presentation/technician/TechnicianHomeScreen.kt`
- `.../technician/TechnicianPmDetailScreen.kt`
- `.../technician/TechnicianPmExecuteScreen.kt`
- `.../technician/DailyReadingScreen.kt`
- `.../technician/TicketDetailScreen.kt`

**Prefix:** `technician_`. Apply the Extraction Procedure. Commit per file, e.g. `feat(i18n): localize TechnicianHomeScreen`.

## Task 19: Sync status

**Files:** Modify `app/src/main/java/com/candorfoods/factoryops/presentation/sync/SyncStatusScreen.kt`. **Prefix:** `sync_`. Apply the Extraction Procedure. Commit: `feat(i18n): localize sync status screen`.

## Task 20: History

**Files:** Modify `app/src/main/java/com/candorfoods/factoryops/presentation/history/HistoryScreen.kt`. **Prefix:** `history_`. Apply the Extraction Procedure. Commit: `feat(i18n): localize history screen`.

## Task 21: Shared components

**Files:** Modify the user-facing literals in `app/src/main/java/com/candorfoods/factoryops/presentation/common/components/*.kt` (e.g. `ConfirmDialog`, `PrimaryButton`, `LoadingOverlay`, empty/error states). **Prefix:** `common_`. Only extract genuinely static labels; component text passed in by callers is already handled at the call site. Apply the Extraction Procedure. Commit: `feat(i18n): localize shared components`.

---

# Part E — Localized notifications

## Task 22: Localize worker notifications in `NotificationHelper`

Worker notifications (Missed Stop, new-breakdown) build strings from the app `Context`, which the Compose override does not reach. Resolve the logged-in user's language explicitly.

**Files:**
- Modify: `app/src/main/java/com/candorfoods/factoryops/notification/NotificationHelper.kt`

- [ ] **Step 1: Inject the preferences and add a localized-context helper**

Add constructor params and imports:

```kotlin
import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.local.LanguagePreferences
import com.candorfoods.factoryops.domain.model.AppLanguage
import android.content.res.Configuration
import java.util.Locale
```

```kotlin
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authPreferences: AuthPreferences,
    private val languagePreferences: LanguagePreferences,
) {
```

Add a private helper (place it above `showMissedStopNotification`):

```kotlin
    /** A context localized to the signed-in user's language (English if nobody is signed in). */
    private fun localized(): Context {
        val userId = authPreferences.getUserId()
        val lang = if (userId != null) languagePreferences.languageForUser(userId) else AppLanguage.DEFAULT
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale(lang.tag)) }
        return context.createConfigurationContext(config)
    }
```

- [ ] **Step 2: Use it for the Missed Stop notification**

In `showMissedStopNotification`, replace the title/text builders:

```kotlin
        val strings = localized()
        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_FLAGS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(strings.getString(R.string.notification_missed_stop_title, machineName))
            .setContentText(strings.getString(R.string.notification_missed_stop_body, machineName))
```

Add the import `import com.candorfoods.factoryops.R`.

- [ ] **Step 3: Use it for the breakdown notification title**

In `showBreakdownNotification`, replace:

```kotlin
            .setContentTitle("$severity breakdown — $machineName")
```

with:

```kotlin
            .setContentTitle(localized().getString(R.string.notification_breakdown_title, severity, machineName))
```

(`severity` and `descriptionPreview` are dynamic data and stay as-is.)

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (Hilt already provides `AuthPreferences`; `LanguagePreferences` was added in Task 2.)

- [ ] **Step 5: Run the full unit suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/candorfoods/factoryops/notification/NotificationHelper.kt
git commit -m "feat(i18n): localize worker notifications to the signed-in user's language"
```

---

# Final verification

- [ ] `./gradlew :app:testDebugUnitTest` — all unit tests pass.
- [ ] `./gradlew :app:assembleDebug` — build succeeds.
- [ ] Manual: log in, switch to Hindi and Marathi from Settings, walk the worker screens (home → machine list → machine → breakdown → checklist → transfer → technician → sync → history) and confirm they render translated; logout returns to English; re-login restores the user's choice.
- [ ] Manual: trigger a Missed Stop (or verify via a forced call) and confirm the notification appears in the signed-in user's language.
- [ ] Update the spec status line to `Status: Implemented` in `docs/superpowers/specs/2026-07-08-multilanguage-support-design.md`.

## Notes on scope

- Deferred screens (English for now): `admin`, `head`, `qc`, `supervisor`, `schedule`, `machinemaster`, `user` (Manage Users). A follow-up plan repeats Part D's Extraction Procedure for these.
- Reminder notifications built upstream (`ReminderEvaluator` → `showReminder`) carry pre-built `title`/`body` and are out of scope for this pass.
- All Hindi/Marathi text in this plan is a **draft** — flag for a native speaker's review before shipping a release.
