#!/bin/bash

# serve_model.sh
# Usage: Run this in the directory containing your model files (e.g. scripts/)

PORT=8000
IP_ADDR=$(ifconfig | grep -E "inet " | grep -v 127.0.0.1 | awk '{print $2}' | head -n 1)

echo "📡 Starting local model server..."
echo "------------------------------------------------"
echo "Your Laptop IP: $IP_ADDR"
echo "E2B URL: http://10.0.2.2:$PORT/gemma-4-E2B-it.litertlm"
echo "E4B URL: http://10.0.2.2:$PORT/gemma-4-E4B-it.litertlm"
echo "------------------------------------------------"
echo "Press Ctrl+C to stop the server."

python3 -m http.server $PORT
