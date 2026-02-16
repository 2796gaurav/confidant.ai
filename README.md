# 🤖 Confidant AI

<div align="center">

<img src="docs/assets/logo.svg" alt="Confidant AI Logo" width="200" height="200">

**Your Privacy-First AI Companion**

[![GitHub](https://img.shields.io/badge/GitHub-Repository-blue?logo=github)](https://github.com/2796gaurav/confidant.ai)
[![Website](https://img.shields.io/badge/Website-Landing%20Page-green)](http://2796gaurav.github.io/confidantai)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green?logo=android)](https://www.android.com/)

*100% On-Device • Privacy First • Open Source*

</div>

---

## 🌟 Overview

**Confidant AI** is an intelligent Android AI assistant that runs entirely on your device. Powered by on-device LLM (llama.cpp), it learns from your notifications and conversations to provide contextual assistance, proactive insights, and intelligent responses—all while keeping your data completely private and secure.

### ✨ Key Features

- 🔒 **100% On-Device Processing** - All AI processing happens locally. No cloud servers, no data collection.
- 🛡️ **Privacy First** - We don't collect names, gender, or personal identifiers. Your data stays on your device.
- 💬 **Telegram Integration** - Chat with your AI companion through Telegram. Get instant responses and proactive notifications.
- 🔍 **Smart Web Search** - Integrated DuckDuckGo search with intelligent query handling for accurate, up-to-date information.
- 🧠 **Intelligent & Proactive** - Learns from your notifications and conversations to provide contextual assistance.
- 🌙 **Sleep Mode** - Conserve battery and prevent disturbances during your rest hours.
- 📱 **Background Processing** - Continues working even when the app is closed.
- 🌡️ **Thermal Management** - Adaptive performance based on device temperature.

---

## 📱 Screenshots

*Coming soon - Add screenshots of the app*

---

## 🚀 Quick Start

### Prerequisites

- Android Studio Hedgehog or later
- Android SDK 26+ (Android 8.0+)
- NDK 27.0.12077973
- Gradle 8.9
- 8GB+ RAM for building

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/2796gaurav/confidant.ai.git
   cd confidant.ai
   ```

2. **Download APK**
   - Visit the [Releases](https://github.com/2796gaurav/confidant.ai/releases) page
   - Download the latest APK file
   - Install on your Android device

3. **Build from Source**
   ```bash
   ./gradlew assembleDebug
   ```
   APK will be generated at: `app/build/outputs/apk/debug/app-debug.apk`

---

## 📖 Setup Guide

### Initial Setup

1. **Launch the App** - Open Confidant AI on your device
2. **Complete Onboarding**:
   - Enter your interests (minimum 2)
   - Connect Telegram bot (get token from [@BotFather](https://t.me/botfather))
   - Configure sleep mode (optional)
   - Grant required permissions
3. **Download AI Model**:
   - Go to Dashboard
   - Tap "DOWNLOAD MODEL NOW"
   - Wait for download to complete (~2GB, use WiFi recommended)
4. **Start AI Server**:
   - Tap "Start Server" on Dashboard
   - Wait for server to initialize
5. **Start Chatting**:
   - Open Telegram
   - Find your bot and send `/start`
   - Begin chatting with your AI companion!

### Telegram Bot Setup

1. Open Telegram and search for `@BotFather`
2. Send `/newbot` command
3. Follow the prompts to create your bot
4. Copy the API token provided
5. Get your Chat ID:
   - Search for `@userinfobot` in Telegram
   - Start a chat and copy your ID
6. Enter both in the app during onboarding

---

## 🏗️ Architecture

### Core Components

- **LLM Engine**: Native llama.cpp integration for on-device inference
- **Telegram Manager**: Bot communication with streaming responses
- **Memory System**: Conversation history and context management
- **Search Integration**: Web search with intelligent result processing
- **Personalization**: User preference learning and adaptation
- **Proactive System**: Pattern detection and contextual suggestions

### Tech Stack

- **Language**: Kotlin + C++ (JNI)
- **LLM Backend**: llama.cpp (optimized for ARM)
- **Database**: Room (SQLite)
- **UI**: Jetpack Compose
- **Networking**: OkHttp + Retrofit
- **Async**: Kotlin Coroutines
- **Architecture**: MVVM with Clean Architecture principles

---

## 📁 Project Structure

```
confidant.ai/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── cpp/              # Native code
│   │   │   │   ├── llama.cpp/    # LLM engine
│   │   │   │   ├── llama-jni.cpp # JNI bindings
│   │   │   │   └── CMakeLists.txt
│   │   │   ├── java/com/confidant/ai/
│   │   │   │   ├── engine/       # LLM engine
│   │   │   │   ├── telegram/     # Telegram integration
│   │   │   │   ├── memory/       # Memory system
│   │   │   │   ├── integrations/ # Search & tools
│   │   │   │   ├── ui/           # Compose UI
│   │   │   │   ├── database/     # Room DB
│   │   │   │   ├── proactive/    # Proactive messaging
│   │   │   │   └── service/      # Background services
│   │   │   └── res/              # Resources
│   │   ├── test/                 # Unit tests
│   │   └── androidTest/          # Integration tests
│   └── build.gradle.kts
├── build.gradle.kts
└── README.md
```

---

## 🔧 Configuration

### Model Setup

The AI model is stored in **persistent storage** (Downloads folder) that survives app uninstallation.

- Model file: `lfm2.5-1.2b-instruct-q4_k_m.gguf`
- Location: `/storage/emulated/0/Download/`
- Size: ~2GB
- Format: GGUF (llama.cpp compatible)

**Note**: Model download is manual only. The app never downloads automatically to respect your data usage.

### Permissions

Confidant AI requires the following permissions:

- **Storage Access**: To read AI model file from Downloads folder
- **Notification Permission**: To send you AI messages and alerts (Android 13+)
- **Notification Access**: To read notifications for contextual assistance
- **Battery Optimization**: Disable to keep AI running 24/7

All permissions are clearly explained during onboarding.

---

## 🎯 Features in Detail

### On-Device LLM

- Runs Llama models locally using llama.cpp
- No internet required for AI responses
- Optimized for ARM processors
- Supports quantization for smaller models

### Telegram Integration

- Real-time chat interface
- Streaming responses
- Proactive notifications
- Message history sync

### Smart Search

- Integrated DuckDuckGo search
- Intelligent query handling
- Citation extraction
- Context-aware results

### Memory System

- Two-tier architecture (hot/cold memory)
- Conversation history
- User preferences
- Pattern detection

### Proactive Intelligence

- Pattern detection from notifications
- Contextual suggestions
- Rate-limited messaging (max 3/day)
- Sleep-aware scheduling

---

## 🔒 Privacy & Security

### Data Storage

- **100% Local**: All data stored on your device
- **No Cloud**: No data sent to external servers (except web search queries)
- **No Tracking**: No analytics, no telemetry
- **Open Source**: Full source code available for audit

### What We Don't Collect

- Names or personal identifiers
- Gender or demographic data
- Location data
- Device identifiers
- Usage analytics

### What We Store Locally

- Your interests (for personalization)
- Conversation history
- Notification patterns (for proactive insights)
- User preferences

All data is encrypted at rest (Android 9+) and never leaves your device.

---

## 🛠️ Development

### Building

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test
```

### Requirements

- Android Studio Hedgehog+
- NDK 27.0.12077973
- Gradle 8.9
- 8GB+ RAM

### Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📊 Performance

### Optimizations

- **Native Code**: ARM NEON SIMD instructions
- **Quantization**: i8mm support for 20% speedup
- **Flash Attention**: Faster inference
- **Smart Caching**: Efficient context management
- **Thermal Management**: Adaptive performance

### System Requirements

- **Minimum**: Android 8.0 (API 26)
- **Recommended**: Android 10+ (API 29+)
- **RAM**: 4GB+ recommended
- **Storage**: 3GB+ free space (for model)

---

## 🐛 Troubleshooting

### Model Not Loading

- Check model file permissions
- Verify GGUF format compatibility
- Check available device memory
- Ensure storage permission is granted

### Telegram Not Responding

- Verify bot token
- Check internet connection
- Ensure bot is started (`/start` command)
- Review logs: `adb logcat | grep TelegramBotManager`

### Slow Responses

- Check thermal state
- Reduce batch size in settings
- Use smaller model
- Ensure battery optimization is disabled

### Build Issues

**NDK not found**
```bash
# Install NDK via Android Studio SDK Manager
# Or set in local.properties:
ndk.dir=/path/to/ndk/27.0.12077973
```

**Out of memory**
```bash
# Increase Gradle memory in gradle.properties:
org.gradle.jvmargs=-Xmx8g
```

---

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- [llama.cpp](https://github.com/ggerganov/llama.cpp) - LLM inference engine
- [Telegram Bot API](https://core.telegram.org/bots/api) - Bot integration
- Android Jetpack - Modern Android development
- Jetpack Compose - Modern UI toolkit

---

## 📞 Support

- **GitHub Issues**: [Report a bug](https://github.com/2796gaurav/confidant.ai/issues)
- **Website**: [http://2796gaurav.github.io/confidantai](http://2796gaurav.github.io/confidantai)
- **Repository**: [https://github.com/2796gaurav/confidant.ai](https://github.com/2796gaurav/confidant.ai)

---

## 🌐 Links

- 🔗 **Repository**: [https://github.com/2796gaurav/confidant.ai](https://github.com/2796gaurav/confidant.ai)
- 🌐 **Website**: [http://2796gaurav.github.io/confidantai](http://2796gaurav.github.io/confidantai)
- 📱 **Download APK**: [Releases](https://github.com/2796gaurav/confidant.ai/releases)

---

<div align="center">

**Made with ❤️ for Privacy-Conscious Users**

*Confidant AI - Your Trusted On-Device AI Companion*

</div>
