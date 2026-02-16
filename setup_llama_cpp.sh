#!/bin/bash

# Script to download and setup llama.cpp for Android integration

set -e

LLAMA_CPP_DIR="app/src/main/cpp/llama.cpp"
LLAMA_CPP_VERSION="b4313"  # Latest stable version

echo "=== Setting up llama.cpp for Android ==="

# Create directory if it doesn't exist
mkdir -p "$LLAMA_CPP_DIR"

# Download llama.cpp
echo "Downloading llama.cpp..."
if [ ! -d "$LLAMA_CPP_DIR/.git" ]; then
    git clone --depth 1 https://github.com/ggerganov/llama.cpp.git "$LLAMA_CPP_DIR"
else
    echo "llama.cpp already exists, pulling latest changes..."
    cd "$LLAMA_CPP_DIR"
    git pull
    cd -
fi

echo "✓ llama.cpp downloaded successfully"
echo "✓ Location: $LLAMA_CPP_DIR"
echo ""
echo "Next steps:"
echo "1. Update CMakeLists.txt to include llama.cpp"
echo "2. Rewrite llama-jni.cpp to use real llama.cpp API"
echo "3. Build the project"
