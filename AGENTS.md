# AGENTS.md — Инструкция для AI-агентов

## Краткое описание

MindfulPause — Android-приложение (Kotlin + Compose), которое перехватывает отслеживаемые приложения через AccessibilityService и показывает экран осознанной паузы перед использованием.

## Стек

- Kotlin, Jetpack Compose (Material3)
- DataStore Preferences (два хранилища: `settings` и `blocks`)
- AccessibilityService + WindowManager overlay
- Coroutines + Flow
- Min SDK 26, Target SDK 34

## Структура проекта

```
app/src/main/java/com/tabek/mindfulpause/
├── MainActivity.kt              # Single-activity, bottom nav (Home / Stats)
├── data/
│   ├── SettingsRepository.kt    # Настройки паузы, tracked apps, статистика
│   ├── BlockRepository.kt       # TimedBlock, DailyLimit, open counters
│   ├── EventLog.kt              # Лог: пауза/блокировка по времени
│   ├── Permissions.kt           # Проверка overlay + accessibility
│   └── AppInfo.kt               # Модель приложения
├── overlay/
│   ├── PauseOverlayController.kt # WindowManager overlay, lifecycle owner
│   ├── PauseScreen.kt           # Compose: дыхание + сообщение + таймер
│   └── BlockedScreen.kt         # Compose: блокировка с обратным отсчётом
├── service/
│   └── InterceptAccessibilityService.kt # Перехват TYPE_WINDOW_STATE_CHANGED
└── ui/
    ├── MainViewModel.kt         # Загрузка списка приложений, управление состоянием
    ├── screens/                  # HomeScreen, StatsScreen, AppPicker, BlockSheet, GestureSettings, PermissionGate, Components
    └── theme/Theme.kt           # Цвета: Accent, Background, SurfaceGlass, TextPrimary
```

## Ключевые файлы при изменении

| Файл | Трогать когда |
|------|---------------|
| `InterceptAccessibilityService.kt` | Логика перехвата, тайминги (RE_PAUSE_MS), приоритет блокировок |
| `SettingsRepository.kt` | Новые настройки паузы, дефолтные значения |
| `BlockRepository.kt` | Новые типы блокировок, формат хранения |
| `PauseOverlayController.kt` | Поведение overlay,.lifecycle, перехват BACK |
| `PauseScreen.kt` | UI экрана паузы |
| `BlockedScreen.kt` | UI экрана блокировки |
| `HomeScreen.kt` | Основной экран настроек |

## Паттерны

- **Два DataStore**: `settings` (настройки, tracked apps, счётчики решений) и `blocks` (timed blocks, daily limits, open counters). Не смешивать.
- **Flow → @Volatile snapshot**: AccessibilityService не может suspend, поэтому собирает Flow в `@Volatile` переменные через `launchIn(scope)`.
- **Overlay без Activity**: `PauseOverlayController` создаёт минимальный `LifecycleOwner` + `ViewModelStoreOwner` + `SavedStateRegistryOwner` для ComposeView в WindowManager.
- **Fail open**: Если overlay не удаётся показать — пользователь попадает в приложение, а не зависает.

## Правила при работе

1. **Не трогать AccessibilityService без необходимости** — он критичен для работы приложения
2. **Формат хранения в DataStore** — строки вида `"package|value"`, не нарушать совместимость
3. **Russian UI** — весь текст на русском языке
4. **Тема** — использовать цвета из `ui/theme/Theme.kt` (Accent, Background, SurfaceGlass, TextPrimary, TextMuted)
5. **Композы** — Material3, edge-to-edge, Surface с Background цветом

## Команды

```bash
# Сборка
./gradlew assembleDebug

# Линт
./gradlew lint

# Тесты (если есть)
./gradlew test
```

## Частые ошибки

- Забыть добавить `import` для нового ключа DataStore
- Нарушить формат строки `"package|value"` в `BlockRepository`
- Не обработать `isShowing` в `PauseOverlayController` — будет дублирование overlay
- Забыть `scope.cancel()` в `onDestroy` AccessibilityService
