#!/bin/bash

# push_model.sh
# Usage: ./scripts/push_model.sh <path_to_model_file>

if [ -z "$1" ]; then
    echo "Error: No model file path provided."
    echo "Usage: ./scripts/push_model.sh /path/to/gemma.task"
    exit 1
fi

MODEL_PATH=$1
PACKAGE_NAME="org.medialiteracy"

# Extract extension (either 'task' or 'litertlm')
EXTENSION="${MODEL_PATH##*.}"
if [ "$EXTENSION" != "task" ] && [ "$EXTENSION" != "litertlm" ]; then
    EXTENSION="task" # fallback
fi

TEMP_PATH="/data/local/tmp/gemma.$EXTENSION"
DEST_PATH="/data/data/$PACKAGE_NAME/files/gemma.$EXTENSION"

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

echo "🚀 Starting high-speed model transfer..."

# 1. Push to temp
echo "📦 Pushing to temporary storage..."
adb push "$MODEL_PATH" "$TEMP_PATH"
adb shell "chmod 666 $TEMP_PATH"

# 2. Move to app internal storage
echo "🔐 Moving to app internal storage (requires run-as)..."
adb shell "run-as $PACKAGE_NAME mkdir -p files"
if adb shell "run-as $PACKAGE_NAME cp $TEMP_PATH files/gemma.$EXTENSION"; then
    echo "📄 File copied successfully within app context."
else
    echo "❌ Error: Failed to copy file to app context. Ensure the app is installed and debuggable."
    exit 1
fi

# 3. Cleanup
echo "🧹 Cleaning up..."
adb shell rm "$TEMP_PATH"

echo "✅ Success! Model is now available at: $DEST_PATH"
echo "Restart the app to initialize the engine."
