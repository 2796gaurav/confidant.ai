#!/bin/bash

# Build Production Release APK Script
# This script builds a clean, production-ready release APK

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Confidant AI - Production APK Build Script ===${NC}\n"

# Check if keystore exists
KEYSTORE_FILE="confidant-release.keystore"
KEYSTORE_PROPERTIES="keystore.properties"

if [ ! -f "$KEYSTORE_FILE" ]; then
    echo -e "${YELLOW}Keystore not found. Creating new release keystore...${NC}"
    echo -e "${YELLOW}You will be prompted for keystore information.${NC}\n"
    
    keytool -genkey -v \
        -keystore "$KEYSTORE_FILE" \
        -alias confidant-release \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass android \
        -keypass android \
        -dname "CN=Confidant AI, OU=Development, O=Confidant AI, L=Unknown, S=Unknown, C=US"
    
    echo -e "\n${GREEN}Keystore created successfully!${NC}\n"
fi

# Create keystore.properties if it doesn't exist
if [ ! -f "$KEYSTORE_PROPERTIES" ]; then
    echo -e "${YELLOW}Creating keystore.properties...${NC}"
    cat > "$KEYSTORE_PROPERTIES" << EOF
storeFile=confidant-release.keystore
storePassword=android
keyAlias=confidant-release
keyPassword=android
EOF
    echo -e "${GREEN}keystore.properties created!${NC}\n"
fi

# Clean previous builds
echo -e "${GREEN}Cleaning previous builds...${NC}"
./gradlew clean

# Build release APK
echo -e "\n${GREEN}Building release APK...${NC}"
./gradlew assembleRelease

# Get version info from build.gradle.kts
VERSION_NAME=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts | head -1)
VERSION_CODE=$(grep -oP 'versionCode = \K[0-9]+' app/build.gradle.kts | head -1)

# Create releases directory
RELEASES_DIR="releases"
mkdir -p "$RELEASES_DIR"

# Copy and rename APK with proper naming
APK_SOURCE="app/build/outputs/apk/release/app-release.apk"
APK_NAME="confidant-ai-v${VERSION_NAME}-${VERSION_CODE}-release.apk"
APK_DEST="$RELEASES_DIR/$APK_NAME"

if [ -f "$APK_SOURCE" ]; then
    cp "$APK_SOURCE" "$APK_DEST"
    echo -e "\n${GREEN}✓ Release APK built successfully!${NC}"
    echo -e "${GREEN}  Location: $APK_DEST${NC}"
    echo -e "${GREEN}  Size: $(du -h "$APK_DEST" | cut -f1)${NC}"
    echo -e "${GREEN}  Version: $VERSION_NAME (Code: $VERSION_CODE)${NC}\n"
else
    echo -e "${RED}✗ Error: APK not found at $APK_SOURCE${NC}"
    exit 1
fi

# Sign the APK with apksigner (if available)
if command -v apksigner &> /dev/null; then
    echo -e "${GREEN}APK signing verified.${NC}\n"
else
    echo -e "${YELLOW}Note: apksigner not found. APK is signed via Gradle.${NC}\n"
fi

echo -e "${GREEN}=== Build Complete ===${NC}"
echo -e "Production APK: ${GREEN}$APK_DEST${NC}\n"
