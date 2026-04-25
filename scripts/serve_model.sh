#!/bin/bash

# serve_model.sh
# Usage: Run this in the directory containing your gemma.task file

PORT=8000
IP_ADDR=$(ifconfig | grep -E "inet " | grep -v 127.0.0.1 | awk '{print $2}' | head -n 1)

echo "📡 Starting local model server..."
echo "------------------------------------------------"
echo "Your Laptop IP: $IP_ADDR"
echo "Emulator URL:   http://10.0.2.2:$PORT/gemma.task"
echo "Device URL:     http://$IP_ADDR:$PORT/gemma.task"
echo "------------------------------------------------"
echo "Press Ctrl+C to stop the server."

python3 -m http.server $PORT
