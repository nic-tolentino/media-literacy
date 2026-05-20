#!/bin/bash

# prepare_models.sh
# Calculates SHA-256 checksums and generates metadata.json for GemmaLens models.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

E2B_FILE="gemma-4-E2B-it.litertlm"
E4B_FILE="gemma-4-E4B-it.litertlm"

echo "🎯 Preparing model assets for hosting..."

# Helper to generate SHA-256
generate_sha256() {
    local file=$1
    if [ -f "$file" ]; then
        echo "⏳ Calculating SHA-256 for $file..."
        # shasum is standard on macOS
        shasum -a 256 "$file" > "$file.sha256"
        local hash=$(cat "$file.sha256" | awk '{print $1}')
        echo "✅ Created $file.sha256 (Hash: $hash)"
        eval "$2='$hash'"
    else
        echo "⚠️  Skipping $file: File not found in scripts/ directory."
    fi
}

E2B_HASH=""
E4B_HASH=""

generate_sha256 "$E2B_FILE" E2B_HASH
generate_sha256 "$E4B_FILE" E4B_HASH

# Generate metadata.json if at least one hash is found
if [ -n "$E2B_HASH" ] || [ -n "$E4B_HASH" ]; then
    echo "📄 Generating metadata.json..."
    cat <<EOF > metadata.json
{
  "e2b_hash": "${E2B_HASH:-none}",
  "e4b_hash": "${E4B_HASH:-none}"
}
EOF
    echo "✅ Created metadata.json"
    cat metadata.json
    echo "------------------------------------------------"
    echo "🎉 Ready! Upload these files to your hosting server:"
    [ -f "$E2B_FILE" ] && echo "  - $E2B_FILE" && echo "  - $E2B_FILE.sha256"
    [ -f "$E4B_FILE" ] && echo "  - $E4B_FILE" && echo "  - $E4B_FILE.sha256"
    echo "  - metadata.json"
    echo "------------------------------------------------"
else
    echo "❌ Error: No model files found. Please place them in the 'scripts' directory."
    exit 1
fi
