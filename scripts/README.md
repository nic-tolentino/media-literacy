# Developer Scripts for News Decoder

These scripts help you manage the 2.4GB - 3.4GB Gemma model weights during development.

## 1. Fast Transfer (ADB Push)
Use this if you already have the model file on your Mac and want to skip the download screen entirely.

Run this command from the project root:
```bash
./scripts/push_model.sh scripts/gemma-4-E2B-it.litertlm
```

## 2. Local Testing (HTTP Server)
Use this if you want to test the `OnboardingScreen` download logic and progress bar without using your internet data.

1. `cd` into the folder containing your model.
2. Run the server:
   ```bash
   ../scripts/serve_model.sh
   ```
3. Update `BASE_URL` in `ModelConfig.kt` to the local IP URL shown in the terminal (e.g. `http://10.0.2.2:8000/`).

## Troubleshooting
- **Permission Denied**: Run `chmod +x scripts/*.sh`
- **ADB not found**: The script attempts to find `adb` automatically. If it fails, ensure the Android SDK is installed or the `$ANDROID_HOME` / `$ANDROID_SDK_ROOT` environment variable is set.
- **run-as failed**: This happens if the app is not currently installed or is not a debuggable build. Run the app once from Android Studio first.
