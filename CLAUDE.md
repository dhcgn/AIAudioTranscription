# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android app for transcribing audio/video files using OpenAI's Whisper-1, GPT-4o-transcribe, and GPT-4o-mini-transcribe models. Built with Kotlin 2.0, Jetpack Compose, Hilt DI, and Retrofit. Targets API 24-35, JVM 11.

## Build & Test Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean build (required after changing local.properties)
./gradlew clean assembleDebug

# Run all unit tests
./gradlew test

# Run a specific test class
./gradlew test --tests app.hdev.io.aitranscribe.ClipboardHelperTest

# Run instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Architecture

### Package Structure (`app/src/main/java/app/hdev/io/aitranscribe/`)

- **`presentation/`** — Activities (MainActivity, SettingsActivity, HistoryActivity) with Jetpack Compose UI. State is managed directly in Activities; ViewModels are not yet used.
- **`api/`** — `OpenAiApiService` (Retrofit interface) and `RetrofitClient` (OkHttp config with auth interceptor, 90s timeouts). Supports three models: `whisper-1`, `gpt-4o-audio-preview-transcribe`, `gpt-4o-mini-audio-preview-transcribe`.
- **`data/`** — `TranscriptionDbHelper` (SQLiteOpenHelper, version 3) and `TranscriptionEntry` data model. Room migration is planned but not done.
- **`di/`** — `AppModule` providing `OpenAiApiService` and `TranscriptionDbHelper` as Hilt singletons.
- **`utils/`** — `FileProcessingManager` (media conversion via Media3 Transformer to M4A/AAC, adaptive bitrate 16-32kbps, 24MB limit) and `ClipboardHelper` (clipboard with 20,000 char threshold, file-save fallback).
- **`sharedPrefsUtils/`** — `SharedPrefsUtils` wrapping `EncryptedSharedPreferences` (AES256_GCM) for API key storage.

### Key Workflows

**File Processing:** URI → `FileProcessingManager.processAudioFile(uri)` → copy via ContentResolver → convert to M4A/AAC via Media3 Transformer → validate ≤24MB → returns `ProcessingResult` with metadata.

**Transcription:** Processed file → multipart upload via Retrofit → model-specific endpoint → optional AI cleanup via GPT-4o chat completions → store in SQLite with statistics → display with copy/share/retry.

**API Key Flow:** `local.properties` → `BuildConfig.DEFAULT_OPENAI_API_KEY` → on first launch, `AITranscriptionApp.onCreate()` transfers to `EncryptedSharedPreferences` → Retrofit interceptor reads via `SharedPrefsUtils.getApiKey()`.

## Development Conventions

- **Hilt DI everywhere:** Use `@AndroidEntryPoint` on Activities, `@Inject` for `FileProcessingManager`, `OpenAiApiService`, `TranscriptionDbHelper`. Never instantiate these manually.
- **Concurrency:** Use `lifecycleScope.launch` for network/file ops, `withContext(Dispatchers.IO)` for blocking I/O, `rememberCoroutineScope()` in Composables.
- **Three-model handling:** When modifying transcription logic, update all three model branches (whisper-1, gpt-4o, gpt-4o-mini).
- **Database schema changes:** Increment `DATABASE_VERSION` and add migration in `onUpgrade()`. All queries on background threads.
- **Temp files:** Use `context.filesDir`, clean up on error. No hardcoded filenames.
- **Dependencies:** Managed via version catalog at `gradle/libs.versions.toml`.

## API Key Setup for Testing

Add `OPENAI_API_KEY=sk-...` to `local.properties` (gitignored), then `./gradlew clean assembleDebug`. See `TESTING.md` for details.

## Constraints

- **24MB file size limit** after processing (OpenAI API constraint)
- **20,000 char clipboard limit** (~1MB Android Binder limit); larger text triggers file-save fallback
- Debug builds use `.debug` applicationId suffix
- `local.properties` is gitignored — never commit API keys
