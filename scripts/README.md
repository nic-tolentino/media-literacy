# Developer Scripts for Gemma4ML

These scripts help you manage the 1.2GB Gemma model weights during development.

## 1. Fast Transfer (ADB Push)
Use this if you already have the `gemma.task` file on your Mac and want to skip the download screen entirely.

```bash
./scripts/push_model.sh ~/Downloads/gemma-2b-it-cpu.task
```

## 2. Local Testing (HTTP Server)
Use this if you want to test the `OnboardingScreen` download logic and progress bar without using your internet data.

1. `cd` into the folder containing your model.
2. Run the server:
   ```bash
   /path/to/media-literacy/scripts/serve_model.sh
   ```
3. Update `modelUrl` in `GemmaOrchestrator.kt` to the URL shown in the terminal.

## Troubleshooting
- **Permission Denied**: Run `chmod +x scripts/*.sh`
- **ADB not found**: Ensure Android SDK platform-tools is in your PATH.
- **run-as failed**: This happens if the app is not currently installed or is not a debuggable build. Run the app once from Android Studio first.
