# Copilot Instructions for AI Assistance in the AI Transcription App

This document provides essential guidelines and context for AIs helping with this project. Follow these instructions to ensure that code modifications align with the project’s design, security, and maintainability goals.

---

## Project Overview
- **Purpose:**  
  Transcribe media files (audio/video) using OpenAI’s Whisper API and GPT-4o-based models with additional AI-powered text cleanup.
- **Key Features:**  
  - File selection (including sharing intents)  
  - Audio processing (conversion via FFmpegKit to AAC/M4A format)  
  - Transcription using multiple models  
  - Local transcription history storage  
  - Secure API key management via EncryptedSharedPreferences  
  - Settings for language, prompts, and transcription models

---

## Technical Guidelines

### Architecture & Dependency Management
- **Dependency Injection:**  
  Use Hilt to inject dependencies (Retrofit service, database helper, file processing manager) rather than creating instances manually.
- **UI State Management:**  
  Prefer using a ViewModel to hold UI state and business logic rather than managing mutable state directly in Activities.
- **Networking & Concurrency:**  
  Use Retrofit’s coroutine support (suspend functions) for cleaner asynchronous code. Ensure all UI updates from background threads are dispatched on the main thread.
- **Database:**  
  Consider migrating from SQLiteOpenHelper to Room for better type safety, maintainability, and coroutine support.

### Code Quality & Maintainability
- **Reduce Duplication:**  
  Consolidate similar code paths (e.g., handling different transcription models) into shared helper methods.
- **Gradle Scripts:**  
  Consolidate dependency declarations to minimize duplication and improve readability.
- **Logging:**  
  Use logging interceptors only for debugging. Remove or disable sensitive logging in production builds.

### File Handling & Error Management
- **Unique File Names:**  
  When processing audio files, avoid hardcoding output file names. Use unique or temporary file names to prevent conflicts.
- **Error Propagation:**  
  Handle errors gracefully. For example, if the API key is missing or an FFmpeg command fails, notify the user instead of crashing.
- **Threading:**  
  Ensure background operations (file processing, network calls) run on appropriate threads, and any UI updates are posted on the main thread.

### Security & Permissions
- **Sensitive Data:**  
  Store API keys securely using EncryptedSharedPreferences. Avoid printing or logging sensitive information.
- **Storage Permissions:**  
  Reevaluate the use of legacy external storage permissions (e.g., WRITE_EXTERNAL_STORAGE, MANAGE_EXTERNAL_STORAGE). Consider modern scoped storage (MediaStore) to improve security and compatibility.

---

## What to Avoid
- **Hardcoding Sensitive Data:**  
  Never hardcode API keys or secrets in the code.
- **Redundant Dependency Initialization:**  
  Do not instantiate dependencies manually when they can be injected via Hilt.
- **Duplicated Logic:**  
  Avoid duplicate implementations for similar features (e.g., transcription model handling). Instead, extract common functionality.
- **Excessive Logging in Production:**  
  Ensure sensitive information is not logged, and disable debug logging in release builds.
- **Using Outdated Storage Permissions:**  
  Replace legacy storage permissions with modern, scoped approaches wherever possible.

---

## Best Practices
- Write idiomatic Kotlin and follow Android’s modern architecture patterns (e.g., MVVM, Jetpack Compose).
- Leverage coroutines and Retrofit’s suspend functions for network operations.
- Keep the UI responsive by offloading heavy operations (like file processing and network requests) to background threads.
- Ensure meaningful error messages and graceful failure states for a better user experience.
- Document functions clearly and maintain consistency in coding style.

---

## Project-Specific Tips
- **Transcription Models:**  
  Validate that the correct media type and encoding are used for each transcription model (e.g., Whisper vs. GPT-4o variants).
- **FFmpeg Commands:**  
  Carefully handle the FFmpeg command outputs and always clean up temporary files on errors.
- **Settings & Preferences:**  
  Make sure all user settings (API key, language, prompts, model selection) are saved and retrieved correctly.
- **Testing:**  
  Ensure comprehensive unit and instrumented tests are written to cover key functionalities.

---

Use this document as a guide when suggesting changes or writing new code. It serves to maintain the project’s consistency, security, and performance as it evolves.
