# Testing Guide: Pre-configuring OpenAI API Key

This guide explains how to build the AI Audio Transcription app with a pre-configured OpenAI API key for testing purposes. This approach ensures that API keys are never committed to the repository while allowing testers to have a ready-to-use build.

## Overview

The app supports pre-configuring an OpenAI API key at build time. When the app is first launched, it will automatically use the provided API key if one hasn't been set already. This feature is particularly useful for:

- Providing test builds to testers without requiring manual API key entry
- Creating builds for specific testing environments
- Streamlining the testing process

## Security Considerations

⚠️ **IMPORTANT SECURITY NOTES:**

1. **Never commit your API key to version control.** The `local.properties` file is already included in `.gitignore` to prevent accidental commits.
2. API keys in the build are stored in `BuildConfig` as plain text during compilation. Only distribute test builds to trusted testers.
3. Once saved, API keys are stored securely in the app using `EncryptedSharedPreferences`.
4. Users can always override the pre-configured key by entering a new one in the Settings screen.
5. For production releases distributed publicly, **do not** include an API key. Let users provide their own keys.

## How to Add an API Key for Testing

### Step 1: Create or Edit `local.properties`

In the root directory of the project (same level as `build.gradle.kts`), create or edit the file named `local.properties`.

```bash
# From the project root directory
touch local.properties  # Creates the file if it doesn't exist
```

### Step 2: Add Your OpenAI API Key

Open `local.properties` in a text editor and add the following line:

```properties
OPENAI_API_KEY=sk-your-actual-api-key-here
```

Replace `sk-your-actual-api-key-here` with your actual OpenAI API key.

**Example:**
```properties
OPENAI_API_KEY=sk-proj-AbCdEfGhIjKlMnOpQrStUvWxYz1234567890
```

### Step 3: Build the App

Build the app normally using Gradle:

```bash
# For release build
./gradlew assembleRelease

# For debug build
./gradlew assembleDebug
```

Or build through Android Studio:
1. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)** or **Build Bundle(s)**
2. The APK or AAB will be generated with the embedded API key

### Step 4: Install and Test

When the app is installed and launched for the first time:
- The API key will be automatically loaded from the build configuration
- The key is saved to secure encrypted storage (`EncryptedSharedPreferences`)
- Testers can immediately use transcription features without manually entering a key

## Verifying the Configuration

To verify that the API key was correctly configured:

1. Launch the app
2. Navigate to **Settings**
3. You should see a preview of the current API key (e.g., `sk-pro...xyz123`)
4. Use the **Test Key** button to verify the key works with OpenAI's API

## Updating or Changing the API Key

### At Build Time

To change the API key for future builds:
1. Edit the `local.properties` file
2. Update the `OPENAI_API_KEY` value
3. Rebuild the app

### At Runtime (By Testers)

Testers can always change the API key within the app:
1. Open the app
2. Go to **Settings**
3. Enter a new API key in the text field
4. Tap **Save Key**

## Building Without an API Key

If you don't want to pre-configure an API key:
1. Simply don't add `OPENAI_API_KEY` to `local.properties`, or leave it empty
2. The app will build normally without a pre-configured key
3. Users will need to enter their API key manually in the Settings screen

**Example (no key):**
```properties
# local.properties with no API key
# OPENAI_API_KEY=
```

## Troubleshooting

### "API key is missing" Error

If you see this error after building with a key:
1. Verify that `local.properties` exists in the **root project directory** (not in the `app` folder)
2. Check that the property is named exactly `OPENAI_API_KEY` (case-sensitive)
3. Ensure there are no extra spaces around the `=` sign
4. Rebuild the app completely: `./gradlew clean assembleRelease`

### Key Not Loading Automatically

If the key doesn't load on first launch:
1. Completely uninstall the app from the device
2. Rebuild the app to ensure the latest code is included
3. Reinstall and launch

### Accidental Commit Warning

If you accidentally commit `local.properties` with your API key:
1. Immediately revoke the API key at https://platform.openai.com/api-keys
2. Generate a new API key
3. Remove the file from git history:
   ```bash
   git rm --cached local.properties
   git commit -m "Remove local.properties from tracking"
   ```

## File Locations

- **Configuration file:** `<project-root>/local.properties`
- **Build script:** `<project-root>/app/build.gradle.kts`
- **App initialization:** `app/src/main/java/app/hdev/io/aitranscribe/AITranscriptionApp.kt`
- **API key storage logic:** `app/src/main/java/app/hdev/io/aitranscribe/sharedPrefsUtils/SharedPrefsUtils.kt`

## Additional Resources

- [OpenAI API Keys Documentation](https://platform.openai.com/api-keys)
- [Android EncryptedSharedPreferences Guide](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences)
- Project README: [README.md](README.md)

## Summary

This mechanism provides a secure and convenient way to pre-configure API keys for testing while ensuring that:
- ✅ No secrets are committed to version control
- ✅ Testers can use the app immediately without manual setup
- ✅ Keys are stored securely once in the app
- ✅ Users can always override the pre-configured key
- ✅ Production builds can be distributed without embedded keys
