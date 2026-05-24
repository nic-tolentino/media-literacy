#!/bin/bash

# push_model.sh
# Usage: ./scripts/push_model.sh <path_to_model_file>

if [ -z "$1" ]; then
    echo "Error: No model file path provided."
    echo "Usage: ./scripts/push_model.sh scripts/gemma-4-E4B-it.litertlm"
    exit 1
fi

MODEL_PATH=$1
FILENAME=$(basename "$MODEL_PATH")
PACKAGE_NAME="org.medialiteracy"

# Locate ADB
if command -v adb >/dev/null 2>&1; then
    ADB_CMD="adb"
elif [ -n "$ANDROID_HOME" ] && [ -x "$ANDROID_HOME/platform-tools/adb" ]; then
    ADB_CMD="$ANDROID_HOME/platform-tools/adb"
elif [ -n "$ANDROID_SDK_ROOT" ] && [ -x "$ANDROID_SDK_ROOT/platform-tools/adb" ]; then
    ADB_CMD="$ANDROID_SDK_ROOT/platform-tools/adb"
elif [ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
    ADB_CMD="$HOME/Library/Android/sdk/platform-tools/adb"
else
    echo "Error: 'adb' command not found."
    echo "Please ensure the Android SDK is installed and 'adb' is in your PATH, or set \$ANDROID_HOME."
    exit 1
fi

TEMP_PATH="/data/local/tmp/$FILENAME"
DEST_PATH="/data/data/$PACKAGE_NAME/files/$FILENAME"

# 0. Size Check
FILE_SIZE=$(du -k "$MODEL_PATH" | cut -f1)
if [ "$FILE_SIZE" -gt 4500000 ]; then
    echo "⚠️  WARNING: This file is >4.5GB ($((FILE_SIZE/1024)) MB)."
    echo "You are likely trying to push a 'Transformers' model instead of an edge model."
    echo "MediaPipe/LiteRT models should be ~1.2GB - 4.0GB."
    read -p "Are you sure you want to proceed? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

echo "🚀 Starting high-speed model transfer for $FILENAME..."

# 1. Push to temp
echo "📦 Pushing to temporary storage..."
"$ADB_CMD" push "$MODEL_PATH" "$TEMP_PATH"
"$ADB_CMD" shell "chmod 666 $TEMP_PATH"

# 2. Move to app internal storage
echo "🔐 Moving to app internal storage (requires run-as)..."
"$ADB_CMD" shell "run-as $PACKAGE_NAME mkdir -p files"
if "$ADB_CMD" shell "run-as $PACKAGE_NAME cp $TEMP_PATH files/$FILENAME"; then
    echo "📄 File copied successfully within app context."
else
    echo "❌ Error: Failed to copy file to app context. Ensure the app is installed and debuggable."
    exit 1
fi

# 3. Cleanup
echo "🧹 Cleaning up..."
"$ADB_CMD" shell rm "$TEMP_PATH"

echo "✅ Success! Model is now available at: $DEST_PATH"
echo "Restart the app to initialize the engine."
